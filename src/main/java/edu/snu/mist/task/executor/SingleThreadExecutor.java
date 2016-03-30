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
package edu.snu.mist.task.executor;

import edu.snu.mist.task.OperatorChainJob;
import edu.snu.mist.task.executor.parameters.MistExecutorId;
import edu.snu.mist.task.executor.queues.SchedulingQueue;
import org.apache.reef.io.network.util.StringIdentifierFactory;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.wake.Identifier;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Default mist executor which uses ThreadPoolExecutor and a blocking queue for scheduling.
 */
final class SingleThreadExecutor implements MistExecutor {
  private static final Logger LOG = Logger.getLogger(SingleThreadExecutor.class.getName());

  /**
   * A queue for scheduling jobs.
   */
  private final SchedulingQueue queue;

  /**
   * A flag if the executor is closed.
   */
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * An identifier of MistExecutor.
   */
  private final Identifier identifier;

  private final Thread thread;

  private final AtomicBoolean interrupted;

  @Inject
  private SingleThreadExecutor(final SchedulingQueue queue,
                               @Parameter(MistExecutorId.class) final String identifier,
                               final StringIdentifierFactory identifierFactory) {
    this.queue = queue;
    this.interrupted = new AtomicBoolean(false);
    this.thread = new Thread(() -> {
      while (true) {
        try {
          final Runnable r = queue.take();
          r.run();
        } catch (final InterruptedException e) {
          if (interrupted.get()) {
            break;
          }
        } catch (final Exception t) {
          throw t;
        }
      }
    });
    thread.start();
    this.identifier = identifierFactory.getNewInstance(identifier);
  }

  /**
   * Submits a OperatorChainJob to the thread pool executor.
   * The thread pool executor pushes the job into the queue and the job is scheduled.
   * @param operatorChainJob a OperatorChainJob
   */
  @Override
  public void submit(final OperatorChainJob operatorChainJob) {
    queue.add(operatorChainJob);
  }

  /**
   * Closes the executor.
   * @throws Exception
   */
  @Override
  public void close() throws Exception {
    if (closed.compareAndSet(false, true)) {
      interrupted.set(true);
      thread.interrupt();
    }
  }

  @Override
  public Identifier getIdentifier() {
    return identifier;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SingleThreadExecutor that = (SingleThreadExecutor) o;
    if (!identifier.equals(that.identifier)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return identifier.hashCode();
  }
}
