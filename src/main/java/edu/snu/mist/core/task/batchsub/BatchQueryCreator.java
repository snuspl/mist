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

import com.rits.cloning.Cloner;
import edu.snu.mist.api.datastreams.configurations.*;
import edu.snu.mist.common.SerializeUtils;
import edu.snu.mist.common.functions.MISTBiFunction;
import edu.snu.mist.common.functions.MISTFunction;
import edu.snu.mist.common.parameters.MQTTBrokerURI;
import edu.snu.mist.common.parameters.SerializedTimestampExtractUdf;
import edu.snu.mist.core.task.ClassLoaderProvider;
import edu.snu.mist.core.task.QueryManager;
import edu.snu.mist.formats.avro.AvroOperatorChainDag;
import edu.snu.mist.formats.avro.AvroVertexChain;
import edu.snu.mist.formats.avro.QueryControlResult;
import edu.snu.mist.formats.avro.Vertex;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Configurations;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.tang.formats.AvroConfigurationSerializer;
import org.apache.reef.tang.implementation.java.ClassHierarchyImpl;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.inject.Inject;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

/**
 * TODO[DELETE] this code is for test.
 * A batch query creator class for supporting test.
 */
public final class BatchQueryCreator {

  /**
   * A configuration serializer.
   */
  private final AvroConfigurationSerializer avroConfigurationSerializer;

  /**
   * A classloader provider.
   */
  private final ClassLoaderProvider classLoaderProvider;

  @Inject
  private BatchQueryCreator(final AvroConfigurationSerializer avroConfigurationSerializer,
                            final ClassLoaderProvider classLoaderProvider) {
    this.avroConfigurationSerializer = avroConfigurationSerializer;
    this.classLoaderProvider = classLoaderProvider;
  }

