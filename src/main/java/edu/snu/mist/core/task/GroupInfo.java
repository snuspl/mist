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

import edu.snu.mist.common.parameters.GroupId;
import org.apache.reef.tang.annotations.Parameter;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * A class which contains query and metric information about query group.
 */
final class GroupInfo {

  /**
   * Group id.
   */
  private final String groupId;

  /**
   * List of query ids belonging to this GroupInfo.
   */
  private final List<String> queryIdList;

  private final ExecutionDags<String> executionDags;

  private final GroupMetric groupMetric;

  @Inject
  private GroupInfo(@Parameter(GroupId.class) final String groupId,
                    final GroupMetric groupMetric,
                    final ExecutionDags executionDags) {
    this.groupId = groupId;
    this.queryIdList = new ArrayList<>();
    this.executionDags = executionDags;
    this.groupMetric = groupMetric;
  }

  public void addQueryIdToGroup(final String queryId) {
    queryIdList.add(queryId);
  }

  public List<String> getQueryIdList() {
    return queryIdList;
  }

  public GroupMetric getGroupMetric() {
    return groupMetric;
  }

  /**
   * Return the execution dags in the group.
   * @return execution dags
   */
  public ExecutionDags<String> getExecutionDags() {
    return executionDags;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof GroupInfo)) {
      return false;
    }
    final GroupInfo groupInfo = (GroupInfo) o;
    return this.queryIdList.equals(groupInfo.queryIdList) &&
        this.groupMetric.equals(groupInfo.getGroupMetric());
  }

  @Override
  public int hashCode() {
    return this.queryIdList.hashCode() * 31;
  }
}