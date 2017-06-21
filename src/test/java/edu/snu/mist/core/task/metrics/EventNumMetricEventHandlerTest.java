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
package edu.snu.mist.core.task.metrics;

import edu.snu.mist.common.graph.AdjacentListDAG;
import edu.snu.mist.common.graph.MISTEdge;
import edu.snu.mist.common.parameters.GroupId;
import edu.snu.mist.core.task.*;
import edu.snu.mist.core.task.merging.MergingExecutionDags;
import edu.snu.mist.core.task.utils.IdAndConfGenerator;
import edu.snu.mist.formats.avro.Direction;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static edu.snu.mist.core.task.utils.SimpleOperatorChainUtils.*;

/**
 * Test whether EventNumAndWeightMetricEventHandler tracks the metrics properly or not.
 */
public final class EventNumMetricEventHandlerTest {

  private MistPubSubEventHandler metricPubSubEventHandler;
  private IdAndConfGenerator idAndConfGenerator;
  private GroupInfoMap groupInfoMap;
  private EventNumMetricEventHandler handler;

  @Before
  public void setUp() throws InjectionException {
    final Injector injector = Tang.Factory.getTang().newInjector();
    groupInfoMap = injector.getInstance(GroupInfoMap.class);
    final GroupMetrics globalMetricHolder = injector.getInstance(GroupMetrics.class);
    metricPubSubEventHandler = injector.getInstance(MistPubSubEventHandler.class);
    handler = injector.getInstance(EventNumMetricEventHandler.class);
    idAndConfGenerator = new IdAndConfGenerator();
  }

