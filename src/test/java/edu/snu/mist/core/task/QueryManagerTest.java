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
package edu.snu.mist.core.task;

import edu.snu.mist.common.functions.MISTBiFunction;
import edu.snu.mist.common.functions.MISTFunction;
import edu.snu.mist.common.functions.MISTPredicate;
import edu.snu.mist.common.graph.AdjacentListDAG;
import edu.snu.mist.common.graph.DAG;
import edu.snu.mist.common.graph.MISTEdge;
import edu.snu.mist.common.operators.FilterOperator;
import edu.snu.mist.common.operators.FlatMapOperator;
import edu.snu.mist.common.operators.MapOperator;
import edu.snu.mist.common.operators.ReduceByKeyOperator;
import edu.snu.mist.common.rpc.RPCServerPort;
import edu.snu.mist.common.sources.EventGenerator;
import edu.snu.mist.common.sources.PunctuatedEventGenerator;
import edu.snu.mist.common.types.Tuple2;
import edu.snu.mist.core.driver.MistTaskConfigs;
import edu.snu.mist.core.driver.parameters.ExecutionModelOption;
import edu.snu.mist.core.driver.parameters.GroupAware;
import edu.snu.mist.core.parameters.PlanStorePath;
import edu.snu.mist.core.task.eventProcessors.parameters.DefaultNumEventProcessors;
import edu.snu.mist.core.task.globalsched.parameters.GroupSchedModelType;
import edu.snu.mist.core.task.stores.QueryInfoStore;
import edu.snu.mist.core.task.utils.TestDataGenerator;
import edu.snu.mist.core.task.utils.TestWithCountDownSink;
import edu.snu.mist.formats.avro.AvroOperatorChainDag;
import edu.snu.mist.formats.avro.Direction;
import junit.framework.Assert;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class QueryManagerTest {
  private static final Logger LOG = Logger.getLogger(QueryManagerTest.class.getName());

  /**
   * Build the query which consists of:
   * src -> flatMap -> filter -> toTupleMap -> reduceByKey -> toStringMap   -> sink1
   *                                                       -> totalCountMap -> sink2
   * The src first generates strings,
   * flatMap operator splits the sentence into words,
   * filter operator trims common words (such as "is", "the", or "or"),
   * toTupleMap maps the string words to Tuple(word, 1),
   * reduceByKey operator counts the number of occurrence of each words,
   * and it forwards the word counts to toStringMap and totalCountMap operators.
   *
   * The toStringMap operator changes the outputs to string and send it to sink1.
   * The totalCountMap operator calculates the total number of occurrence of words and send it to sink2.
   *
   * This test will verify QueryManager handles the query without any error
   * and the operators are executed correctly.
   */
  // UDF functions for operators
  private final MISTFunction<String, List<String>> flatMapFunc = (input) -> Arrays.asList(input.split(" "));
  private final MISTPredicate<String> filterFunc =
      (input) -> !(input.equals("a") || input.equals("or") || input.equals("the") || input.equals("of")
          || input.equals("in") || input.equals("at") || input.equals("that")
          || input.equals("out") || input.equals("were"));
  private final MISTFunction<String, Tuple2<String, Integer>> toTupleMapFunc = (input) -> new Tuple2<>(input, 1);
  private final MISTBiFunction<Integer, Integer, Integer> reduceByKeyFunc = (oldVal, newVal) -> oldVal + newVal;
  private final MISTFunction<Map<String, Integer>, String> toStringMapFunc = (input) -> input.toString();
  private final MISTFunction<Map<String, Integer>, Integer> totalCountMapFunc =
      (input) -> input.values().stream().reduce(0, (x, y) -> x + y);

  @Test(timeout = 10000)
  public void testSubmitComplexQueryInOption2Dispatching() throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(RPCServerPort.class, "20338");
    jcb.bindNamedParameter(DefaultNumEventProcessors.class, "4");
    jcb.bindNamedParameter(ExecutionModelOption.class, "2");
    jcb.bindNamedParameter(GroupSchedModelType.class, "dispatching");
    final Injector injector = Tang.Factory.getTang().newInjector(jcb.build());
    final MistTaskConfigs taskConfigs = injector.getInstance(MistTaskConfigs.class);
    testSubmitComplexQueryHelper(taskConfigs.getConfiguration());
  }

  @Test(timeout = 10000)
  public void testSubmitComplexQueryInOption2GroupUnaware() throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(RPCServerPort.class, "20339");
    jcb.bindNamedParameter(DefaultNumEventProcessors.class, "4");
    jcb.bindNamedParameter(ExecutionModelOption.class, "2");
    jcb.bindNamedParameter(GroupSchedModelType.class, "dispatching");
    jcb.bindNamedParameter(GroupAware.class, "false");
    final Injector injector = Tang.Factory.getTang().newInjector(jcb.build());
    final MistTaskConfigs taskConfigs = injector.getInstance(MistTaskConfigs.class);
    testSubmitComplexQueryHelper(taskConfigs.getConfiguration());
  }

  @Test(timeout = 10000)
  public void testSubmitComplexQueryInOption3() throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(RPCServerPort.class, "20334");
    jcb.bindNamedParameter(DefaultNumEventProcessors.class, "4");
    jcb.bindNamedParameter(ExecutionModelOption.class, "3");
    final Injector injector = Tang.Factory.getTang().newInjector(jcb.build());
    final MistTaskConfigs taskConfigs = injector.getInstance(MistTaskConfigs.class);
    testSubmitComplexQueryHelper(taskConfigs.getConfiguration());
  }

  @SuppressWarnings("unchecked")
  private void testSubmitComplexQueryHelper(final Configuration conf) throws Exception {
    final String queryId = "testQuery";
    final List<String> inputs = Arrays.asList(
        "mist is a cloud of tiny water droplets suspended in the atmosphere",
        "a mist rose out of the river",
        "the peaks were shrouded in mist");

    // Expected results
    final List<Map<String, Integer>> intermediateResult = getIntermediateResult(inputs);

    final List<String> expectedSink1Output = intermediateResult.stream()
        .map(input -> input.toString())
        .collect(Collectors.toList());
    final List<Integer> expectedSink2Output = intermediateResult.stream()
        .map(totalCountMapFunc)
        .collect(Collectors.toList());

    // Number of expected outputs
    final CountDownLatch countDownAllOutputs = new CountDownLatch(intermediateResult.size() * 2);

    // Create the execution DAG of the query
    final ExecutionDag executionDag = new ExecutionDag(new AdjacentListDAG<>());

    // Create source
    final TestDataGenerator dataGenerator = new TestDataGenerator(inputs);
    final EventGenerator eventGenerator =
        new PunctuatedEventGenerator(null, input -> false, null);
    final PhysicalSource src = new PhysicalSourceImpl("testSource",
        "conf", dataGenerator, eventGenerator);

    final Injector injector = Tang.Factory.getTang().newInjector(conf);

    // Create sinks
    final List<String> sink1Result = new LinkedList<>();
    final List<Integer> sink2Result = new LinkedList<>();
    final PhysicalSink sink1 = new PhysicalSinkImpl<>("sink1",
        null, new TestWithCountDownSink<String>(sink1Result, countDownAllOutputs));
    final PhysicalSink sink2 = new PhysicalSinkImpl<>("sink2",
        null, new TestWithCountDownSink<Integer>(sink2Result, countDownAllOutputs));

    // Fake operator chain dag of QueryManager
    final AvroOperatorChainDag fakeOperatorChainDag = new AvroOperatorChainDag();
    fakeOperatorChainDag.setGroupId("testGroup");
    final Tuple<String, AvroOperatorChainDag> tuple = new Tuple<>(queryId, fakeOperatorChainDag);

    // Construct execution dag
    constructExecutionDag(tuple, executionDag, src, sink1, sink2);

    // Create mock DagGenerator. It returns the above  execution dag
    final ConfigDagGenerator configDagGenerator = mock(ConfigDagGenerator.class);
    final DAG<ConfigVertex, MISTEdge> configDag = mock(DAG.class);
    when(configDagGenerator.generate(tuple.getValue())).thenReturn(configDag);
    final DagGenerator dagGenerator = mock(DagGenerator.class);
    when(dagGenerator.generate(configDag, tuple.getValue().getJarFilePaths())).thenReturn(executionDag);

    // Build QueryManager
    final QueryManager queryManager = queryManagerBuild(tuple, configDagGenerator, dagGenerator, injector);
    queryManager.create(tuple);

    // Wait until all of the outputs are generated
    countDownAllOutputs.await();

    // Check the outputs
    Assert.assertEquals(expectedSink1Output, sink1Result);
    Assert.assertEquals(expectedSink2Output, sink2Result);

    src.close();
    queryManager.close();

    // Delete plan directory and plans
    deletePlans(injector);
  }

  /**
   * Receives input string and makes the intermediate result.
   * The intermediate result is the expected result which is made by query.
   */
  private List<Map<String, Integer>> getIntermediateResult(final List<String> inputString) {
    // Expected results
    final List<Map<String, Integer>> intermediateResult =
        inputString.stream().flatMap((input) -> flatMapFunc.apply(input).stream())
            .filter(filterFunc)
            .map(input -> {
              final Map<String, Integer> map = new HashMap<String, Integer>();
              map.put(input, 1);
              return map;
            }).collect(LinkedList<Map<String, Integer>>::new, Accumulator::accept, Accumulator::combine);

    return intermediateResult;
  }

  /**
   * Construct execution dag.
   * Creates operators and adds source, dag vertices, edges and sinks to dag.
   */
  private void constructExecutionDag(final Tuple<String, AvroOperatorChainDag> tuple,
                                     final ExecutionDag executionDag,
                                     final PhysicalSource src,
                                     final PhysicalSink sink1,
                                     final PhysicalSink sink2) {

    // Create operators and operator chains
    //                     (chain1)                                  (chain2)
    // src -> [flatMap -> filter -> toTupleMap -> reduceByKey] -> [toStringMap]   -> sink1
    //                                                         -> [totalCountMap] -> sink2
    //                                                               (chain3)
    final OperatorChain chain1 = new DefaultOperatorChainImpl("testOpChain-1");
    final OperatorChain chain2 = new DefaultOperatorChainImpl("testOpChain-2");
    final OperatorChain chain3 = new DefaultOperatorChainImpl("testOpChain-3");

    final PhysicalOperator flatMap = new DefaultPhysicalOperatorImpl("flatMap",
        null, new FlatMapOperator<>(flatMapFunc), chain1);
    final PhysicalOperator filter = new DefaultPhysicalOperatorImpl("filter",
        null, new FilterOperator<>(filterFunc), chain1);
    final PhysicalOperator toTupleMap = new DefaultPhysicalOperatorImpl("toTupleMap",
        null, new MapOperator<>(toTupleMapFunc), chain1);
    final PhysicalOperator reduceByKey = new DefaultPhysicalOperatorImpl("reduceByKey",
        null, new ReduceByKeyOperator<>(0, reduceByKeyFunc), chain1);
    final PhysicalOperator toStringMap = new DefaultPhysicalOperatorImpl("toStringMap",
        null, new MapOperator<>(toStringMapFunc), chain2);
    final PhysicalOperator totalCountMap = new DefaultPhysicalOperatorImpl("totalCountMap",
        null, new MapOperator<>(totalCountMapFunc), chain3);

    // Build the execution dag
    chain1.insertToTail(flatMap);
    chain1.insertToTail(filter);
    chain1.insertToTail(toTupleMap);
    chain1.insertToTail(reduceByKey);
    chain2.insertToTail(toStringMap);
    chain3.insertToTail(totalCountMap);

    final DAG<ExecutionVertex, MISTEdge> dag = executionDag.getDag();

    // Add Source
    dag.addVertex(src);

    // Add dag vertices and edges
    dag.addVertex(chain1);
    dag.addEdge(src, chain1, new MISTEdge(Direction.LEFT));
    dag.addVertex(chain2);
    dag.addEdge(chain1, chain2, new MISTEdge(Direction.LEFT));
    dag.addVertex(chain3);
    dag.addEdge(chain1, chain3, new MISTEdge(Direction.LEFT));


    // Add Sink
    dag.addVertex(sink1);
    dag.addEdge(chain2, sink1, new MISTEdge(Direction.LEFT));
    dag.addVertex(sink2);
    dag.addEdge(chain3, sink2, new MISTEdge(Direction.LEFT));
  }

  /**
   * QueryManager Builder.
   * It receives inputs tuple, physicalPlanGenerator, injector then makes query manager.
   */
  private QueryManager queryManagerBuild(final Tuple<String, AvroOperatorChainDag> tuple,
                                         final ConfigDagGenerator configDagGenerator,
                                         final DagGenerator dagGenerator,
                                         final Injector injector) throws Exception {
    // Create mock PlanStore. It returns true and the above logical plan
    final QueryInfoStore planStore = mock(QueryInfoStore.class);
    when(planStore.load(tuple.getKey())).thenReturn(tuple.getValue());

    // Create QueryManager
    injector.bindVolatileInstance(ConfigDagGenerator.class, configDagGenerator);
    injector.bindVolatileInstance(DagGenerator.class, dagGenerator);
    injector.bindVolatileInstance(QueryInfoStore.class, planStore);

    // Submit the fake logical plan
    // The operators in the physical plan are executed
    final QueryManager queryManager = injector.getInstance(QueryManager.class);

    return queryManager;
  }

  /**
   * Receives injector and uses it to get PlanStorePath.
   * Deletes logical plans and a plan folder.
   */
  private void deletePlans(final Injector injector) throws Exception {
    // Delete plan directory and plans
    final String planStorePath = injector.getNamedInstance(PlanStorePath.class);
    final File planFolder = new File(planStorePath);
    if (planFolder.exists()) {
      final File[] destroy = planFolder.listFiles();
      for (final File des : destroy) {
        des.delete();
      }
      planFolder.delete();
    }
  }

  /**
   * This class is used for collector.
   */
  static final class Accumulator {
    /**
     * Receives inputs and creates a new map.
     * It simulates ReduceByKey operation.
     */
    public static void accept(final List<Map<String, Integer>> list, final Map<String, Integer> value) {
      if (list.size() > 0) {
        final Map<String, Integer> prevMap = list.get(list.size() - 1);
        final Map<String, Integer> newMap = new HashMap<>(prevMap);
        for (final String key : value.keySet()) {
          if (newMap.get(key) == null) {
            newMap.put(key, 1);
          } else {
            newMap.put(key, newMap.get(key) + 1);
          }
        }
        list.add(newMap);
      } else {
        final Map<String, Integer> newMap = new HashMap<>(value);
        list.add(newMap);
      }
    }

    /**
     * This is unnecessary method but we should implement it for .collector() method.
     */
    public static List<Map<String, Integer>> combine(final List<Map<String, Integer>> list1,
                                                     final List<Map<String, Integer>> list2) {
      return list1;
    }
  }
}
