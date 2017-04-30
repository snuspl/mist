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
package edu.snu.mist.core.task.batchsub;

import edu.snu.mist.api.MISTQuery;
import edu.snu.mist.api.MISTQueryBuilder;
import edu.snu.mist.api.batchsub.BatchSubmissionConfiguration;
import edu.snu.mist.api.datastreams.configurations.MQTTSourceConfiguration;
import edu.snu.mist.api.datastreams.configurations.SourceConfiguration;
import edu.snu.mist.common.SerializeUtils;
import edu.snu.mist.common.functions.MISTFunction;
import edu.snu.mist.common.graph.AdjacentListDAG;
import edu.snu.mist.common.graph.DAG;
import edu.snu.mist.common.graph.MISTEdge;
import edu.snu.mist.common.parameters.MQTTBrokerURI;
import edu.snu.mist.common.parameters.MQTTTopic;
import edu.snu.mist.common.parameters.SerializedTimestampExtractUdf;
import edu.snu.mist.common.rpc.RPCServerPort;
import edu.snu.mist.core.driver.parameters.ExecutionModelOption;
import edu.snu.mist.core.parameters.PlanStorePath;
import edu.snu.mist.core.task.*;
import edu.snu.mist.core.task.eventProcessors.parameters.DefaultNumEventProcessors;
import edu.snu.mist.core.task.stores.QueryInfoStore;
import edu.snu.mist.formats.avro.*;
import junit.framework.Assert;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.formats.AvroConfigurationSerializer;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test batch submission in the query managers of option 1, 2, and 3.
 */
public final class BatchSubQueryManagerTest {
  private QueryManager manager;
  private Tuple<List<String>, AvroOperatorChainDag> tuple;
  private List<Integer> queryGroupList;
  private Injector injector;
  private AvroConfigurationSerializer avroConfigurationSerializer;
  private static final String QUERY_ID_PREFIX = "TestQueryId";
  private static final String ORIGINAL_GROUP_ID = "OriginalGroupId";
  private static final String ORIGINAL_PUB_TOPIC = "OriginalPubTopic";
  private static final String ORIGINAL_SUB_TOPIC = "OriginalSubTopic";
  private static final int START_QUERY_NUM = 10;
  private static final int BATCH_SIZE = 3;
  private static final String EXPECTED_GROUP_ID = "2";
  private static final String BROKER_URI = "tcp://localhost:12345";
  private static final MISTFunction<MqttMessage, Tuple<MqttMessage, Long>> EXTRACT_FUNC
      = (msg) -> new Tuple<>(msg, 10L);
  private static final MISTFunction<MqttMessage, String> MAP_FUNC = (msg) -> "TestData";
  private static final MISTFunction<String, String> PUB_TOPIC_FUNCTION = (groupId) -> "/group" + groupId + "/pub";
  private static final MISTFunction<String, String> SUB_TOPIC_FUNCTION = (groupId) -> "/group" + groupId + "/sub";
  private static final Logger LOG = Logger.getLogger(BatchSubQueryManagerTest.class.getName());