  /**
   * Test that a metric track event handler can track the total event number metric properly.
   */
  @Test(timeout = 1000L)
  public void testEventNumMetricTracking() throws Exception {

    final GroupInfo groupInfoA = generateGroupInfo("GroupA");
    final GroupInfo groupInfoB = generateGroupInfo("GroupB");
    final ExecutionDags executionDagsA = groupInfoA.getExecutionDags();
    final ExecutionDags executionDagsB = groupInfoB.getExecutionDags();

    // two dags in group A:
    // srcA1 -> opA1 -> sinkA1
    // srcA2 -> opA2 -> sinkA2
    final PhysicalSource srcA1 = generateTestSource(idAndConfGenerator);
    final PhysicalSource srcA2 = generateTestSource(idAndConfGenerator);
    final OperatorChain opA1 = generateFilterOperatorChain(idAndConfGenerator);
    final OperatorChain opA2 = generateFilterOperatorChain(idAndConfGenerator);
    final PhysicalSink sinkA1 = generateTestSink(idAndConfGenerator);
    final PhysicalSink sinkA2 = generateTestSink(idAndConfGenerator);

    final ExecutionDag executionDagA1 = new ExecutionDag(new AdjacentListDAG<>());
    executionDagA1.getDag().addVertex(srcA1);
    executionDagA1.getDag().addVertex(opA1);
    executionDagA1.getDag().addVertex(sinkA1);
    executionDagA1.getDag().addEdge(srcA1, opA1, new MISTEdge(Direction.LEFT));
    executionDagA1.getDag().addEdge(opA1, sinkA1, new MISTEdge(Direction.LEFT));

    final ExecutionDag executionDagA2 = new ExecutionDag(new AdjacentListDAG<>());
    executionDagA2.getDag().addVertex(srcA2);
    executionDagA2.getDag().addVertex(opA2);
    executionDagA2.getDag().addVertex(sinkA2);
    executionDagA2.getDag().addEdge(srcA2, opA2, new MISTEdge(Direction.LEFT));
    executionDagA2.getDag().addEdge(opA2, sinkA2, new MISTEdge(Direction.LEFT));

    executionDagsA.add(executionDagA1);
    executionDagsA.add(executionDagA2);

    // one dag in group B:
    // srcB1 -> opB1 -> union -> sinkB1
    // srcB2 -> opB2 ->       -> sinkB2
    final PhysicalSource srcB1 = generateTestSource(idAndConfGenerator);
    final PhysicalSource srcB2 = generateTestSource(idAndConfGenerator);
    final OperatorChain opB1 = generateFilterOperatorChain(idAndConfGenerator);
    final OperatorChain opB2 = generateFilterOperatorChain(idAndConfGenerator);
    final OperatorChain union = generateUnionOperatorChain(idAndConfGenerator);
    final PhysicalSink sinkB1 = generateTestSink(idAndConfGenerator);
    final PhysicalSink sinkB2 = generateTestSink(idAndConfGenerator);

    final ExecutionDag executionDagB = new ExecutionDag(new AdjacentListDAG<>());
    executionDagB.getDag().addVertex(srcB1);
    executionDagB.getDag().addVertex(srcB2);
    executionDagB.getDag().addVertex(opB1);
    executionDagB.getDag().addVertex(opB2);
    executionDagB.getDag().addVertex(union);
    executionDagB.getDag().addVertex(sinkB1);
    executionDagB.getDag().addVertex(sinkB2);
    executionDagB.getDag().addEdge(srcB1, opB1, new MISTEdge(Direction.LEFT));
    executionDagB.getDag().addEdge(srcB2, opB2, new MISTEdge(Direction.LEFT));
    executionDagB.getDag().addEdge(opB1, union, new MISTEdge(Direction.LEFT));
    executionDagB.getDag().addEdge(opB2, union, new MISTEdge(Direction.RIGHT));
    executionDagB.getDag().addEdge(union, sinkB1, new MISTEdge(Direction.LEFT));
    executionDagB.getDag().addEdge(union, sinkB2, new MISTEdge(Direction.LEFT));

    executionDagsB.add(executionDagB);
    executionDagsB.add(executionDagB);

    // the event number should be zero in each group
    Assert.assertEquals(0, getNumEvents(groupInfoA), 0.00001);
    Assert.assertEquals(0, getNumEvents(groupInfoB), 0.00001);

    // add a few events to the operator chains in group A
    opA1.addNextEvent(generateTestEvent(), Direction.LEFT);
    opA2.addNextEvent(generateTestEvent(), Direction.LEFT);
    opA2.addNextEvent(generateTestEvent(), Direction.LEFT);

    // wait the tracker for a while
    metricPubSubEventHandler.getPubSubEventHandler().onNext(new MetricTrackEvent());
    Assert.assertEquals(3, getNumEvents(groupInfoA), 0.00001);
    Assert.assertEquals(0, getNumEvents(groupInfoB), 0.00001);

    // add a few events to the operator chains in group B
    opB1.addNextEvent(generateTestEvent(), Direction.LEFT);
    opB2.addNextEvent(generateTestEvent(), Direction.LEFT);
    union.addNextEvent(generateTestEvent(), Direction.LEFT);
    union.addNextEvent(generateTestEvent(), Direction.RIGHT);

    // wait the tracker for a while
    metricPubSubEventHandler.getPubSubEventHandler().onNext(new MetricTrackEvent());
    Assert.assertEquals(3, getNumEvents(groupInfoA), 0.00001);
    Assert.assertEquals(4, getNumEvents(groupInfoB), 0.00001);
  }

  /**
   * Generate a group info instance that has the group id and put it into a group info map.
   * @param groupId group id
   * @return the generated group info
   * @throws InjectionException
   */
  private GroupInfo generateGroupInfo(final String groupId) throws InjectionException {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(GroupId.class, groupId);
    jcb.bindImplementation(ExecutionDags.class, MergingExecutionDags.class);
    final Injector injector = Tang.Factory.getTang().newInjector(jcb.build());
    final GroupInfo groupInfo = injector.getInstance(GroupInfo.class);
    groupInfoMap.put(groupId, groupInfo);
    return groupInfo;
  }

  /**
   * Get the value of num events metric from gorup info.
   * @param groupInfo the group info
   * @return the value of num events
   */
  private double getNumEvents(final GroupInfo groupInfo) {
    return groupInfo.getMetricHolder().getNumEventsMetric().getValue();
  }
}
