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

    import edu.snu.mist.formats.avro.LogicalPlan;
    import org.apache.reef.io.Tuple;
    import org.apache.reef.tang.annotations.DefaultImplementation;
    import org.apache.reef.wake.EStage;

    import java.util.concurrent.ConcurrentMap;

/**
 * This interface receives a tuple of queryId and logical plan,
 * converts the logical plan to the physical plan,
 * chains operators, allocates the chains into Executors,
 * and starts to receive input data stream of the query.
 */
@DefaultImplementation(DefaultQueryReceiverImpl.class)
public interface QueryReceiver extends EStage<Tuple<String, LogicalPlan>> {
  ConcurrentMap<String, PhysicalPlan<PartitionedQuery>> getPhysicalPlanMap();
}
