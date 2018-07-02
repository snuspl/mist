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
package edu.snu.mist.core.replay;

import edu.snu.mist.core.task.ExecutionDag;
import edu.snu.mist.core.task.ExecutionVertex;
import edu.snu.mist.core.task.PhysicalSource;
import org.apache.reef.io.Tuple;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is used for replaying events with the replay server.
 * It sends REST API calls to the replay server.
 * The server must support these functions accordingly.
 */
public final class EventReplayUtils {

  private static final Logger LOG = Logger.getLogger(EventReplayUtils.class.getName());

  private EventReplayUtils() {
    // empty constructor
  }

  /**
   * Sends a POST to make an mqtt connection and subscribe request topic.
   * This method is the equivalent to :
   * > curl -X POST http://(replayServerAddress):(replayServerPort)/subscribe \
   *     -H 'Content-Type: application/json' \
   *     -d '{
   *        "brokerAddress": "(brokerAddress)",
   *        "brokerPort": (brokerPort),
   *        "topic": "(topic)"
   *        }'
   * @return true on success, else false
   */
  public static boolean subscribe(final String replayServerAddress, final int replayServerPort,
                                  final String brokerAddress, final int brokerPort, final String topic) {
    try {
      final URL url = new URL(getReplayServerUrl(replayServerAddress, replayServerPort) + "/subscribe");
      final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);

      final StringBuilder requestBody = new StringBuilder("{");
      requestBody.append("\"brokerAddress\": \"" + brokerAddress);
      requestBody.append("\", \"brokerPort\": " +  brokerPort);
      requestBody.append(", \"topic\": \"" + topic + "\"}\n");

      final OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
      writer.write(requestBody.toString());
      writer.flush();
      writer.close();

      if (conn.getResponseCode() != 200) {
        LOG.log(Level.WARNING, "Failed : HTTP error code is {0}. Failure Response message : {1}",
            new Object[]{conn.getResponseCode(), conn.getResponseMessage()});

        // End the connection.
        conn.disconnect();
        return false;
      }

      // End the connection.
      conn.disconnect();
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Sends a GET to retrieve data within a timestamp period.
   * This method is the equivalent to :
   * > curl -X GET http://(replayServerAddress):(replayServerPort)/replay \
   *     -H 'Content-Type: application/json' \
   *     -d '{
   *        "brokerURI": "(brokerURI)",
   *        "topic": "(topic)"
   *        }'
   */
  public static EventReplayResult replay(final String replayServerAddress,
                                         final int replayServerPort,
                                         final String brokerURI,
                                         final String topic) {
    return replay(replayServerAddress, replayServerPort, brokerURI, topic, -1, -1);
  }

  /**
   * Sends a GET to retrieve data within a timestamp period.
   * This method is the equivalent to :
   * > curl -X GET http://(replayServerAddress):(replayServerPort)/replay \
   *     -H 'Content-Type: application/json' \
   *     -d '{
   *        "brokerURI": "(brokerURI)",
   *        "topic": "(topic)",
   *        "startTimestamp": (startTimestamp)
   *        }'
   */
  public static EventReplayResult replay(final String replayServerAddress,
                                         final int replayServerPort,
                                         final String brokerURI,
                                         final String topic,
                                         final long startTimestamp) {
    return replay(replayServerAddress, replayServerPort, brokerURI, topic, startTimestamp, -1);
  }

