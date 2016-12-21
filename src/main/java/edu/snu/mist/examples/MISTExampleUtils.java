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

package edu.snu.mist.examples;

import edu.snu.mist.api.APIQueryControlResult;
import edu.snu.mist.api.MISTDefaultExecutionEnvironmentImpl;
import edu.snu.mist.api.MISTExecutionEnvironment;
import edu.snu.mist.api.MISTQuery;
import edu.snu.mist.api.MISTQueryControl;
import edu.snu.mist.api.sink.builder.TextSocketSinkConfiguration;
import edu.snu.mist.api.sources.builder.TextSocketSourceConfiguration;
import edu.snu.mist.examples.parameters.DriverAddress;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.tang.formats.CommandLine;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Scanner;

/**
 * Common behavior and basic defaults for MIST examples.
 */
public final class MISTExampleUtils {
  /**
   * TCP endpoint for sink server.
   */
  public static final String SINK_HOSTNAME = "localhost";
  public static final int SINK_PORT = 20330;

  /**
   * Get a new sink server.
   */
  public static SinkServer getSinkServer() {
    return new SinkServer(SINK_PORT);
  }

  /**
   * Get socket configuration for local text source.
   */
  public static TextSocketSourceConfiguration getLocalTextSocketSourceConf(final String socket) {
    final String[] sourceSocket = socket.split(":");
    final String sourceHostname = sourceSocket[0];
    final int sourcePort = Integer.parseInt(sourceSocket[1]);
    return TextSocketSourceConfiguration.newBuilder()
        .setHostAddress(sourceHostname)
        .setHostPort(sourcePort)
        .build();
  }

  /**
   * Get socket configuration for local text sink.
   */
  public static TextSocketSinkConfiguration getLocalTextSocketSinkConf() {
    return TextSocketSinkConfiguration.newBuilder()
        .setHostAddress(SINK_HOSTNAME)
        .setHostPort(SINK_PORT)
        .build();
  }

  /**
   * Submit query to MIST driver.
   */
  public static APIQueryControlResult submit(final MISTQuery query, final Configuration configuration)
      throws IOException, URISyntaxException, InjectionException {
    final String[] driverSocket =
        Tang.Factory.getTang().newInjector(configuration).getNamedInstance(DriverAddress.class).split(":");
    final String driverHostname = driverSocket[0];
    final int driverPort = Integer.parseInt(driverSocket[1]);
    final MISTExecutionEnvironment executionEnvironment =
        new MISTDefaultExecutionEnvironmentImpl(driverHostname, driverPort);
    return executionEnvironment.submit(query);
  }

  public static CommandLine getDefaultCommandLine(final JavaConfigurationBuilder jcb) {
    return new CommandLine(jcb)
        .registerShortNameOfClass(DriverAddress.class);
  }
  /**
   * Parse command line and control the query.
   * @param result
   * @throws IOException
   */
  public static void cmdLineParser(final APIQueryControlResult result) throws IOException {
    final Scanner scanner = new Scanner(System.in);
    final String cmdLine = scanner.nextLine();
    final String[] arg = cmdLine.split(" ");
    final APIQueryControlResult controlResult;

    if (arg.length != 2) {
      System.out.println("The format must be: 'control' 'queryId'");
    } else {
      switch(arg[0]) {
        case "resume":
          controlResult = MISTQueryControl.resume(arg[1], result.getTaskAddress());
          System.out.println(controlResult.getMsg());
          break;
        case "stop":
          controlResult = MISTQueryControl.stop(arg[1], result.getTaskAddress());
          System.out.println(controlResult.getMsg());
          break;
        case "delete":
          controlResult = MISTQueryControl.delete(arg[1], result.getTaskAddress());
          System.out.println(controlResult.getMsg());
          break;
        default:
          System.out.println("Control must be: resume, stop and delete");
      }
    }
  }

  /**
   * Must not be instantiated.
   */
  private MISTExampleUtils() {
  }
}
