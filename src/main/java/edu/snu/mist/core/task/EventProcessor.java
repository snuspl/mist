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

/**
 * This abstract class represents the event processor which processes events of queries.
 */
abstract class EventProcessor extends Thread {

  /**
   * The operator chain manager for picking up a chain for event processing.
   */
  protected final OperatorChainManager operatorChainManager;

  /**
   * The boolean represents whether this processor should be reaped or not.
   * To announce the change right after it is marked, this variable is declared as volatile.
   */
  protected volatile boolean closed;

  EventProcessor(final OperatorChainManager operatorChainManagerParam) {
    // Assume that operator chain manager is blocking
    this.operatorChainManager = operatorChainManagerParam;
    this.closed = false;
  }

  /**
   * Close this event processor.
   * If this method is called, this processor will be marked as closed and reaped after the current event processing.
   */
  void close() {
    closed = true;
  }
}