  /**
   * Build a simple query and make the AvroOperatorChainDag.
   * The simple query will consists of:
   * mqttSrc -> map -> mqttSink
   * This query will be duplicated and the group id, topic configuration of source and sink will be overwritten.
   */
  @Before
  public void setUp() throws Exception {
    // Make batch submission configuration
    // The batch will start from group 1 in this list and end at group 2
    queryGroupList = new LinkedList<>();
    queryGroupList.add(9);
    queryGroupList.add(2);
    queryGroupList.add(2);
    final BatchSubmissionConfiguration batchSubConfig = new BatchSubmissionConfiguration(
        PUB_TOPIC_FUNCTION, SUB_TOPIC_FUNCTION, queryGroupList, START_QUERY_NUM, BATCH_SIZE);

    // Create MQTT query having original configuration
    final SourceConfiguration sourceConfiguration = MQTTSourceConfiguration.newBuilder()
        .setBrokerURI(BROKER_URI)
        .setTopic(ORIGINAL_PUB_TOPIC)
        .setTimestampExtractionFunction(EXTRACT_FUNC)
        .build();
    final MISTQueryBuilder queryBuilder = new MISTQueryBuilder(ORIGINAL_GROUP_ID);
    queryBuilder.mqttStream(sourceConfiguration)
        .map(MAP_FUNC)
        .mqttOutput(BROKER_URI, ORIGINAL_SUB_TOPIC);
    final MISTQuery query = queryBuilder.build();

    // Make fake jar upload result
    final List<String> paths = new LinkedList<>();
    final JarUploadResult jarUploadResult = JarUploadResult.newBuilder()
        .setIsSuccess(true)
        .setMsg("Success")
        .setPaths(paths)
        .build();

    // Create AvroOperatorChainDag
    final Tuple<List<AvroVertexChain>, List<Edge>> serializedDag = query.getAvroOperatorChainDag();
    final AvroOperatorChainDag operatorChainDag = AvroOperatorChainDag.newBuilder()
        .setJarFilePaths(jarUploadResult.getPaths())
        .setAvroVertices(serializedDag.getKey())
        .setEdges(serializedDag.getValue())
        .setGroupId(query.getGroupId())
        .setPubTopicGenerateFunc(
            SerializeUtils.serializeToString(batchSubConfig.getPubTopicGenerateFunc()))
        .setSubTopicGenerateFunc(
            SerializeUtils.serializeToString(batchSubConfig.getSubTopicGenerateFunc()))
        .setQueryGroupList(batchSubConfig.getQueryGroupList())
        .setStartQueryNum(batchSubConfig.getStartQueryNum())
        .build();

    // Create query id list
    final List<String> queryIdList = new LinkedList<>();
    queryIdList.add(QUERY_ID_PREFIX + "0");
    queryIdList.add(QUERY_ID_PREFIX + "1");
    queryIdList.add(QUERY_ID_PREFIX + "2");
    tuple = new Tuple<>(queryIdList, operatorChainDag);
  }

  @After
  public void tearDown() throws Exception {
    // Close the query manager
    manager.close();
    // Delete plan directory and plans
    deletePlans();
  }

