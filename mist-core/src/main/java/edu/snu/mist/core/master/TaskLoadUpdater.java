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

import edu.snu.mist.core.master.allocation.QueryAllocationManager;
import edu.snu.mist.formats.avro.IPAddress;
import edu.snu.mist.formats.avro.MasterToTaskMessage;
import org.apache.avro.AvroRemoteException;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The updater which updates task load periodically and runs on a separate thread.
 */
public class TaskLoadUpdater implements Runnable {

  private static final Logger LOG = Logger.getLogger(TaskLoadUpdater.class.getName());

  /**
   * The proxy-to-task map.
   */
  private final ProxyToTaskMap proxyToTaskMap;

  /**
   * The task-to-info map.
   */
  private final QueryAllocationManager queryAllocationManager;

  private final int clientToTaskPort;

  public TaskLoadUpdater(final ProxyToTaskMap proxyToTaskMap,
                         final QueryAllocationManager queryAllocationManager,
                         final int clientToTaskPort) {
    this.proxyToTaskMap = proxyToTaskMap;
    this.queryAllocationManager = queryAllocationManager;
    this.clientToTaskPort = clientToTaskPort;
  }

  @Override
  public synchronized void run() {
    // TODO: Parallelize task-load update process.
    for (final Map.Entry<IPAddress, MasterToTaskMessage> entry : proxyToTaskMap.entrySet()) {
      try {
        final MasterToTaskMessage proxyToTask = entry.getValue();
        final double updatedCpuLoad = (Double) proxyToTask.getTaskLoad();
        final TaskInfo taskInfo = queryAllocationManager.getTaskInfo(
            new IPAddress(entry.getKey().getHostAddress(), clientToTaskPort));
        taskInfo.setCpuLoad(updatedCpuLoad);
        LOG.log(Level.INFO, "Task {0} updated its load: {1}", new Object[]{entry.getKey().toString(), updatedCpuLoad});
      } catch (final AvroRemoteException e) {
        LOG.log(Level.INFO, "Remote error occured during connecting to task " + entry.getKey().toString());
      }
    }
  }
}
