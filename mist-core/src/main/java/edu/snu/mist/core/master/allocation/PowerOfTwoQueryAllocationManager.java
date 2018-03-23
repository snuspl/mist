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
package edu.snu.mist.core.master.allocation;

import edu.snu.mist.core.master.TaskInfo;
import edu.snu.mist.formats.avro.IPAddress;

import javax.inject.Inject;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The group-unaware QAM which adopts Power-Of-Two allocation algorithm.
 */
public final class PowerOfTwoQueryAllocationManager extends AbstractQueryAllocationManager {

  /**
   * The list of MistTasks which is necessary for query scheduling.
   */
  private final List<IPAddress> taskList;

  /**
   * The random object.
   */
  private final Random random;

  @Inject
  private PowerOfTwoQueryAllocationManager() {
    super();
    this.taskList = new CopyOnWriteArrayList<>();
    this.random = new Random();
  }

  @Override
  public IPAddress getAllocatedTask(final String appId) {
    int index0, index1;
    index0 = random.nextInt(taskList.size());
    index1 = random.nextInt(taskList.size());
    while (index1 == index0) {
      index1 = random.nextInt(taskList.size());
    }
    final IPAddress task0 = taskList.get(index0);
    final IPAddress task1 = taskList.get(index1);
    if (this.taskInfoMap.get(task0).getCpuLoad() < this.taskInfoMap.get(task1).getCpuLoad()) {
      return task0;
    } else {
      return task1;
    }
  }

  @Override
  public TaskInfo addTaskInfo(final IPAddress taskAddress, final TaskInfo taskInfo) {
    this.taskList.add(taskAddress);
    return super.addTaskInfo(taskAddress, taskInfo);
  }
}
