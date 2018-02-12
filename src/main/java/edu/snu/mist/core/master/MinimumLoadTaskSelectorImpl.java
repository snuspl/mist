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
package edu.snu.mist.core.master;

import edu.snu.mist.formats.avro.IPAddress;
import edu.snu.mist.formats.avro.QueryInfo;
import edu.snu.mist.formats.avro.TaskLoadInfo;
import org.apache.avro.AvroRemoteException;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MinimumLoadTaskSelectorImpl implements TaskSelector {

  // IP address for client to task server
  private List<IPAddress> taskIPAddressList;

  // Map that saves load for each IPAddress.
  private TaskAddressAndLoadInfoMap taskAddressAndLoadInfoMap;

  @Inject
  private MinimumLoadTaskSelectorImpl(final TaskAddressAndLoadInfoMap taskAddressAndLoadInfoMap) {
    this.taskIPAddressList = new CopyOnWriteArrayList<>();
    this.taskAddressAndLoadInfoMap = taskAddressAndLoadInfoMap;
  }

  @Override
  public void registerRunningTask(final String taskAddress) {

    final String[] splitAddress = taskAddress.split(":");
    final IPAddress ipAddress = new IPAddress();
    ipAddress.setHostAddress(splitAddress[0]);
    ipAddress.setPort(Integer.valueOf(splitAddress[1]));
    taskIPAddressList.add(ipAddress);
  }

  @Override
  public void unregisterTask(final String taskAddress) {

  }

  /**
   * Returns the random IP address of the master's tasks.
   * This method is called by avro RPC when client calls .getTasks(msg);
   * Current implementation simply returns the list of tasks.
   * @param message a message containing query information from clients
   * @return a list of ip addresses of MistTasks
   * @throws AvroRemoteException
   */
  @Override
  public IPAddress getTask(final QueryInfo message) throws AvroRemoteException {
    double minimumLoad = 0;
    IPAddress minimumLoadIPAddress = null;
    for (final Map.Entry<IPAddress, TaskLoadInfo> entry :
         taskAddressAndLoadInfoMap.getTaskAddressAndLoadInfoMap().entrySet()) {
      final double currentLoad = entry.getValue().getTotalLoad();
      if (minimumLoad > currentLoad) {
        minimumLoad = currentLoad;
        minimumLoadIPAddress = entry.getKey();
      }
    }
    return minimumLoadIPAddress;
  }
}
