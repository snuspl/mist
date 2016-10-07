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
package edu.snu.mist.api.operators;

import edu.snu.mist.api.AvroVertexSerializable;
import edu.snu.mist.api.OperatorState;
import edu.snu.mist.api.StreamType;
import edu.snu.mist.api.functions.MISTBiConsumer;
import edu.snu.mist.api.functions.MISTFunction;
import edu.snu.mist.api.functions.MISTSupplier;
import edu.snu.mist.common.DAG;
import edu.snu.mist.formats.avro.InstantOperatorInfo;
import edu.snu.mist.formats.avro.InstantOperatorTypeEnum;
import org.apache.commons.lang.SerializationUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the necessary methods for getting information
 * about user-defined stateful operators on continuous stream.
 */
public final class ApplyStatefulOperatorStream<IN, OUT, S> extends InstantOperatorStream<IN, OUT> {

  /**
   * BiConsumer used for updating the internal state.
   * To handle primitive type states, this consumer should consume a OperatorState instance as the state.
   */
  private final MISTBiConsumer<IN, OperatorState<S>> updateStateCons;
  /**
   * Function used for producing the result stream.
   */
  private final MISTFunction<S, OUT> produceResultFunc;
  /**
   * Supplier used for initializing state.
   */
  private final MISTSupplier<S> initializeStateSup;

  public ApplyStatefulOperatorStream(final MISTBiConsumer<IN, OperatorState<S>> updateStateCons,
                                     final MISTFunction<S, OUT> produceResultFunc,
                                     final MISTSupplier<S> initializeStateSup,
                                     final DAG<AvroVertexSerializable, StreamType.Direction> dag) {
    super(StreamType.OperatorType.APPLY_STATEFUL, dag);
    this.updateStateCons = updateStateCons;
    this.produceResultFunc = produceResultFunc;
    this.initializeStateSup = initializeStateSup;
  }

  /**
   * @return the Consumer that updates the current state with the input data
   */
  public MISTBiConsumer<IN, OperatorState<S>> getUpdateStateCons() {
    return updateStateCons;
  }

  /**
   * @return the Function with one argument used for producing results
   */
  public MISTFunction<S, OUT> getProduceResultFunc() {
    return produceResultFunc;
  }

  /**
   * @return the supplier that generates the state of operation.
   */
  public MISTSupplier<S> getInitializeStateSup() {
    return initializeStateSup;
  }

  @Override
  protected InstantOperatorInfo getInstantOpInfo() {
    final InstantOperatorInfo.Builder iOpInfoBuilder = InstantOperatorInfo.newBuilder();
    iOpInfoBuilder.setInstantOperatorType(InstantOperatorTypeEnum.APPLY_STATEFUL);
    final List<ByteBuffer> serializedFunctionList = new ArrayList<>();
    serializedFunctionList.add(ByteBuffer.wrap(SerializationUtils.serialize(updateStateCons)));
    serializedFunctionList.add(ByteBuffer.wrap(SerializationUtils.serialize(produceResultFunc)));
    serializedFunctionList.add(ByteBuffer.wrap(SerializationUtils.serialize(initializeStateSup)));
    iOpInfoBuilder.setFunctions(serializedFunctionList);
    iOpInfoBuilder.setKeyIndex(null);
    return iOpInfoBuilder.build();
  }
}