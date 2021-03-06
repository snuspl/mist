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

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that represents whether mist master is ready to receive query submission or not.
 */
public final class MasterSetupFinished {

  private final AtomicBoolean ready;

  @Inject
  private MasterSetupFinished() {
    this.ready = new AtomicBoolean(false);
  }

  public void setFinished() {
    ready.set(true);
  }

  public boolean isFinished() {
    return ready.get();
  }
}
