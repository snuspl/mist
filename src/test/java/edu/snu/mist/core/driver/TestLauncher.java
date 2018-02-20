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
package edu.snu.mist.core.driver;

import org.apache.reef.client.DriverLauncher;
import org.apache.reef.client.LauncherStatus;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.exceptions.InjectionException;

import java.util.logging.Logger;

/**
 * The test launcher for MistDriver.
 */
final class TestLauncher {
  private static final Logger LOG = Logger.getLogger(TestLauncher.class.getName());

  private TestLauncher() {
    // empty
  }

  public static LauncherStatus run(
      final Configuration runtimeConf,
      final Configuration driverConf,
      final int timeout) throws InjectionException {
    return DriverLauncher
        .getLauncher(runtimeConf)
        .run(driverConf, timeout);
  }
}
