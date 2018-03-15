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
package edu.snu.mist.core.master;

import edu.snu.mist.formats.avro.MasterToTaskMessage;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The wrapper class which contains proxies to managed tasks.
 */
public final class ProxyToTaskMap {

  private ConcurrentMap<String, MasterToTaskMessage> innerMap;

  @Inject
  private ProxyToTaskMap() {
    this.innerMap = new ConcurrentHashMap<>();
  }

  public void addNewProxy(final String taskHost, final MasterToTaskMessage proxyToTask) {
    innerMap.put(taskHost, proxyToTask);
  }

  public Set<Map.Entry<String, MasterToTaskMessage>> entrySet() {
    return innerMap.entrySet();
  }
}
