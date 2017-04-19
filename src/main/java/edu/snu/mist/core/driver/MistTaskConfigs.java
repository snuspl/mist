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
package edu.snu.mist.core.driver;

import edu.snu.mist.common.rpc.AvroRPCNettyServerWrapper;
import edu.snu.mist.common.rpc.RPCServerPort;
import edu.snu.mist.core.parameters.NumTaskCores;
import edu.snu.mist.core.parameters.NumTasks;
import edu.snu.mist.core.parameters.TaskMemorySize;
import edu.snu.mist.core.parameters.TempFolderPath;
import edu.snu.mist.formats.avro.ClientToTaskMessage;
import edu.snu.mist.core.task.DefaultClientToTaskMessageImpl;
import edu.snu.mist.core.task.TaskSpecificResponderWrapper;
import edu.snu.mist.core.parameters.NumPeriodicSchedulerThreads;
import edu.snu.mist.core.task.eventProcessors.parameters.NumEventProcessors;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;

/**
 * This class contains information for setting MistTasks.
 */
final class MistTaskConfigs {

  private static final int MAX_PORT_NUM = 65535;

  /**
   * The number of MistTasks.
   */
  private final int numTasks;

  /**
   * The memory size of a MistTask.
   */
  private final int taskMemSize;

  /**
   * The number of event processors of a MistTask.
   */
  private final int numEventProcessors;

  /**
   * The number of cores of a MistTask.
   */
  private final int numTaskCores;

  /**
   * The port number of rpc server of a MistTask.
   */
  private final int rpcServerPort;

  /**
   * Temporary folder path for storing temporay jar files.
   */
  private final String tempFolderPath;

  /**
   * The number of threads for the periodic service scheduler.
   */
  private final int numSchedulerThreads;

  @Inject
  private MistTaskConfigs(@Parameter(NumTasks.class) final int numTasks,
                          @Parameter(TaskMemorySize.class) final int taskMemSize,
                          @Parameter(NumEventProcessors.class) final int numEventProcessors,
                          @Parameter(NumTaskCores.class) final int numTaskCores,
                          @Parameter(RPCServerPort.class) final int rpcServerPort,
                          @Parameter(TempFolderPath.class) final String tempFolderPath,
                          @Parameter(NumPeriodicSchedulerThreads.class) final int numSchedulerThreads) {
    this.numTasks = numTasks;
    this.numEventProcessors = numEventProcessors;
    this.taskMemSize = taskMemSize;
    this.numTaskCores = numTaskCores;
    this.tempFolderPath = tempFolderPath;
    this.rpcServerPort = rpcServerPort + 10 > MAX_PORT_NUM ? rpcServerPort - 10 : rpcServerPort + 10;
    this.numSchedulerThreads = numSchedulerThreads;
  }

  public int getNumTasks() {
   return numTasks;
  }

  public int getTaskMemSize() {
    return taskMemSize;
  }

  public int getNumEventProcessors() {
    return numEventProcessors;
  }

  public int getNumTaskCores() {
    return numTaskCores;
  }

  public int getRpcServerPort() {
    return rpcServerPort;
  }

  public String getTempFolderPath() {
    return tempFolderPath;
  }

  public int getNumSchedulerThreads() {
    return numSchedulerThreads;
  }

  public Configuration getConfiguration() {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();

    // Parameter
    jcb.bindNamedParameter(NumTasks.class, Integer.toString(numTasks));
    jcb.bindNamedParameter(TaskMemorySize.class, Integer.toString(taskMemSize));
    jcb.bindNamedParameter(NumEventProcessors.class, Integer.toString(numEventProcessors));
    jcb.bindNamedParameter(NumTaskCores.class, Integer.toString(numTaskCores));
    jcb.bindNamedParameter(RPCServerPort.class, Integer.toString(rpcServerPort));
    jcb.bindNamedParameter(TempFolderPath.class, tempFolderPath);
    jcb.bindNamedParameter(NumPeriodicSchedulerThreads.class, Integer.toString(numSchedulerThreads));


    // Implementation
    jcb.bindImplementation(ClientToTaskMessage.class, DefaultClientToTaskMessageImpl.class);
    jcb.bindConstructor(Server.class, AvroRPCNettyServerWrapper.class);
    jcb.bindConstructor(SpecificResponder.class, TaskSpecificResponderWrapper.class);
    return jcb.build();
  }
}
