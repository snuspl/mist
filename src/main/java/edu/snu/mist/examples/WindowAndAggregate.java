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
import edu.snu.mist.api.MISTQuery;
import edu.snu.mist.api.MISTQueryBuilder;
import edu.snu.mist.api.functions.MISTFunction;
import edu.snu.mist.api.sink.builder.TextSocketSinkConfiguration;
import edu.snu.mist.api.sources.builder.TextSocketSourceConfiguration;
import edu.snu.mist.api.window.*;
import edu.snu.mist.examples.parameters.SourceAddress;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.tang.formats.CommandLine;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Example client which submits a query that makes window collecting inputs and aggregates them.
 */
public final class WindowAndAggregate {
  /**
   * Submit a windowing and aggregating query.
   * The query reads strings from a source server, puts them into window,
   * concatenates all inputs in the window into a single string using toString function, and
   * print out the start and end time of the window.
   * @return result of the submission
   * @throws IOException
   * @throws InjectionException
   */
  public static APIQueryControlResult submitQuery(final Configuration configuration)
      throws IOException, InjectionException, URISyntaxException {
    // configurations for source and sink
    final String sourceSocket = Tang.Factory.getTang().newInjector(configuration).getNamedInstance(SourceAddress.class);
    final TextSocketSourceConfiguration localTextSocketSourceConf =
        MISTExampleUtils.getLocalTextSocketSourceConf(sourceSocket);
    final TextSocketSinkConfiguration localTextSocketSinkConf = MISTExampleUtils.getLocalTextSocketSinkConf();
    // configurations for windowing and aggregation
    final WindowSizePolicy sizePolicy = new TimeSizePolicy(5000);
    final WindowEmitPolicy emitPolicy = new TimeEmitPolicy(2500);
    final MISTFunction<WindowData<String>, String> aggregateFunc =
        (windowData) -> {
          return windowData.getDataCollection().toString() + ", window is started at " +
              windowData.getStart() + ", window is ended at " + windowData.getEnd() + ".";
        };

    final MISTQueryBuilder queryBuilder = new MISTQueryBuilder();
    queryBuilder.socketTextStream(localTextSocketSourceConf)
        .window(sizePolicy, emitPolicy)
        .aggregateWindow(aggregateFunc)
        .textSocketOutput(localTextSocketSinkConf);
    final MISTQuery query = queryBuilder.build();

    return MISTExampleUtils.submit(query, configuration);
  }

  /**
   * Set the environment(Hostname and port of driver, source, and sink) and submit a query.
   * @param args command line parameters
   * @throws Exception
   */
  public static void main(final String[] args) throws Exception {
    final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();

    final CommandLine commandLine = MISTExampleUtils.getDefaultCommandLine(jcb)
        .registerShortNameOfClass(SourceAddress.class) // Additional parameter
        .processCommandLine(args);

    if (commandLine == null) {  // Option '?' was entered and processCommandLine printed the help.
      return;
    }

    Thread sinkServer = new Thread(MISTExampleUtils.getSinkServer());
    sinkServer.start();

    final APIQueryControlResult result = submitQuery(jcb.build());
    System.out.println("Query submission result: " + result.getQueryId());
  }

  /**
   * Must not be instantiated.
   */
  private WindowAndAggregate(){
  }
}