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

import javax.inject.Inject;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A map of global schedule group info.
 */
public final class GlobalSchedGroupInfoMap {

  private final ConcurrentHashMap<String, GlobalSchedGroupInfo> map;

  @Inject
  private GlobalSchedGroupInfoMap() {
    this.map = new ConcurrentHashMap<>();
  }

  public GlobalSchedGroupInfo get(final String conf) {
    return map.get(conf);
  }

  public GlobalSchedGroupInfo putIfAbsent(final String groupId, final GlobalSchedGroupInfo groupInfo) {
    return map.putIfAbsent(groupId, groupInfo);
  }

  public GlobalSchedGroupInfo put(final String groupId, final GlobalSchedGroupInfo groupInfo) {
    return map.put(groupId, groupInfo);
  }

  public GlobalSchedGroupInfo remove(final String groupId) {
    return map.remove(groupId);
  }

  public int size() {
    return map.size();
  }

  public Collection<GlobalSchedGroupInfo> values() {
    return map.values();
  }
}