  /**
   * Test option 1 query manager.
   */
  @Test(timeout = 5000)
  public void testSubmitComplexQueryInOption1() throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(RPCServerPort.class, "20332");
    jcb.bindNamedParameter(DefaultNumEventProcessors.class, "4");
    jcb.bindNamedParameter(ExecutionModelOption.class, "1");
    injector = Tang.Factory.getTang().newInjector(jcb.build());
    testBatchSubmitQueryHelper();
  }

  /**
   * Test option 2 query manager.
   */
  @Test(timeout = 5000)
  public void testSubmitComplexQueryInOption2() throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(RPCServerPort.class, "20333");
    jcb.bindNamedParameter(DefaultNumEventProcessors.class, "4");
    jcb.bindNamedParameter(ExecutionModelOption.class, "2");
    injector = Tang.Factory.getTang().newInjector(jcb.build());
    testBatchSubmitQueryHelper();
  }

  /**
   * Test option 3 query manager.
   */
  @Test(timeout = 5000)
  public void testSubmitComplexQueryInOption3() throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(RPCServerPort.class, "20334");
    jcb.bindNamedParameter(DefaultNumEventProcessors.class, "4");
    jcb.bindNamedParameter(ExecutionModelOption.class, "3");
    injector = Tang.Factory.getTang().newInjector(jcb.build());
    testBatchSubmitQueryHelper();
  }

  /**
   * Test whether the query manager re-configure the MQTT source and sink properly.
   */
  private void testBatchSubmitQueryHelper() throws Exception {
    avroConfigurationSerializer = injector.getInstance(AvroConfigurationSerializer.class);

    // Create a fake execution DAG of the query
    final DAG<ExecutionVertex, MISTEdge> dag = new AdjacentListDAG<>();

    // Create mock DagGenerator. It returns the above fake execution dag
    final DagGenerator dagGenerator = mock(DagGenerator.class);
    when(dagGenerator.generate(Matchers.any())).thenReturn(dag);

    // Build QueryManager and create queries in batch manner
    manager = queryManagerBuild(tuple, dagGenerator);
    manager.createBatch(tuple);
    final AvroOperatorChainDag opChainDag = tuple.getValue();

    // Test whether the group id is overwritten well
    // The batch creation is expected to end at group 2
    Assert.assertEquals(EXPECTED_GROUP_ID, opChainDag.getGroupId());

    // Test whether the MQTT configuration is overwritten well
    for (final AvroVertexChain avroVertexChain : opChainDag.getAvroVertices()) {
      switch (avroVertexChain.getAvroVertexChainType()) {
        case SOURCE: {
          final Vertex vertex = avroVertexChain.getVertexChain().get(0);
          final Configuration modifiedConf = avroConfigurationSerializer.fromString(vertex.getConfiguration());

          // Restore the configuration and see whether it is overwritten well
          final Injector newInjector = Tang.Factory.getTang().newInjector(modifiedConf);
          final String mqttBrokerURI = newInjector.getNamedInstance(MQTTBrokerURI.class);
          final String mqttSubTopic = newInjector.getNamedInstance(MQTTTopic.class);
          final String serializedTimestampFunc = newInjector.getNamedInstance(SerializedTimestampExtractUdf.class);
          final MISTFunction<MqttMessage, Tuple<MqttMessage, Long>> timestampFunc =
              SerializeUtils.deserializeFromString(serializedTimestampFunc);

          // The broker URI should not be overwritten
          Assert.assertEquals(BROKER_URI, mqttBrokerURI);
          // The topic should be overwritten
          Assert.assertEquals(SUB_TOPIC_FUNCTION.apply(EXPECTED_GROUP_ID), mqttSubTopic);
          // The timestamp extract function should not be modified
          final MqttMessage tmpMsg = new MqttMessage();
          final Tuple<MqttMessage, Long> expectedTuple = EXTRACT_FUNC.apply(tmpMsg);
          final Tuple<MqttMessage, Long> actualTuple = timestampFunc.apply(tmpMsg);
          Assert.assertEquals(expectedTuple.getKey(), actualTuple.getKey());
          Assert.assertEquals(expectedTuple.getValue(), actualTuple.getValue());
          break;
        }
        case OPERATOR_CHAIN: {
          // Do nothing
          break;
        }
        case SINK: {
          final Vertex vertex = avroVertexChain.getVertexChain().get(0);
          final Configuration modifiedConf = avroConfigurationSerializer.fromString(vertex.getConfiguration());

          // Restore the configuration and see whether it is overwritten well
          final Injector newInjector = Tang.Factory.getTang().newInjector(modifiedConf);
          final String mqttBrokerURI = newInjector.getNamedInstance(MQTTBrokerURI.class);
          final String mqttPubTopic = newInjector.getNamedInstance(MQTTTopic.class);

          // The broker URI should not be overwritten
          Assert.assertEquals(BROKER_URI, mqttBrokerURI);
          // The topic should be overwritten
          Assert.assertEquals(PUB_TOPIC_FUNCTION.apply(EXPECTED_GROUP_ID), mqttPubTopic);
          break;

        }
        default: {
          throw new IllegalArgumentException("MISTTest: Invalid vertex type");
        }
      }
    }
  }

  /**
   * A builder for QueryManager.
   */
  private QueryManager queryManagerBuild(final Tuple<List<String>, AvroOperatorChainDag> tp,
                                         final DagGenerator dagGenerator) throws Exception {
    // Create mock PlanStore. It returns true and the above logical plan
    final QueryInfoStore planStore = mock(QueryInfoStore.class);
    when(planStore.saveAvroOpChainDag(Matchers.any())).thenReturn(true);
    when(planStore.load(Matchers.any())).thenReturn(tp.getValue());

    // Create QueryManager
    injector.bindVolatileInstance(DagGenerator.class, dagGenerator);
    injector.bindVolatileInstance(QueryInfoStore.class, planStore);

    // Submit the fake logical plan
    // The operators in the physical plan are executed
    final QueryManager queryManager = injector.getInstance(QueryManager.class);

    return queryManager;
  }

  /**
   * Deletes logical plans and a plan folder.
   */
  private void deletePlans() throws Exception {
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
}