  /**
   * Sends a GET to retrieve data within a timestamp period.
   * This method is the equivalent to :
   * > curl -X GET http://(replayServerAddress):(replayServerPort)/replay \
   *     -H 'Content-Type: application/json' \
   *     -d '{
   *        "brokerURI": "(brokerURI)",
   *        "topic": "(topic)",
   *        "startTimestamp": (startTimestamp),
   *        "endTimestamp": (endTimestamp)
   *        }'
   */
  public static EventReplayResult replay(final String replayServerAddress,
                                         final int replayServerPort,
                                         final String brokerURI,
                                         final String topic,
                                         final long startTimestamp,
                                         final long endTimestamp) {
    try {
      final String urlString = getReplayServerUrl(replayServerAddress, replayServerPort) + "/replay";
      final URL url = new URL(urlString);

      final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);

      final StringBuilder requestBody = new StringBuilder("{");
      requestBody.append("\"brokerURI\": \"" + brokerURI + "\", ");
      requestBody.append("\"topic\": \"" + topic + "\"");
      if (startTimestamp != -1) {
        requestBody.append(", \"startTimestamp\": " + String.valueOf(startTimestamp));
        if (endTimestamp != -1) {
          requestBody.append(", \"endTimestamp\": " + String.valueOf(endTimestamp));
        }
      }
      requestBody.append("}\n");

      final OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
      writer.write(requestBody.toString());
      writer.flush();
      writer.close();

      if (conn.getResponseCode() != 200) {
        LOG.log(Level.WARNING, "Failed : HTTP error code is {0}. Failure Response message : {1}",
            new Object[]{conn.getResponseCode(), conn.getResponseMessage()});

        // End the connection.
        conn.disconnect();
        return new EventReplayResult(false, null);
      }

      // Get the output from the server
      final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder concatenatedOutput = new StringBuilder();
      String tmpOutput;

      LOG.log(Level.INFO, "Starting to get events to replay from the server.");
      while ((tmpOutput = br.readLine()) != null) {
        concatenatedOutput.append(tmpOutput);
      }
      br.close();

      final JSONParser parser = new JSONParser();
      final JSONObject resultObject = (JSONObject) parser.parse(concatenatedOutput.toString());

      // The replayed events from the replay server.
      final JSONArray replayEventList = (JSONArray) resultObject.get("result");

      final List<Tuple<Long, MqttMessage>> mqttMessages = new ArrayList<>();

      if (replayEventList != null) {
        for (final Object eventObject : replayEventList) {
          final JSONArray event = (JSONArray) eventObject;
          final MqttMessage mqttMessage = new MqttMessage(event.get(1).toString().getBytes());
          mqttMessages.add(new Tuple<>(Long.valueOf(event.get(0).toString()), mqttMessage));
        }
      }

      // End the connection.
      conn.disconnect();
      return new EventReplayResult(true, mqttMessages);
    } catch (Exception e) {
      LOG.log(Level.WARNING, "No server was found, or another error has occurred.");
      e.printStackTrace();
      return new EventReplayResult(false, null);
    }
  }

  /**
   * Removes the data with timestamps faster than the current time.
   * This method is the equivalent to :
   * > curl -X GET http://(replayServerAddress):(replayServerPort)/checkpoint \
   *     -H 'Content-Type: application/json' \
   *     -d '{
   *        "brokerURI": "(brokerURI)",
   *        "topic": "(topic)"
   *        }'
   */
  public static boolean removeOnCheckpoint(final String replayServerAddress, final int replayServerPort,
                                           final String brokerURI, final String topic) {
    return removeOnCheckpoint(replayServerAddress, replayServerPort, brokerURI, topic, -1);
  }


  /**
   * Removes the data with timestamps faster than the given timestamp.
   * This method is the equivalent to :
   * > curl -X GET http://(replayServerAddress):(replayServerPort)/checkpoint \
   *     -H 'Content-Type: application/json' \
   *     -d '{
   *        "brokerURI": "(brokerURI)",
   *        "topic": "(topic)",
   *        "timestamp": (timestamp)
   *        }'
   */
  public static boolean removeOnCheckpoint(final String replayServerAddress, final int replayServerPort,
                                           final String brokerURI, final String topic, final long timestamp) {
    try {
      final URL url = new URL(getReplayServerUrl(replayServerAddress, replayServerPort) + "/checkpoint");
      final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);

      final StringBuilder requestBody = new StringBuilder("{");
      requestBody.append("\"brokerURI\": \"" + brokerURI + "\", ");
      requestBody.append("\"topic\": \"" + topic + "\"");
      if (timestamp != -1) {
        requestBody.append(", \"timestamp\": " + String.valueOf(timestamp));
      }
      requestBody.append("}\n");

      final OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
      writer.write(requestBody.toString());
      writer.flush();
      writer.close();

      if (conn.getResponseCode() != 200) {
        LOG.log(Level.WARNING, "Failed : HTTP error code is {0}. Failure Response message : {1}",
            new Object[]{conn.getResponseCode(), conn.getResponseMessage()});

        // End the connection.
        conn.disconnect();
        return false;
      }

      // The returned content can be ignored.

      // End the connection.
      conn.disconnect();
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static String getReplayServerUrl(final String replayServerAddress, final int replayServerPort) {
    return "http://" + replayServerAddress + ":" + replayServerPort;
  }

  /**
   * Start the sources.
   */
  public static void startSources(final Collection<ExecutionDag> executionDags) {
    LOG.log(Level.INFO, "Starting sources...");
    for (final ExecutionDag executionDag : executionDags) {
      for (final ExecutionVertex executionVertex : executionDag.getDag().getRootVertices()) {
        final PhysicalSource src = (PhysicalSource) executionVertex;
        src.start();
      }
    }
  }

  /**
   * Send the MqttMessages of the sources in chronological order.
   * @param srcAndMqttMessageListMap each value(List) is always required to be ordered in chronological order.
   */
  public static void sendMsgs(final Map<PhysicalSource, List<Tuple<Long, MqttMessage>>> srcAndMqttMessageListMap) {
    final Map<Tuple<Long, MqttMessage>, PhysicalSource> timestampMqttMessageTupleAndSrcMap = new HashMap<>();
    for (final Map.Entry<PhysicalSource, List<Tuple<Long, MqttMessage>>> entry : srcAndMqttMessageListMap.entrySet()) {
      final List<Tuple<Long, MqttMessage>> messageList = entry.getValue();
      if (!messageList.isEmpty()) {
        timestampMqttMessageTupleAndSrcMap.put(entry.getValue().remove(0), entry.getKey());
      }
    }
    while (!srcAndMqttMessageListMap.isEmpty()) {
      // Select the entry with the minimum timestamp.
      final Tuple<Tuple<Long, MqttMessage>, PhysicalSource> minTimestampEntry =
          selectAndRemoveMinTimestampEntry(timestampMqttMessageTupleAndSrcMap);
      final Tuple<Long, MqttMessage> minTimestampTuple = minTimestampEntry.getKey();
      final PhysicalSource minTimestampSrc = minTimestampEntry.getValue();

      // Emit the MqttMessage corresponding to the minimum timestamp.
      minTimestampSrc.getEventGenerator().emitData(minTimestampTuple.getValue());

      if (srcAndMqttMessageListMap.get(minTimestampSrc).isEmpty()) {
        // If there are no more messages to send for the source, exclude it completely.
        srcAndMqttMessageListMap.remove(minTimestampSrc);
      } else {
        // If there are still more messages to send for the source, put the next message in the
        // timestampMqttMessageAndSrcMap.
        timestampMqttMessageTupleAndSrcMap.put(srcAndMqttMessageListMap.get(minTimestampSrc).remove(0),
            minTimestampSrc);
      }
    }
  }

  private static Tuple<Tuple<Long, MqttMessage>, PhysicalSource> selectAndRemoveMinTimestampEntry(
      final Map<Tuple<Long, MqttMessage>, PhysicalSource> timestampMqttMessageAndSrcMap) {
    Long minimumTimestamp = Long.MAX_VALUE;
    Tuple<Tuple<Long, MqttMessage>, PhysicalSource> result = null;
    for (final Map.Entry<Tuple<Long, MqttMessage>, PhysicalSource> entry : timestampMqttMessageAndSrcMap.entrySet()) {
      final Long entryTimestamp = entry.getKey().getKey();
      if (entryTimestamp < minimumTimestamp) {
        minimumTimestamp = entryTimestamp;
        result = new Tuple<>(entry.getKey(), entry.getValue());
      }
    }
    // Remove the entry to emit from the map.
    timestampMqttMessageAndSrcMap.remove(result.getKey());
    return result;
  }
}
