/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.mist.core.task.eventProcessors;

import edu.snu.mist.core.task.eventProcessors.parameters.IsolationTriggerPeriod;
import edu.snu.mist.core.task.globalsched.GlobalSchedGroupInfo;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * This class isolates an overloaded group,
 * if the processing of events in a group is not finished until a certain period.
 *
 * 1) When processing a group is not finished because of lots of events,
 * it preempts the group and isolates it in a new thread.
 *
 * 2) When processing a group is not finished because of adversarial operations,
 * such as sleep(10000) and while (true) {}, it isolates the group in the current thread
 * and moves all groups except for the isolated group to a new thread.
 */
public final class DefaultGroupIsolatorImpl implements GroupIsolator {

  private final GroupAllocationTable groupAllocationTable;
  private final EventProcessorFactory eventProcessorFactory;
  private final long isolationTriggerPeriod;

  @Inject
  private DefaultGroupIsolatorImpl(final GroupAllocationTable groupAllocationTable,
                                   final EventProcessorFactory eventProcessorFactory,
                                   @Parameter(IsolationTriggerPeriod.class) final long isolationTriggerPeriod) {
    this.groupAllocationTable = groupAllocationTable;
    this.eventProcessorFactory = eventProcessorFactory;
    this.isolationTriggerPeriod = isolationTriggerPeriod;
  }

  /**
   * Check whether the group is preemptible.
   * We decide the group is preemtible if the number of processed events > 0.
   * @param runtimeProcessingInfo runtime processing info
   * @return True if it is preemptible
   */
  private boolean isPreemptible(final RuntimeProcessingInfo runtimeProcessingInfo) {
    final long numProcessedEvents = runtimeProcessingInfo.getNumProcessedEvents();
    return numProcessedEvents > 0;
  }

  @Override
  public void triggerIsolation() {
    final List<EventProcessor> eventProcessors = groupAllocationTable.getKeys();
    for (final EventProcessor eventProcessor : eventProcessors) {
      if (!eventProcessor.isIsolatedProcessor()) {
        final RuntimeProcessingInfo runtimeProcessingInfo = eventProcessor.getCurrentRuntimeInfo();
        final GlobalSchedGroupInfo groupInfo = runtimeProcessingInfo.getCurrGroup();
        final long startTime = runtimeProcessingInfo.getStartTime();
        final long elapsedTime = System.currentTimeMillis() - startTime;

        if (groupInfo != null && elapsedTime >= isolationTriggerPeriod && groupInfo.setIsolated()) {
          // create a new thread
          final EventProcessor newEP = eventProcessorFactory.newEventProcessor();
          newEP.start();
          groupAllocationTable.addEventProcessor(newEP);
          final Collection<GlobalSchedGroupInfo> destGroups = groupAllocationTable.getValue(newEP);

          // Groups of the current event processor
          final Collection<GlobalSchedGroupInfo> srcGroups = groupAllocationTable.getValue(eventProcessor);

          if (isPreemptible(runtimeProcessingInfo)) {
            // The new thread is an isolated thread
            newEP.setToIsolatedProcessor();

            // Move the preemptible group to the new thread
            // Add the group to the new thread
            destGroups.add(groupInfo);
            // Remove the group from the previous thread
            srcGroups.remove(groupInfo);
            eventProcessor.removeActiveGroup(groupInfo);

          } else {
            // The new thread is a normal, but the current thread should be an isolated thread
            eventProcessor.setToIsolatedProcessor();
            newEP.setToNormalProcessor();

            // Move remaining groups to the new thread and isolate the current group in the current thread
            final Iterator<GlobalSchedGroupInfo> iterator = srcGroups.iterator();
            while (iterator.hasNext()) {
              final GlobalSchedGroupInfo groupToMove = iterator.next();
              if (!groupToMove.equals(groupInfo)) {
                destGroups.add(groupToMove);
                iterator.remove();
                eventProcessor.removeActiveGroup(groupInfo);
              }
            }
          }

          // End of the isolation
          groupInfo.setReadyFromIsolated();
        }
      }
    }
  }
}