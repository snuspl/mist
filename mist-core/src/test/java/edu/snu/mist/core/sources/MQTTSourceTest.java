/*
 * Copyright (C) 2018 Seoul National University
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
package edu.snu.mist.core.sources;

import edu.snu.mist.core.OutputEmitter;
import edu.snu.mist.core.parameters.TaskHostname;
import edu.snu.mist.core.shared.MQTTSharedResource;
import edu.snu.mist.core.utils.MqttUtils;
import io.moquette.server.Server;
import junit.framework.Assert;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class MQTTSourceTest {

  private static final String FIRST_TOPIC = "testTopic1";
  private static final String SECOND_TOPIC = "testTopic2";

  private MQTTSharedResource mqttSharedResource;
  private Server mqttBroker;

  @Before
  public void setUp() throws InjectionException, IOException {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
    jcb.bindNamedParameter(TaskHostname.class, "127.0.0.1");
    final Injector injector = Tang.Factory.getTang().newInjector(jcb.build());
    mqttSharedResource = injector.getInstance(MQTTSharedResource.class);
    // create local mqtt broker
    mqttBroker = MqttUtils.createMqttBroker();
  }

  @After
  public void tearDown() throws Exception {
    mqttSharedResource.close();
    mqttBroker.stopServer();
  }

  /**
   * Test whether the MQTTDataGenerator fetches input stream from a MQTT local broker
   * (created through Moquette), and generates data correctly.
   * Two DataGenerators subscribing different topic from identical broker will be tested.
   * @throws Exception
   */
  @Test(timeout = 4000L)
  public void testMQTTDataGenerator() throws Exception {
    final List<String> inputStream1 = new ArrayList<>();
    final List<String> inputStream2 = new ArrayList<>();
    inputStream1.add("Lorem ipsum dolor sit amet, consectetur adipiscing elit.");
    inputStream1.add("In in leo nec erat fringilla mattis eu non massa.");
    inputStream2.add("Cras quis diam suscipit, commodo enim id, pulvinar nunc.");

    final CountDownLatch dataCountDownLatch1 = new CountDownLatch(inputStream1.size());
    final CountDownLatch dataCountDownLatch2 = new CountDownLatch(inputStream2.size());
    final List<String> result1 = new ArrayList<>();
    final List<String> result2 = new ArrayList<>();

    // create data generators
    final MQTTDataGenerator dataGenerator1 = mqttSharedResource.getDataGenerator(MqttUtils.BROKER_URI, FIRST_TOPIC);
    final MQTTDataGenerator dataGenerator2 = mqttSharedResource.getDataGenerator(MqttUtils.BROKER_URI, SECOND_TOPIC);
    final SourceTestEventGenerator eventGenerator1 = new SourceTestEventGenerator(result1, dataCountDownLatch1);
    final SourceTestEventGenerator eventGenerator2 = new SourceTestEventGenerator(result2, dataCountDownLatch2);
    dataGenerator1.setEventGenerator(eventGenerator1);
    dataGenerator2.setEventGenerator(eventGenerator2);

    dataGenerator1.setup();
    dataGenerator2.setup();
    dataGenerator1.start();
    dataGenerator2.start();
    // Wait for the MQTT data generator to subscribe.
    Thread.sleep(500);

    // create test client and publish data
    final PublishTestClient publishClient = new PublishTestClient(MqttUtils.BROKER_URI);
    publishClient.publish(inputStream1, FIRST_TOPIC);
    publishClient.publish(inputStream2, SECOND_TOPIC);

    dataCountDownLatch1.await();
    dataCountDownLatch2.await();

    publishClient.stop();
    dataGenerator1.close();
    dataGenerator2.close();

    Assert.assertEquals(inputStream1, result1);
    Assert.assertEquals(inputStream2, result2);
  }

  /**
   * Simple Paho MQTT publishing client for test.
   */
  private final class PublishTestClient implements MqttCallback {
    /**
     * The actual Paho MQTT client.
     */
    private MqttClient client;

    /**
     * Construct a client connected with target MQTT broker.
     * @param brokerURI the URI of broker to connect
     */
    PublishTestClient(final String brokerURI) {
      try {
        this.client = new MqttClient(brokerURI, "MistTestClient");
        client.connect();
        client.setCallback(this);
      } catch (final MqttException e) {
        e.printStackTrace();
      }
    }

    /**
     * Publish message toward the connected broker.
     */
    void publish(final List<String> messageList,
                 final String topic) {
      try {
        for (final String messagePayload : messageList) {
          client.publish(topic, messagePayload.getBytes(), 1, false);
        }
      } catch (final MqttException e) {
        e.printStackTrace();
      }
    }

    /**
     * Stop the connection between the target broker.
     */
    void stop() {
      try {
        client.disconnect();
        client.close();
      } catch (final MqttException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void connectionLost(final Throwable cause) {
      // do nothing
    }

    @Override
    public void messageArrived(final String topic, final MqttMessage message) {
      // do nothing
    }

    @Override
    public void deliveryComplete(final IMqttDeliveryToken token) {
      // do nothing
    }
  }

  /**
   * Event Generator for source test.
   * It stores the data which are sent from mqtt data generator.
   */
  private final class SourceTestEventGenerator implements EventGenerator<MqttMessage> {
    private final List<String> dataList;
    private final CountDownLatch dataCountDownLatch;

    SourceTestEventGenerator(final List<String> dataList,
                             final CountDownLatch dataCountDownLatch) {
      this.dataList = dataList;
      this.dataCountDownLatch = dataCountDownLatch;
    }

    @Override
    public void emitData(final MqttMessage input) {
      dataList.add(new String(input.getPayload()));
      dataCountDownLatch.countDown();
    }

    @Override
    public void start() {
      // do nothing
    }

    @Override
    public OutputEmitter getOutputEmitter() {
      return null;
    }

    @Override
    public void close() {
      // do nothing
    }

    @Override
    public void setOutputEmitter(final OutputEmitter emitter) {
      // do nothing
    }
  }
}
