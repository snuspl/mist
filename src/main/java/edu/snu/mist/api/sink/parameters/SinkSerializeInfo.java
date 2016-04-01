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
package edu.snu.mist.api.sink.parameters;

import com.google.common.collect.ImmutableMap;
import edu.snu.mist.api.SerializedType;

import java.util.Map;

/**
 * This class contains information about serialization of sink.
 */
public final class SinkSerializeInfo {

  private SinkSerializeInfo() {
    // Not called.
  }

  /**
   * The information about avro-serialized types for sink configurtaion value.
   */
  private static Map<String, SerializedType.AvroType> avroSerializedTypes =
      ImmutableMap.<String, SerializedType.AvroType>builder()
          .put(REEFNetworkSinkParameters.CODEC, SerializedType.AvroType.BYTES)
          .put(REEFNetworkSinkParameters.CONNECTION_ID, SerializedType.AvroType.STRING)
          .put(REEFNetworkSinkParameters.NAME_SERVER_HOSTNAME, SerializedType.AvroType.STRING)
          .put(REEFNetworkSinkParameters.NAME_SERVICE_PORT, SerializedType.AvroType.INT)
          .put(REEFNetworkSinkParameters.RECEIVER_ID, SerializedType.AvroType.STRING)
          .put(TextSocketSinkParameters.SOCKET_HOST_ADDRESS, SerializedType.AvroType.STRING)
          .put(TextSocketSinkParameters.SOCKET_HOST_PORT, SerializedType.AvroType.INT)
          .put(TextKafkaSinkParameters.KAFKA_HOST_ADDRESS, SerializedType.AvroType.STRING)
          .put(TextKafkaSinkParameters.KAFKA_HOST_PORT, SerializedType.AvroType.INT)
          .put(TextKafkaSinkParameters.KAFKA_TOPIC_NAME, SerializedType.AvroType.STRING)
          .put(TextKafkaSinkParameters.KAFKA_NUM_PARTITION, SerializedType.AvroType.INT)
          .build();

  /**
   * @return serialized type information of sink.
   */
  public static SerializedType.AvroType getAvroSerializedTypeInfo(final String key) {
    return avroSerializedTypes.get(key);
  }
}