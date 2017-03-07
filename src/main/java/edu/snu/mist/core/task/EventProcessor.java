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
 * This class processes events of queries
 * by picking up an operator chain from the OperatorChainManager.
 */
public final class EventProcessor implements Runnable {

  /**
   * The operator chain manager for picking up a chain for event processing.
   */
  private final OperatorChainManager operatorChainManager;

  public EventProcessor(final OperatorChainManager operatorChainManager) {
    this.operatorChainManager = operatorChainManager;
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        final OperatorChain query = operatorChainManager.pickOperatorChain();
        if (query != null) {
          query.processNextEvent();
          if (!query.isQueueEmpty()) {
            operatorChainManager.insert(query);
          }
        }
      } catch (final Exception t) {
        throw t;
      }
    }
  }
}
