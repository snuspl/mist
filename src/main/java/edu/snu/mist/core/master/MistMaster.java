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

import edu.snu.mist.common.rpc.*;
import edu.snu.mist.formats.avro.ClientToMasterMessage;
import edu.snu.mist.formats.avro.TaskToMasterMessage;
import edu.snu.mist.core.master.parameters.ClientToMasterServerPortNum;
import edu.snu.mist.core.master.parameters.ClientToTaskServerAddressSet;
import edu.snu.mist.core.master.parameters.TaskToMasterServerPortNum;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.tang.annotations.Unit;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.task.Task;
import org.apache.reef.task.events.CloseEvent;
import org.apache.reef.wake.EventHandler;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MistMaster communicates with 1) MistClients and 2) MistTasks.
 * For 1), avro RPC is used.
 * For 2), reef NCS is used.
 *
 * 1) MistMaster returns a list of MistTasks' ip addresses to MistClients,
 * when they send messages to MistMaster.
 * With the list of ip addresses, MistClients can connect to the mist tasks directly,
 * in order to send their queries to the tasks.
 *
 * 2) MistMaster communicates with MistTasks in order to collect information about MistTasks' loads.
 * With the information, MistMaster can decide some tasks to run the clients' queries.
 * This logic is performed by TaskSelector.
 *
 * Current MistMaster cannot add/remove Tasks at runtime.
 * TODO[MIST-#]: We need to support this feature to dynamically scale in/out Tasks.
 */
@Unit
public final class MistMaster implements Task {

  private static final Logger LOG = Logger.getLogger(MistMaster.class.getName());

  /**
   * The count down latch used for waiting and closing the main loop.
   */
  private final CountDownLatch countDownLatch;

  /**
   * Avro ipc server.
   */
  private final Server taskToMasterServer;
  private final Server clientToMasterServer;

  private final Tang tang = Tang.Factory.getTang();

  /**
   * The task manager for this master.
   */
  private final TaskManager taskManager;

  @Inject
  private MistMaster(final TaskManager taskManager,
                     @Parameter(TaskToMasterServerPortNum.class) final int taskToMasterPortNum,
                     @Parameter(ClientToMasterServerPortNum.class) final int clientToMasterPortNum,
                     @Parameter(ClientToTaskServerAddressSet.class) final Set<String> clientToTaskServerAddressSet)
      throws InjectionException {
      this.countDownLatch = new CountDownLatch(1);

    final JavaConfigurationBuilder clientToMasterServerConfBuilder = tang.newConfigurationBuilder();
    clientToMasterServerConfBuilder.bindImplementation(ClientToMasterMessage.class, TaskSelector.class);
    clientToMasterServerConfBuilder.bindConstructor(SpecificResponder.class, ClientToMasterSpecificResponderWrapper
        .class);
    clientToMasterServerConfBuilder.bindConstructor(Server.class, AvroRPCNettyServerWrapper.class);
    clientToMasterServerConfBuilder.bindNamedParameter(RPCServerPort.class, String.valueOf(clientToMasterPortNum));
    final Injector injector = tang.newInjector(clientToMasterServerConfBuilder.build());
    final TaskSelector taskSelector = injector.getInstance(TaskSelector.class);
    for (final String clientToTaskServerAddress: clientToTaskServerAddressSet) {
      taskSelector.registerRunningTask(clientToTaskServerAddress);
    }
    this.clientToMasterServer = injector.getInstance(Server.class);

    final JavaConfigurationBuilder taskToMasterServerConfBuilder = tang.newConfigurationBuilder();
    taskToMasterServerConfBuilder.bindImplementation(TaskToMasterMessage.class, DefaultTaskToMasterMessageImpl.class);
    taskToMasterServerConfBuilder.bindConstructor(SpecificResponder.class, TaskToMasterSpecificResponderWrapper.class);
    taskToMasterServerConfBuilder.bindConstructor(Server.class, AvroRPCNettyServerWrapper.class);
    taskToMasterServerConfBuilder.bindNamedParameter(RPCServerPort.class, String.valueOf(taskToMasterPortNum));
    this.taskToMasterServer = tang.newInjector(taskToMasterServerConfBuilder.build()).getInstance(Server.class);

    this.taskManager = taskManager;
  }

  @Override
  public byte[] call(final byte[] memento) throws Exception {
    LOG.log(Level.INFO, "MistMaster is started");
    countDownLatch.await();
    taskToMasterServer.close();
    clientToMasterServer.close();
    taskManager.close();
    return new byte[0];
  }

  public final class MasterCloseHandler implements EventHandler<CloseEvent> {
    @Override
    public void onNext(final CloseEvent closeEvent) {
      LOG.log(Level.INFO, "Closing master");
      countDownLatch.countDown();
    }
  }
}