  /**
   * Duplicate the submitted queries.
   *
   * @param tuple a pair of query id list and the operator chain dag
   * @param manager a query manager
   */
  public void duplicate(final Tuple<List<String>, AvroOperatorChainDag> tuple,
                        final QueryManager manager) throws Exception {
    final List<String> queryIdList = tuple.getKey();
    final AvroOperatorChainDag operatorChainDag = tuple.getValue();

    // Get classloader
    final URL[] urls = SerializeUtils.getJarFileURLs(operatorChainDag.getJarFilePaths());
    final ClassLoader classLoader = classLoaderProvider.newInstance(urls);

    // Load the batch submission configuration
    final MISTBiFunction<String, String, String> pubTopicFunc = SerializeUtils.deserializeFromString(
        operatorChainDag.getPubTopicGenerateFunc(), classLoader);
    final MISTBiFunction<String, String, Set<String>> subTopicFunc = SerializeUtils.deserializeFromString(
        operatorChainDag.getSubTopicGenerateFunc(), classLoader);
    final List<String> groupIdList = operatorChainDag.getGroupIdList();

    // Parallelize the submission process
    final int batchThreads = groupIdList.size() / 100 + ((groupIdList.size() % 100 == 0) ? 0 : 1);
    final ExecutorService executorService = Executors.newFixedThreadPool(batchThreads);
    final List<Future> futures = new LinkedList<>();
    final boolean[] success = new boolean[batchThreads];

    for (int i = 0; i < batchThreads; i++) {
      final int threadNum = i;
      // Do a deep copy for operatorChainDag
      final AvroOperatorChainDag opChainDagClone = new Cloner().deepClone(operatorChainDag);

      futures.add(executorService.submit(new Runnable() {
        @Override
        public void run() {
          try {
            for (int j = threadNum * 100; j < (threadNum + 1) * 100 && j < queryIdList.size(); j++) {
              // Set the topic according to the query number
              final String groupId = groupIdList.get(j);
              final String queryId = queryIdList.get(j);
              final String pubTopic = pubTopicFunc.apply(groupId, queryId);
              final Iterator<String> subTopicItr = subTopicFunc.apply(groupId, queryId).iterator();

              // Overwrite the group id
              opChainDagClone.setGroupId(groupId);
              // Insert the topic information to a copied AvroOperatorChainDag
              for (final AvroVertexChain avroVertexChain : opChainDagClone.getAvroVertices()) {
                switch (avroVertexChain.getAvroVertexChainType()) {
                  case SOURCE: {
                    // It have to be MQTT source at now
                    final Vertex vertex = avroVertexChain.getVertexChain().get(0);
                    final Configuration originConf = avroConfigurationSerializer.fromString(vertex.getConfiguration(),
                        new ClassHierarchyImpl(urls));

                    // Restore the original configuration and inject the overriding topic
                    final Injector injector = Tang.Factory.getTang().newInjector(originConf);
                    final String mqttBrokerURI = injector.getNamedInstance(MQTTBrokerURI.class);
                    final MQTTSourceConfiguration.MQTTSourceConfigurationBuilder builder =
                        MQTTSourceConfiguration.newBuilder();

                    // At now, this default watermark configuration is only supported in batch submission.
                    final WatermarkConfiguration defaultWatermarkConfig = PeriodicWatermarkConfiguration.newBuilder()
                        .setWatermarkPeriod(100)
                        .setExpectedDelay(0)
                        .build();

                    try {
                      final MISTFunction extractFuncClass = injector.getInstance(MISTFunction.class);
                      // Class-based timestamp extract function config in batch submission is not allowed at now
                      throw new RuntimeException(
                          "Class-based timestamp extract function config in batch submission is not allowed at now.");
                    } catch (final InjectionException e) {
                      // Function class is not defined
                    }

                    try {
                      final String serializedFunction = injector.getNamedInstance(SerializedTimestampExtractUdf.class);
                      // Timestamp function was set
                      final MISTFunction<MqttMessage, Tuple<MqttMessage, Long>> extractFunc =
                          SerializeUtils.deserializeFromString(
                              injector.getNamedInstance(SerializedTimestampExtractUdf.class), classLoader);
                      builder
                          .setTimestampExtractionFunction(extractFunc);
                    } catch (final InjectionException e) {
                      // Timestamp function was not set
                    }

                    final Configuration modifiedDataGeneratorConf = builder
                        .setBrokerURI(mqttBrokerURI)
                        .setTopic(subTopicItr.next())
                        .build().getConfiguration();
                    final Configuration modifiedConf =
                        Configurations.merge(modifiedDataGeneratorConf, defaultWatermarkConfig.getConfiguration());

                    vertex.setConfiguration(avroConfigurationSerializer.toString(modifiedConf));
                    break;
                  }
                  case OPERATOR_CHAIN: {
                    // Do nothing
                    break;
                  }
                  case SINK: {
                    final Vertex vertex = avroVertexChain.getVertexChain().get(0);
                    final Configuration originConf = avroConfigurationSerializer.fromString(vertex.getConfiguration(),
                        new ClassHierarchyImpl(urls));

                    // Restore the original configuration and inject the overriding topic
                    final Injector injector = Tang.Factory.getTang().newInjector(originConf);
                    final String mqttBrokerURI = injector.getNamedInstance(MQTTBrokerURI.class);
                    final Configuration modifiedConf = MqttSinkConfiguration.CONF
                        .set(MqttSinkConfiguration.MQTT_BROKER_URI, mqttBrokerURI)
                        .set(MqttSinkConfiguration.MQTT_TOPIC, pubTopic)
                        .build();
                    vertex.setConfiguration(avroConfigurationSerializer.toString(modifiedConf));
                    break;
                  }
                  default: {
                    throw new IllegalArgumentException("MISTTask: Invalid vertex detected in AvroLogicalPlan!");
                  }
                }
              }

              final Tuple<String, AvroOperatorChainDag> newTuple = new Tuple<>(queryIdList.get(j), opChainDagClone);
              final QueryControlResult result = manager.create(newTuple);
              if (!result.getIsSuccess()) {
                throw new RuntimeException(j + "'th duplicated query creation failed: " + result.getMsg());
              }
            }
            // There was no exception during submission
            success[threadNum] = true;
          } catch (final Exception e) {
            e.printStackTrace();
            success[threadNum] = false;
            return;
          }
        }
      }));
    }

    // Wait to the end of submission and check whether every submission was successful or not
    boolean allSuccess = true;
    for (int i = 0; i < batchThreads; i++) {
      for (final Future future : futures) {
        while (!future.isDone()) {
          sleep(1000);
        }
        allSuccess = allSuccess && success[i];
      }
    }
    executorService.shutdown();

    if (!allSuccess) {
      throw new RuntimeException("Submission failed");
    }
  }
}