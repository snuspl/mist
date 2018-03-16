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
package edu.snu.mist.common.sources;

import edu.snu.mist.common.MistCheckpointEvent;
import edu.snu.mist.common.MistDataEvent;
import edu.snu.mist.common.OutputEmitter;
import edu.snu.mist.common.functions.MISTFunction;
import edu.snu.mist.common.parameters.PeriodicCheckpointPeriod;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This abstract class represents the basic event generator.
 * If the input from DataGenerator means DataEvent, extract the timestamp from input and make it as MistDataEvent.
 * @param <I> the type of raw data input
 * @param <V> the type of the value of MistDataEvent
 */
public abstract class EventGeneratorImpl<I, V> implements EventGenerator<I> {


  /**
   * This log is for recording the case in which there was late data, which is not an exception.
   */
  private static final Logger LOG = Logger.getLogger(EventGeneratorImpl.class.getName());

  /**
   * Started to receive data stream.
   */
  private final AtomicBoolean started;

  /**
   * The function that extract timestamp from input object.
   */
  private final MISTFunction<I, Tuple<V, Long>> extractTimestampFunc;

  /**
   * The emitter that is the destination of watermark.
   */
  protected OutputEmitter outputEmitter;

  /**
   * The timestamp for the latest watermark.
   * This is used for discarding late data.
   */
  protected volatile long latestWatermarkTimestamp;

  /**
   * The period of checkpoint emission.
   */
  protected final long checkpointPeriod;

  /**
   * The unit of time for period and expectedDelay.
   */
  protected final TimeUnit timeUnit;

  /**
   * The scheduler for periodic watermark emission.
   */
  protected final ScheduledExecutorService scheduler;

  /**
   * The result of the checkpoint service execution.
   */
  protected ScheduledFuture checkpointResult;

  @Inject
  public EventGeneratorImpl(final MISTFunction<I, Tuple<V, Long>> extractTimestampFunc,
                            @Parameter(PeriodicCheckpointPeriod.class) final long checkpointPeriod,
                            final TimeUnit timeUnit,
                            final ScheduledExecutorService scheduler) {
    this.extractTimestampFunc = extractTimestampFunc;
    this.started = new AtomicBoolean(false);
    this.latestWatermarkTimestamp = 0L;
    this.checkpointPeriod = checkpointPeriod;
    this.timeUnit = timeUnit;
    this.scheduler = scheduler;
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      if (outputEmitter != null) {
        startRemain();
      } else {
        throw new RuntimeException("OutputEmitter should be set in " + EventGenerator.class.getName());
      }
    }
  }

  @Override
  public OutputEmitter getOutputEmitter() {
    return outputEmitter;
  }

  /**
   * If there is any remainder to do during start in downstream class, conduct it.
   */
  protected void startRemain() {
    if (checkpointPeriod != 0) {
      checkpointResult = scheduler.scheduleAtFixedRate(new Runnable() {
        public void run() {
          outputEmitter.emitCheckpoint(new MistCheckpointEvent());
        }
      }, checkpointPeriod, checkpointPeriod, timeUnit);
    } else {
      LOG.log(Level.INFO, "checkpointing is not turned on");
    }
  }

  @Override
  public void close() {
    if (checkpointResult != null) {
      checkpointResult.cancel(true);
    }
  }

  /**
   * Extracts the data and timestamp for MistDataEvent to generate and generate MistDataEvent.
   * If there is a timestamp extractor, then use it.
   * If not, just use current time.
   * @param input the input from DataGenerator
   * @return the MistDataEvent consists of the timestamp and input object without it
   */
  protected MistDataEvent generateEvent(final I input) {
    if (extractTimestampFunc == null) {
      long currentTimestamp = getCurrentTimestamp();
      if (currentTimestamp > latestWatermarkTimestamp) {
        return new MistDataEvent(input, currentTimestamp);
      } else {
        return null;
      }
    } else {
      Tuple<V, Long> extractionResult = extractTimestampFunc.apply(input);
      if (extractionResult.getValue() > latestWatermarkTimestamp) {
        if (extractionResult.getKey() == null || extractionResult.getValue() == null) {
          throw new IllegalArgumentException("Timestamp extraction from input data is failed. Data is " +
                  extractionResult.getKey().toString() + ", timestamp is " + extractionResult.getValue().toString());
        }
        return new MistDataEvent(extractionResult.getKey(), extractionResult.getValue());
      } else {
        LOG.log(Level.INFO, "Late data: The data timestamp was later than the latest watermark timestamp.");
        return null;
      }
    }
  }

  @Override
  public void setOutputEmitter(final OutputEmitter emitter) {
    this.outputEmitter = emitter;
  }

  /**
   * Gets current time in synchronized block.
   * @return the current time
   */
  protected synchronized long getCurrentTimestamp() {
    return System.currentTimeMillis();
  }
}
