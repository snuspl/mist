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

import edu.snu.mist.common.DAG;
import edu.snu.mist.task.sinks.Sink;
import edu.snu.mist.task.sources.SourceGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A default implementation of physical plan.
 * @param <E> Operator or OperatorChain
 */
final class DefaultPhysicalPlanImpl<E> implements PhysicalPlan<E> {

  /**
   * A map of source generator and operators.
   */
  private final Map<String, Set<E>> sourceMap;

  /**
   * A DAG of operators.
   */
  private final DAG<E> operators;

  /**
   * A map of operator and sinks.
   */
  private final Map<E, Set<Sink>> sinkMap;

  private final Set<SourceGenerator> sources;

  public DefaultPhysicalPlanImpl(final Map<SourceGenerator, Set<E>> sourceAndOperatorMap,
                                 final DAG<E> operators,
                                 final Map<E, Set<Sink>> sinkMap) {
    this.operators = operators;
    this.sinkMap = sinkMap;
    this.sources = sourceAndOperatorMap.keySet();
    this.sourceMap = new HashMap<>();
    for (final Map.Entry<SourceGenerator, Set<E>> entry : sourceAndOperatorMap.entrySet()) {
      this.sourceMap.put(entry.getKey().getIdentifier().toString(), entry.getValue());
    }
  }

  public DefaultPhysicalPlanImpl(final Set<SourceGenerator> sources,
                                 final Map<String, Set<E>> sourceMap,
                                 final DAG<E> operators,
                                 final Map<E, Set<Sink>> sinkMap) {
    this.operators = operators;
    this.sinkMap = sinkMap;
    this.sources = sources;
    this.sourceMap = sourceMap;
  }

  @Override
  public DAG<E> getOperators() {
    return operators;
  }

  @Override
  public Set<SourceGenerator> getSources() {
    return sources;
  }

  @Override
  public Map<String, Set<E>> getSourceMap() {
    return sourceMap;
  }

  @Override
  public Map<E, Set<Sink>> getSinkMap() {
    return sinkMap;
  }
}
