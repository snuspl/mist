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
package edu.snu.mist.task.operators;

import com.sun.corba.se.impl.io.TypeMismatchException;
import edu.snu.mist.api.StreamType;
import edu.snu.mist.api.WindowData;
import edu.snu.mist.task.common.MistDataEvent;
import edu.snu.mist.task.common.MistWatermarkEvent;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 * This operator apply user-defined operation to the WindowData received from window operator.
 * It can use the start and end information of WindowData also.
 * @param <IN> the type of input data
 * @param <OUT> the type of output data
 */
public final class AggregateWindowOperator<IN, OUT>
    extends OneStreamOperator {
  private static final Logger LOG = Logger.getLogger(AggregateWindowOperator.class.getName());

  /**
   * The function that processes the input WindowData.
   */
  private final Function<WindowData<IN>, OUT> aggregateFunc;

  /**
   * @param queryId identifier of the query which contains this operator
   * @param operatorId identifier of operator
   * @param aggregateFunc the function that processes the input WindowData
   */
  public AggregateWindowOperator(final String queryId,
                                 final String operatorId,
                                 final Function<WindowData<IN>, OUT> aggregateFunc) {
    super(queryId, operatorId);
    this.aggregateFunc = aggregateFunc;
  }

  @Override
  public StreamType.OperatorType getOperatorType() {
    return StreamType.OperatorType.AGGREGATE_WINDOW;
  }

  @Override
  public void processLeftData(final MistDataEvent input) {
    if (input.getValue() instanceof WindowData) {
      input.setValue(aggregateFunc.apply((WindowData<IN>) input.getValue()));
      outputEmitter.emitData(input);
    } else {
      throw new TypeMismatchException(
          "The input value for aggregate window operator is not an instance of WindowData.");
    }
  }

  @Override
  public void processLeftWatermark(final MistWatermarkEvent input) {
    outputEmitter.emitWatermark(input);
  }
}
