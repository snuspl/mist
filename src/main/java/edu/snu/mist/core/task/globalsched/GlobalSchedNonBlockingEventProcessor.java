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
package edu.snu.mist.core.task.globalsched;

import edu.snu.mist.core.task.OperatorChain;
import edu.snu.mist.core.task.OperatorChainManager;
import edu.snu.mist.core.task.eventProcessors.EventProcessor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is an event processor that can change the operator chain manager.
 * Every time slice of the group, it selects another operator chain manager
 * to execute the events of queries within the group.
 * It also selects another operator chain manager when there are no active operator chain,
 * which means it does not block when the group has no active operator chain.
 */
public final class GlobalSchedNonBlockingEventProcessor extends Thread implements EventProcessor {

  private static final Logger LOG = Logger.getLogger(GlobalSchedNonBlockingEventProcessor.class.getName());

  /**
   * Variable for checking close or not.
   */
  private volatile boolean closed;

  /**
   * Selector of the executable group.
   */
  private final NextGroupSelector nextGroupSelector;

  public GlobalSchedNonBlockingEventProcessor(final NextGroupSelector nextGroupSelector) {
    super();
    this.nextGroupSelector = nextGroupSelector;
  }

  /**
   * It executes the events of the selected group during the scheduling period, and re-select another group.
   */
  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && !closed) {
        // Pick an active group
        final GlobalSchedGroupInfo groupInfo = nextGroupSelector.getNextExecutableGroup();

        // Incoming rates of events
        double incomingEventRate;
        try {
          incomingEventRate = groupInfo.numberOfRemainingEvents() /
              (double)(System.currentTimeMillis() - groupInfo.getLatestInactiveTime());
        } catch (final ArithmeticException e) {
          incomingEventRate = 0;
        }

        final OperatorChainManager operatorChainManager = groupInfo.getOperatorChainManager();

        final long startProcessingTime = System.currentTimeMillis();
        long numProcessedEvents = 0;
        while (groupInfo.isActive()) {
          //Pick an active operator event queue
          final OperatorChain operatorChain = operatorChainManager.pickOperatorChain();
          while (operatorChain.processNextEvent()) {
            // Process next event
            numProcessedEvents += 1;
          }
        }
        final long endProcessingTime = System.currentTimeMillis();

        // Processing time / events
        double processingTimeRate;
        try {
          processingTimeRate = (double)(endProcessingTime - startProcessingTime) /
              numProcessedEvents;
        } catch (final ArithmeticException e) {
          processingTimeRate = 0;
        }

        // Update load
        final double load = processingTimeRate * incomingEventRate;
        groupInfo.updateLoad(load);
        groupInfo.setLatestInactiveTime(endProcessingTime);

        if (LOG.isLoggable(Level.FINE)) {
          LOG.log(Level.FINE, "{0} Process Group {1}, Load: {2}",
              new Object[]{Thread.currentThread().getName(), groupInfo, load});
        }

        // Reschedule
        nextGroupSelector.reschedule(groupInfo, true);
      }
    } catch (final InterruptedException e) {
      // Interrupt occurs while sleeping, so just finishes the process...
      e.printStackTrace();
    } catch (final Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e + ", OperatorChainManager should not return null");
    }
  }

  public void close() throws Exception {
    closed = true;
    nextGroupSelector.close();
  }

  @Override
  public NextGroupSelector getNextGroupSelector() {
    return nextGroupSelector;
  }
}