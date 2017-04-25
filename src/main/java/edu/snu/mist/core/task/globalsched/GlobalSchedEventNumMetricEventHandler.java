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

import edu.snu.mist.common.graph.DAG;
import edu.snu.mist.common.graph.MISTEdge;
import edu.snu.mist.core.task.*;
import org.apache.reef.wake.EventHandler;

import javax.inject.Inject;
import java.util.Collection;

/**
 * A class handles the metric event about EventNumMetric.
 */
public final class GlobalSchedEventNumMetricEventHandler implements EventHandler<MetricEvent> {

  /**
   * The map of group ids and group info to update.
   */
  private final GlobalSchedGroupInfoMap groupInfoMap;

  /**
   * The global metrics.
   */
  private final GlobalSchedGlobalMetrics globalMetrics;

  @Inject
  private GlobalSchedEventNumMetricEventHandler(final MetricPubSubEventHandler metricPubSubEventHandler,
                                                final GlobalSchedGroupInfoMap groupInfoMap,
                                                final GlobalSchedGlobalMetrics globalMetrics) {
    this.groupInfoMap = groupInfoMap;
    this.globalMetrics = globalMetrics;
    metricPubSubEventHandler.getPubSubEventHandler().subscribe(MetricEvent.class, this);
    // Initialize
    this.onNext(new MetricEvent());
  }

  @Override
  public void onNext(final MetricEvent metricEvent) {
    long totalNumEvent = 0;
    int totalWeight = 0;
    for (final GlobalSchedGroupInfo groupInfo : groupInfoMap.values()) {
      // Track the number of event per each group
      long groupNumEvent = 0;
      for (final DAG<ExecutionVertex, MISTEdge> dag : groupInfo.getExecutionDags().getUniqueValues()) {
        final Collection<ExecutionVertex> vertices = dag.getVertices();
        for (final ExecutionVertex ev : vertices) {
          if (ev.getType() == ExecutionVertex.Type.OPERATOR_CHIAN) {
            groupNumEvent += ((OperatorChain) ev).numberOfEvents();
          }
        }
      }
      final EventNumAndWeightMetric metric = groupInfo.getEventNumAndWeightMetric();
      metric.setNumEvents(groupNumEvent);
      totalNumEvent += groupNumEvent;

      // TODO: [MIST-617] Add group weight adding process into GlobalSchedMetricTracker
      final int weightToSet = 1;
      metric.setWeight(weightToSet);
      totalWeight += weightToSet;
    }
    globalMetrics.getNumEventAndWeightMetric().setNumEvents(totalNumEvent);
    globalMetrics.getNumEventAndWeightMetric().setWeight(totalWeight);
  }
}