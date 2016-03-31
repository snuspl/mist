/*
 * Copyright (C) 2016 Seoul National University
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
package edu.snu.mist.task;

import edu.snu.mist.common.AdjacentListDAG;
import edu.snu.mist.common.DAG;
import edu.snu.mist.common.GraphUtils;
import edu.snu.mist.task.operators.Operator;
import edu.snu.mist.task.sinks.Sink;
import edu.snu.mist.task.sources.Source;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class QueryPartitionerTest {
  private static final Logger LOG = Logger.getLogger(QueryPartitionerTest.class.getName());

  /**
   * Test complex chaining (branch and merge exist).
   * PhysicalPlan:
   * src1 -> op11 -> op12 -> op13 -> op14 -> op15 -> sink1
   * src2 -> op21 -> op22 ->      -> op23 -> sink2.
   *
   * should be converted to the expected chained PhysicalPlan:
   * src1 -> [op11 -> op12] -> [op13] -> [op14 -> op15] -> sink1
   * src2 -> [op21 -> op22] ->        -> [op23] -> sink2.
   */
  @Test
  public void testComplexQueryPartitioning() throws InjectionException {
    // Build a physical plan
    final DAG<Operator> operatorDAG = new AdjacentListDAG<>();
    final Map<Source, Set<Operator>> sourceMap = new HashMap<>();
    final Map<Operator, Set<Sink>> sinkMap = new HashMap<>();

    final Source src1 = mock(Source.class);
    final Source src2 = mock(Source.class);

    final Operator op11 = mock(Operator.class);
    when(op11.toString()).thenReturn("op11");
    final Operator op12 = mock(Operator.class);
    when(op12.toString()).thenReturn("op12");
    final Operator op13 = mock(Operator.class);
    when(op13.toString()).thenReturn("op13");
    final Operator op14 = mock(Operator.class);
    when(op14.toString()).thenReturn("op14");
    final Operator op15 = mock(Operator.class);
    when(op15.toString()).thenReturn("op15");

    final Operator op21 = mock(Operator.class);
    final Operator op22 = mock(Operator.class);
    final Operator op23 = mock(Operator.class);

    final Sink sink1 = mock(Sink.class);
    final Sink sink2 = mock(Sink.class);

    operatorDAG.addVertex(op11); operatorDAG.addVertex(op12);
    operatorDAG.addVertex(op13); operatorDAG.addVertex(op14);
    operatorDAG.addVertex(op15);

    operatorDAG.addVertex(op21); operatorDAG.addVertex(op22);
    operatorDAG.addVertex(op23);

    operatorDAG.addEdge(op11, op12); operatorDAG.addEdge(op12, op13);
    operatorDAG.addEdge(op13, op14); operatorDAG.addEdge(op13, op23);
    operatorDAG.addEdge(op14, op15);

    operatorDAG.addEdge(op21, op22); operatorDAG.addEdge(op22, op13);

    final Set<Operator> src1Ops = new HashSet<>();
    final Set<Operator> src2Ops = new HashSet<>();
    src1Ops.add(op11); src2Ops.add(op21);
    sourceMap.put(src1, src1Ops);
    sourceMap.put(src2, src2Ops);

    final Set<Sink> op15Sinks = new HashSet<>();
    final Set<Sink> op23Sinks = new HashSet<>();
    op15Sinks.add(sink1); op23Sinks.add(sink2);
    sinkMap.put(op15, op15Sinks);
    sinkMap.put(op23, op23Sinks);

    final PhysicalPlan<Operator> physicalPlan =
        new DefaultPhysicalPlanImpl<>(sourceMap, operatorDAG, sinkMap);
    final Injector injector = Tang.Factory.getTang().newInjector();
    final QueryPartitioner queryPartitioner =
        injector.getInstance(QueryPartitioner.class);

    // convert
    final PhysicalPlan<PartitionedQuery> chainedPhysicalPlan =
        queryPartitioner.chainOperators(physicalPlan);

    // check

    final PartitionedQuery op11op12 = new DefaultPartitionedQuery();
    op11op12.insertToTail(op11); op11op12.insertToTail(op12);

    final PartitionedQuery op13chain = new DefaultPartitionedQuery();
    op13chain.insertToTail(op13);

    final PartitionedQuery op14op15 = new DefaultPartitionedQuery();
    op14op15.insertToTail(op14); op14op15.insertToTail(op15);

    final PartitionedQuery op21op22 = new DefaultPartitionedQuery();
    op21op22.insertToTail(op21); op21op22.insertToTail(op22);

    final PartitionedQuery op23chain = new DefaultPartitionedQuery();
    op23chain.insertToTail(op23);

    final DAG<PartitionedQuery> partitionedQueryDAG = chainedPhysicalPlan.getOperators();
    final Iterator<PartitionedQuery> iterator = GraphUtils.topologicalSort(partitionedQueryDAG);
    int num = 0;

    // check
    while (iterator.hasNext()) {
      final PartitionedQuery partitionedQuery = iterator.next();
      if (partitionedQuery.equals(op11op12)) {
        final Set<PartitionedQuery> op11op12neighbor = new HashSet<>();
        op11op12neighbor.add(op13chain);
        Assert.assertEquals("[op11->op12]'s neighbor should be  [op13]",
            op11op12neighbor, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(op13chain)) {
        final Set<PartitionedQuery> op13neighbor = new HashSet<>();
        op13neighbor.add(op14op15);
        op13neighbor.add(op23chain);
        Assert.assertEquals("[op13]'s neighbor should be  [op14->op15], [op23]",
            op13neighbor, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(op14op15)) {
        Assert.assertEquals("[op13->op15]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else if (partitionedQuery.equals(op21op22)) {
        final Set<PartitionedQuery> op2122neighbor = new HashSet<>();
        op2122neighbor.add(op13chain);
        Assert.assertEquals("[op21->op22]'s neighbor should be  [op13]",
            op2122neighbor, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(op23chain)) {
        Assert.assertEquals("[op23]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else {
        throw new RuntimeException("PartitionedQuery mismatched: " + partitionedQuery);
      }
      num += 1;
    }
    Assert.assertEquals("The number of PartitionedQuery should be 5", 5, num);

    // src map
    final Map<Source, Set<PartitionedQuery>> chainedSrcMap = chainedPhysicalPlan.getSourceMap();
    Assert.assertEquals("The number of Source should be 2", 2, chainedSrcMap.size());

    final Set<PartitionedQuery> src1OpChain = new HashSet<>();
    src1OpChain.add(op11op12);
    Assert.assertEquals("The mapped PartitionedQuery of src1 should be [op11->op12]",
        src1OpChain, chainedSrcMap.get(src1));

    final Set<PartitionedQuery> src2OpChain = new HashSet<>();
    src2OpChain.add(op21op22);
    Assert.assertEquals("The mapped PartitionedQuery of src2 should be [op21->op22]",
        src2OpChain, chainedSrcMap.get(src2));

    // sink map
    final Map<PartitionedQuery, Set<Sink>> chainedSinkMap = chainedPhysicalPlan.getSinkMap();
    Assert.assertEquals("The number of PartitionedQueries connected to Sink should be 2", 2, chainedSinkMap.size());

    final Set<Sink> sink1Set = new HashSet<>();
    sink1Set.add(sink1);
    Assert.assertEquals("The mapped Sink of [op14->op15] should be sink1",
        sink1Set, chainedSinkMap.get(op14op15));
    final Set<Sink> sink2Set = new HashSet<>();

    sink2Set.add(sink2);
    Assert.assertEquals("The mapped Sink of [op23] should be sink2",
        sink2Set, chainedSinkMap.get(op23chain));
  }

  /**
   * Test sequential chaining.
   * PhysicalPlan:
   * src1 -> op11 -> op12 -> op13 -> sink1
   *
   * should be converted to the expected chained PhysicalPlan:
   * src1 -> [op11 -> op12 -> op13] -> sink1
   */
  @Test
  public void testSequentialChaining() throws InjectionException {
    // Build a physical plan
    final DAG<Operator> operatorDAG = new AdjacentListDAG<>();
    final Map<Source, Set<Operator>> sourceMap = new HashMap<>();
    final Map<Operator, Set<Sink>> sinkMap = new HashMap<>();

    final Source src1 = mock(Source.class);
    final Operator op11 = mock(Operator.class);
    when(op11.toString()).thenReturn("op11");
    final Operator op12 = mock(Operator.class);
    when(op12.toString()).thenReturn("op12");
    final Operator op13 = mock(Operator.class);
    when(op13.toString()).thenReturn("op13");
    final Sink sink1 = mock(Sink.class);

    operatorDAG.addVertex(op11);
    operatorDAG.addVertex(op12);
    operatorDAG.addVertex(op13);

    operatorDAG.addEdge(op11, op12);
    operatorDAG.addEdge(op12, op13);

    final Set<Operator> src1Ops = new HashSet<>();
    src1Ops.add(op11);
    sourceMap.put(src1, src1Ops);

    final Set<Sink> op13Sinks = new HashSet<>();
    op13Sinks.add(sink1);
    sinkMap.put(op13, op13Sinks);

    final PhysicalPlan<Operator> physicalPlan =
        new DefaultPhysicalPlanImpl<>(sourceMap, operatorDAG, sinkMap);
    final Injector injector = Tang.Factory.getTang().newInjector();
    final QueryPartitioner queryPartitioner =
        injector.getInstance(QueryPartitioner.class);

    // Create PartitionedQuery's plan
    final PhysicalPlan<PartitionedQuery> chainedPhysicalPlan =
        queryPartitioner.chainOperators(physicalPlan);

    // check
    final PartitionedQuery op11op12op13 = new DefaultPartitionedQuery();
    op11op12op13.insertToTail(op11);
    op11op12op13.insertToTail(op12);
    op11op12op13.insertToTail(op13);

    final DAG<PartitionedQuery> partitionedQueryDAG = chainedPhysicalPlan.getOperators();
    final Iterator<PartitionedQuery> iterator = GraphUtils.topologicalSort(partitionedQueryDAG);
    int num = 0;

    while (iterator.hasNext()) {
      final PartitionedQuery partitionedQuery = iterator.next();
      if (partitionedQuery.equals(op11op12op13)) {
        Assert.assertEquals("[op11->op12->op13]'s neighbor should be  empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else {
        throw new RuntimeException("PartitionedQuery mismatched: " + partitionedQuery);
      }
      num += 1;
    }
    Assert.assertEquals("The number of PartitionedQuery should be 1", 1, num);

    // src map
    final Map<Source, Set<PartitionedQuery>> chainedSrcMap = chainedPhysicalPlan.getSourceMap();
    Assert.assertEquals("The number of Source should be 1", 1, chainedSrcMap.size());
    final Set<PartitionedQuery> src1OpChain = new HashSet<>();
    src1OpChain.add(op11op12op13);
    Assert.assertEquals("The mapped PartitionedQuery of src1 should be [op11->op12->op13]",
        src1OpChain, chainedSrcMap.get(src1));

    // sink map
    final Map<PartitionedQuery, Set<Sink>> chainedSinkMap = chainedPhysicalPlan.getSinkMap();
    Assert.assertEquals("The number of PartitionedQueries connected to Sink should be 1", 1, chainedSinkMap.size());
    final Set<Sink> sink1Set = new HashSet<>();
    sink1Set.add(sink1);
    Assert.assertEquals("The mapped Sink of [op11->op12->op13] should be sink1",
        sink1Set, chainedSinkMap.get(op11op12op13));
  }

  /**
   * Test branch chaining.
   * PhysicalPlan:
   *                      -> op14 -> sink2
   * src1 -> op11 -> op12 -> op13 -> sink1
   *                      -> op15 -> sink3
   * should be converted to the expected chained PhysicalPlan:
   *                        -> [op14] -> sink2
   * src1 -> [op11 -> op12] -> [op13] -> sink1
   *                        -> [op15] -> sink3
   */
  @Test
  public void testBranchTest() throws InjectionException {
    // Build a physical plan
    final DAG<Operator> operatorDAG = new AdjacentListDAG<>();
    final Map<Source, Set<Operator>> sourceMap = new HashMap<>();
    final Map<Operator, Set<Sink>> sinkMap = new HashMap<>();

    final Source src1 = mock(Source.class);
    final Operator op11 = mock(Operator.class);
    when(op11.toString()).thenReturn("op11");
    final Operator op12 = mock(Operator.class);
    when(op12.toString()).thenReturn("op12");
    final Operator op13 = mock(Operator.class);
    when(op13.toString()).thenReturn("op13");
    final Operator op14 = mock(Operator.class);
    when(op14.toString()).thenReturn("op14");
    final Operator op15 = mock(Operator.class);
    when(op15.toString()).thenReturn("op15");
    final Sink sink1 = mock(Sink.class);
    final Sink sink2 = mock(Sink.class);
    final Sink sink3 = mock(Sink.class);

    operatorDAG.addVertex(op11); operatorDAG.addVertex(op12);
    operatorDAG.addVertex(op13); operatorDAG.addVertex(op14);
    operatorDAG.addVertex(op15);

    operatorDAG.addEdge(op11, op12); operatorDAG.addEdge(op12, op13);
    operatorDAG.addEdge(op12, op14); operatorDAG.addEdge(op12, op15);

    final Set<Operator> src1Ops = new HashSet<>();
    src1Ops.add(op11);
    sourceMap.put(src1, src1Ops);

    final Set<Sink> op13Sinks = new HashSet<>();
    final Set<Sink> op14Sinks = new HashSet<>();
    final Set<Sink> op15Sinks = new HashSet<>();
    op13Sinks.add(sink1); op14Sinks.add(sink2); op15Sinks.add(sink3);
    sinkMap.put(op13, op13Sinks); sinkMap.put(op14, op14Sinks); sinkMap.put(op15, op15Sinks);

    final PhysicalPlan<Operator> physicalPlan =
        new DefaultPhysicalPlanImpl<>(sourceMap, operatorDAG, sinkMap);
    final Injector injector = Tang.Factory.getTang().newInjector();
    final QueryPartitioner queryPartitioner =
        injector.getInstance(QueryPartitioner.class);

    // convert
    final PhysicalPlan<PartitionedQuery> chainedPhysicalPlan =
        queryPartitioner.chainOperators(physicalPlan);

    // check
    final PartitionedQuery op11op12 = new DefaultPartitionedQuery();
    op11op12.insertToTail(op11); op11op12.insertToTail(op12);

    final PartitionedQuery op13chain = new DefaultPartitionedQuery();
    op13chain.insertToTail(op13);

    final PartitionedQuery op14chain = new DefaultPartitionedQuery();
    op14chain.insertToTail(op14);

    final PartitionedQuery op15chain = new DefaultPartitionedQuery();
    op15chain.insertToTail(op15);

    final DAG<PartitionedQuery> partitionedQueryDAG = chainedPhysicalPlan.getOperators();
    final Iterator<PartitionedQuery> iterator = GraphUtils.topologicalSort(partitionedQueryDAG);
    int num = 0;

    while (iterator.hasNext()) {
      final PartitionedQuery partitionedQuery = iterator.next();
      if (partitionedQuery.equals(op11op12)) {
        final Set<PartitionedQuery> op11op12neighbor = new HashSet<>();
        op11op12neighbor.add(op13chain);
        op11op12neighbor.add(op14chain);
        op11op12neighbor.add(op15chain);
        Assert.assertEquals("[op11->op12]'s neighbor should be  [op13], [op14] and [op15]",
            op11op12neighbor, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(op13chain)) {
        Assert.assertEquals("[op13]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else if (partitionedQuery.equals(op14chain)) {
        Assert.assertEquals("[op14]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else if (partitionedQuery.equals(op15chain)) {
        Assert.assertEquals("[op15]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else {
        throw new RuntimeException("PartitionedQuery mismatched: " + partitionedQuery);
      }
      num += 1;
    }
    Assert.assertEquals("The number of PartitionedQuery should be 4", 4, num);

    // src map
    final Map<Source, Set<PartitionedQuery>> chainedSrcMap = chainedPhysicalPlan.getSourceMap();
    Assert.assertEquals("The number of Source should be 1", 1, chainedSrcMap.size());

    final Set<PartitionedQuery> src1OpChain = new HashSet<>();
    src1OpChain.add(op11op12);
    Assert.assertEquals("The mapped PartitionedQuery of src1 should be [op11->op12]",
        src1OpChain, chainedSrcMap.get(src1));

    // sink map
    final Map<PartitionedQuery, Set<Sink>> chainedSinkMap = chainedPhysicalPlan.getSinkMap();
    Assert.assertEquals("The number of PartitionedQueries connected to Sink should be 3", 3, chainedSinkMap.size());
    final Set<Sink> sink1Set = new HashSet<>();
    sink1Set.add(sink1);
    Assert.assertEquals("The mapped Sink of [op13] should be sink1",
        sink1Set, chainedSinkMap.get(op13chain));

    final Set<Sink> sink2Set = new HashSet<>();
    sink2Set.add(sink2);
    Assert.assertEquals("The mapped Sink of [op14] should be sink2",
        sink2Set, chainedSinkMap.get(op14chain));

    final Set<Sink> sink3Set = new HashSet<>();
    sink3Set.add(sink3);
    Assert.assertEquals("The mapped Sink of [op15] should be sink3",
        sink3Set, chainedSinkMap.get(op15chain));
  }


  /**
   * Test merge chaining.
   * PhysicalPlan:
   * src1 -> op11 -> op12 ->
   * src2 ---------> op21 -> op13 -> sink1
   * src3 ---------> op31 ->
   *
   * should be converted to the expected chained PhysicalPlan:
   * src1 -> [op11 -> op12] ->
   * src2 ---------> [op21] -> [op13] -> sink1
   * src3 ---------> [op31] ->
   */
  @Test
  public void testMergingQueryPartitioning() throws InjectionException {
    // Build a physical plan
    final DAG<Operator> operatorDAG = new AdjacentListDAG<>();
    final Map<Source, Set<Operator>> sourceMap = new HashMap<>();
    final Map<Operator, Set<Sink>> sinkMap = new HashMap<>();

    final Source src1 = mock(Source.class);
    final Source src2 = mock(Source.class);
    final Source src3 = mock(Source.class);

    final Operator op11 = mock(Operator.class);
    when(op11.toString()).thenReturn("op11");
    final Operator op12 = mock(Operator.class);
    when(op12.toString()).thenReturn("op12");
    final Operator op13 = mock(Operator.class);
    when(op13.toString()).thenReturn("op13");
    final Operator op21 = mock(Operator.class);
    when(op21.toString()).thenReturn("op21");
    final Operator op31 = mock(Operator.class);
    when(op31.toString()).thenReturn("op31");

    final Sink sink1 = mock(Sink.class);

    operatorDAG.addVertex(op11); operatorDAG.addVertex(op12);
    operatorDAG.addVertex(op13); operatorDAG.addVertex(op21);
    operatorDAG.addVertex(op31);

    operatorDAG.addEdge(op11, op12); operatorDAG.addEdge(op12, op13);
    operatorDAG.addEdge(op21, op13); operatorDAG.addEdge(op31, op13);

    final Set<Operator> src1Ops = new HashSet<>();
    final Set<Operator> src2Ops = new HashSet<>();
    final Set<Operator> src3Ops = new HashSet<>();
    src1Ops.add(op11); src2Ops.add(op21); src3Ops.add(op31);
    sourceMap.put(src1, src1Ops);
    sourceMap.put(src2, src2Ops);
    sourceMap.put(src3, src3Ops);

    final Set<Sink> op13Sinks = new HashSet<>();
    op13Sinks.add(sink1);
    sinkMap.put(op13, op13Sinks);

    final PhysicalPlan<Operator> physicalPlan =
        new DefaultPhysicalPlanImpl<>(sourceMap, operatorDAG, sinkMap);
    final Injector injector = Tang.Factory.getTang().newInjector();
    final QueryPartitioner queryPartitioner =
        injector.getInstance(QueryPartitioner.class);

    // convert
    final PhysicalPlan<PartitionedQuery> chainedPhysicalPlan =
        queryPartitioner.chainOperators(physicalPlan);

    // check
    final PartitionedQuery op11op12 = new DefaultPartitionedQuery();
    op11op12.insertToTail(op11); op11op12.insertToTail(op12);

    final PartitionedQuery op13chain = new DefaultPartitionedQuery();
    op13chain.insertToTail(op13);

    final PartitionedQuery op21chain = new DefaultPartitionedQuery();
    op21chain.insertToTail(op21);

    final PartitionedQuery op31chain = new DefaultPartitionedQuery();
    op31chain.insertToTail(op31);

    final DAG<PartitionedQuery> partitionedQueryDAG = chainedPhysicalPlan.getOperators();
    final Iterator<PartitionedQuery> iterator = GraphUtils.topologicalSort(partitionedQueryDAG);
    int num = 0;

    // check
    final Set<PartitionedQuery> neighbors = new HashSet<>();
    neighbors.add(op13chain);
    while (iterator.hasNext()) {
      final PartitionedQuery partitionedQuery = iterator.next();
      if (partitionedQuery.equals(op11op12)) {
        Assert.assertEquals("[op11->op12]'s neighbor should be  [op13]",
            neighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(op13chain)) {
        Assert.assertEquals("[op13]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else if (partitionedQuery.equals(op21chain)) {
        Assert.assertEquals("[op21]'s neighbor should be [op13]",
            neighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(op31chain)) {
        Assert.assertEquals("[op31]'s neighbor should be [op13]",
            neighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else {
        throw new RuntimeException("PartitionedQuery mismatched: " + partitionedQuery);
      }
      num += 1;
    }
    Assert.assertEquals("The number of PartitionedQuery should be 4", 4, num);

    // src map
    final Map<Source, Set<PartitionedQuery>> chainedSrcMap = chainedPhysicalPlan.getSourceMap();
    Assert.assertEquals("The number of Source should be 3", 3, chainedSrcMap.size());

    final Set<PartitionedQuery> src1OpChain = new HashSet<>();
    src1OpChain.add(op11op12);
    Assert.assertEquals("The mapped PartitionedQuery of src1 should be [op11->op12]",
        src1OpChain, chainedSrcMap.get(src1));

    final Set<PartitionedQuery> src2OpChain = new HashSet<>();
    src2OpChain.add(op21chain);
    Assert.assertEquals("The mapped PartitionedQuery of src2 should be [op21]",
        src2OpChain, chainedSrcMap.get(src2));

    final Set<PartitionedQuery> src3OpChain = new HashSet<>();
    src3OpChain.add(op31chain);
    Assert.assertEquals("The mapped PartitionedQuery of src3 should be [op31]",
        src3OpChain, chainedSrcMap.get(src3));

    // sink map
    final Map<PartitionedQuery, Set<Sink>> chainedSinkMap = chainedPhysicalPlan.getSinkMap();
    Assert.assertEquals("The number of PartitionedQueries connected to Sink should be 1", 1, chainedSinkMap.size());

    final Set<Sink> sink1Set = new HashSet<>();
    sink1Set.add(sink1);
    Assert.assertEquals("The mapped Sink of [op13] should be sink1",
        sink1Set, chainedSinkMap.get(op13chain));
  }

  /**
   * Test fork/merge chaining.
   * PhysicalPlan:
   *             -> opB-1 ->
   * src1 -> opA -> opB-2 -> opC -> sink1
   *             -> opB-3 ->
   *
   * should be converted to the expected chained PhysicalPlan:
   *               -> [opB-1] ->
   * src1 -> [opA] -> [opB-2] -> [opC] -> sink1
   *               -> [opB-3] ->
   */
  @Test
  public void testForkAndMergeChaining() throws InjectionException {
    // Build a physical plan
    final DAG<Operator> operatorDAG = new AdjacentListDAG<>();
    final Map<Source, Set<Operator>> sourceMap = new HashMap<>();
    final Map<Operator, Set<Sink>> sinkMap = new HashMap<>();

    final Source src1 = mock(Source.class);
    final Source src2 = mock(Source.class);
    final Source src3 = mock(Source.class);

    final Operator opA = mock(Operator.class);
    when(opA.toString()).thenReturn("opA");
    final Operator opB1 = mock(Operator.class);
    when(opB1.toString()).thenReturn("opB-1");
    final Operator opB2 = mock(Operator.class);
    when(opB2.toString()).thenReturn("opB-2");
    final Operator opB3 = mock(Operator.class);
    when(opB3.toString()).thenReturn("opB-3");
    final Operator opC = mock(Operator.class);
    when(opC.toString()).thenReturn("opC");

    final Sink sink1 = mock(Sink.class);

    operatorDAG.addVertex(opA); operatorDAG.addVertex(opB1);
    operatorDAG.addVertex(opB2); operatorDAG.addVertex(opB3);
    operatorDAG.addVertex(opC);

    operatorDAG.addEdge(opA, opB1);
    operatorDAG.addEdge(opA, opB2);
    operatorDAG.addEdge(opA, opB3);
    operatorDAG.addEdge(opB1, opC);
    operatorDAG.addEdge(opB2, opC);
    operatorDAG.addEdge(opB3, opC);

    final Set<Operator> src1Ops = new HashSet<>();
    src1Ops.add(opA);
    sourceMap.put(src1, src1Ops);

    final Set<Sink> opCSinks = new HashSet<>();
    opCSinks.add(sink1);
    sinkMap.put(opC, opCSinks);

    final PhysicalPlan<Operator> physicalPlan =
        new DefaultPhysicalPlanImpl<>(sourceMap, operatorDAG, sinkMap);
    final Injector injector = Tang.Factory.getTang().newInjector();
    final QueryPartitioner queryPartitioner =
        injector.getInstance(QueryPartitioner.class);

    // convert
    final PhysicalPlan<PartitionedQuery> chainedPhysicalPlan =
        queryPartitioner.chainOperators(physicalPlan);

    // check
    final PartitionedQuery opAchain = new DefaultPartitionedQuery();
    opAchain.insertToTail(opA);

    final PartitionedQuery opB1chain = new DefaultPartitionedQuery();
    opB1chain.insertToTail(opB1);

    final PartitionedQuery opB2chain = new DefaultPartitionedQuery();
    opB2chain.insertToTail(opB2);

    final PartitionedQuery opB3chain = new DefaultPartitionedQuery();
    opB3chain.insertToTail(opB3);

    final PartitionedQuery opCchain = new DefaultPartitionedQuery();
    opCchain.insertToTail(opC);

    final DAG<PartitionedQuery> partitionedQueryDAG = chainedPhysicalPlan.getOperators();
    final Iterator<PartitionedQuery> iterator = GraphUtils.topologicalSort(partitionedQueryDAG);
    int num = 0;

    // check
    final Set<PartitionedQuery> neighbors = new HashSet<>();
    neighbors.add(opCchain);
    while (iterator.hasNext()) {
      final PartitionedQuery partitionedQuery = iterator.next();
      if (partitionedQuery.equals(opAchain)) {
        final Set<PartitionedQuery> opAneighbors = new HashSet<>();
        opAneighbors.add(opB1chain);
        opAneighbors.add(opB2chain);
        opAneighbors.add(opB3chain);
        Assert.assertEquals("[opA]'s neighbor should be  [opB-1], [opB-2], and [opB-3]",
            opAneighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(opB1chain)) {
        Assert.assertEquals("[opB1chain]'s neighbor should be [opC]",
            neighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(opB2chain)) {
        Assert.assertEquals("[opB2chain]'s neighbor should be [opC]",
            neighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(opB3chain)) {
        Assert.assertEquals("[opB3chain]'s neighbor should be [opC]",
            neighbors, partitionedQueryDAG.getNeighbors(partitionedQuery));
      } else if (partitionedQuery.equals(opCchain)) {
        Assert.assertEquals("[opCchain]'s neighbor should be empty",
            0, partitionedQueryDAG.getNeighbors(partitionedQuery).size());
      } else {
        throw new RuntimeException("PartitionedQuery mismatched: " + partitionedQuery);
      }
      num += 1;
    }
    Assert.assertEquals("The number of PartitionedQuery should be 5", 5, num);

    // src map
    final Map<Source, Set<PartitionedQuery>> chainedSrcMap = chainedPhysicalPlan.getSourceMap();
    Assert.assertEquals("The number of Source should be 1", 1, chainedSrcMap.size());

    final Set<PartitionedQuery> src1OpChain = new HashSet<>();
    src1OpChain.add(opAchain);
    Assert.assertEquals("The mapped PartitionedQuery of src1 should be [opA]",
        src1OpChain, chainedSrcMap.get(src1));

    // sink map
    final Map<PartitionedQuery, Set<Sink>> chainedSinkMap = chainedPhysicalPlan.getSinkMap();
    Assert.assertEquals("The number of PartitionedQueries connected to Sink should be 1", 1, chainedSinkMap.size());

    final Set<Sink> sink1Set = new HashSet<>();
    sink1Set.add(sink1);
    Assert.assertEquals("The mapped Sink of [opC] should be sink1",
        sink1Set, chainedSinkMap.get(opCchain));
  }
}
