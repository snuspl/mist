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
package edu.snu.mist.common.operators;

import edu.snu.mist.common.MistCheckpointEvent;
import edu.snu.mist.common.MistDataEvent;
import edu.snu.mist.common.MistWatermarkEvent;
import edu.snu.mist.common.SerializeUtils;
import edu.snu.mist.common.functions.MISTBiPredicate;
import edu.snu.mist.common.parameters.SerializedUdf;
import edu.snu.mist.common.types.Tuple2;
import edu.snu.mist.common.windows.WindowData;
import edu.snu.mist.common.windows.WindowImpl;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This operator joins a pair of inputs in two streams that satisfies the user-defined predicate maintaining the window.
 * The two input stream has been unified to a form of Tuple2 that has data at one side and has null at the other side.
 * @param <T> the type of the first input stream data
 * @param <U> the type of the second input stream data
 */
public final class JoinOperator<T, U> extends OneStreamOperator {
  private static final Logger LOG = Logger.getLogger(JoinOperator.class.getName());

  /**
   * The user-defined predicate which checks whether two inputs from both stream are matched or not.
   */
  private final MISTBiPredicate<T, U> joinBiPredicate;

  @Inject
  private JoinOperator(
      @Parameter(SerializedUdf.class) final String serializedObject,
      final ClassLoader classLoader) throws IOException, ClassNotFoundException {
    this(SerializeUtils.deserializeFromString(serializedObject, classLoader));
  }

  @Inject
  public JoinOperator(final MISTBiPredicate<T, U> joinBiPredicate) {
    this.joinBiPredicate = joinBiPredicate;
  }

  @Override
  public void processLeftData(final MistDataEvent input) {
    try {
      final WindowData<Tuple2<T, U>> windowData = (WindowData)input.getValue();
      final Collection<T> firstInputList = new LinkedList<>();
      final Collection<U> secondInputList = new LinkedList<>();
      final Collection<Tuple2<T, U>> outputList = new LinkedList<>();

      // Classifies input collection into two input data lists
      final Iterator<Tuple2<T, U>> inputIterator = windowData.getDataCollection().iterator();
      while (inputIterator.hasNext()) {
        final Tuple2<T, U> tuple = inputIterator.next();
        if (tuple.get(0) != null) {
          firstInputList.add((T)tuple.get(0));
        } else {
          secondInputList.add((U)tuple.get(1));
        }
      }

      // Tests the inputs with user-defined predicate
      final Iterator<T> firstInputIterator = firstInputList.iterator();
      while (firstInputIterator.hasNext()) {
        final T firstInput = firstInputIterator.next();
        final Iterator<U> secondInputIterator = secondInputList.iterator();
        while (secondInputIterator.hasNext()) {
          final U secondInput = secondInputIterator.next();
          if (joinBiPredicate.test(firstInput, secondInput)) {
            outputList.add(new Tuple2<>(firstInput, secondInput));
          }
        }
      }

      // Emits windowed data
      final long windowStart = windowData.getStart();
      final long windowEnd = windowData.getEnd();
      final WindowImpl<Tuple2<T, U>> window = new WindowImpl<>(windowStart, windowEnd - windowStart + 1, outputList);

      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "{0} examines input window {1} which started at {2} and ended at {3}, and " +
                "emits window {4} with matched data list {5}",
            new Object[]{this.getClass().getName(), input, windowStart, windowEnd, window, outputList});
      }

      input.setValue(window);
      outputEmitter.emitData(input);
    } catch (final ClassCastException e) {
      throw e;
    }
  }

  @Override
  public void processLeftWatermark(final MistWatermarkEvent input) {
    outputEmitter.emitWatermark(input);
  }

  @Override
  public void processLeftCheckpoint(final MistCheckpointEvent input) {
    outputEmitter.emitCheckpoint(input);
  }
}
