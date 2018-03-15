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

import edu.snu.mist.formats.avro.GroupStats;
import edu.snu.mist.formats.avro.IPAddress;
import edu.snu.mist.formats.avro.TaskStats;
import org.apache.reef.tang.annotations.DefaultImplementation;

/**
 * This is the interface for classes which is in charge of application-aware query allocation.
 */
@DefaultImplementation(ApplicationAwareQueryAllocationManager.class)
public interface QueryAllocationManager {

  /**
   * This method returns the mist Task where the given query is allocated.
   * @param appId the application id of the given query.
   * @return MIST task ip address where the query is allocated.
   */
  IPAddress getAllocatedTask(final String appId);

  /**
   * This method adds new task to the manager.
   * @param taskHostname
   * @return
   */
  TaskStats addTask(final String taskHostname);

  /**
   * Returns task stats for the given hostname.
   * @param taskHostname
   * @return
   */
  TaskStats getTaskStats(final String taskHostname);

  /**
   * Remove the task stats for the given address.
   * @param taskHostname
   * @return
   */
  TaskStats removeTask(final String taskHostname);

  /**
   * Create the new group.
   * @param groupStats
   * @return the newly allocated groupId.
   */
  String createGroup(final String ipAddress, final GroupStats groupStats);

  /**
   * Update the task stats.
   * @param ipAddress
   * @param taskStats
   */
  void updateTaskStats(final String ipAddress, final TaskStats taskStats);
}