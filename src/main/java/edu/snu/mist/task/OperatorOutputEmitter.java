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
package edu.snu.mist.task;

import edu.snu.mist.task.common.OutputEmitter;
import edu.snu.mist.task.executor.MistExecutor;

import java.util.Set;

/**
 * This emitter forwards current PartitionedQuery's outputs as next PartitionedQueries' inputs.
 */
final class OperatorOutputEmitter implements OutputEmitter {

  /**
   * Current PartitionedQuery.
   */
  private final PartitionedQuery currChain;

  /**
   * Next PartitionedQueries.
   */
  private final Set<PartitionedQuery> nextChains;

  OperatorOutputEmitter(final PartitionedQuery currChain,
                        final Set<PartitionedQuery> nextChains) {
    this.currChain = currChain;
    this.nextChains = nextChains;
  }

  /**
   * This method emits the outputs to next PartitionedQueries.
   * If the Executor of the current PartitionedQuery is same as that of next PartitionedQuery,
   * the OutputEmitter directly forwards outputs of the current PartitionedQuery
   * as inputs of the next PartitionedQuery.
   * Otherwise, the OutputEmitter submits a job to the Executor of the next PartitionedQuery.
   * @param output an output
   */
  @Override
  public void emit(final Object output) {
    final MistExecutor srcExecutor = currChain.getExecutor();
    for (final PartitionedQuery nextChain : nextChains) {
      final MistExecutor destExecutor = nextChain.getExecutor();
      if (srcExecutor.equals(destExecutor)) {
        nextChain.handle(output);
      } else {
        final PartitionedQueryTask partitionedQueryTask = new DefaultPartitionedQueryTask(nextChain, output);
        destExecutor.submit(partitionedQueryTask);
      }
    }
  }
}
