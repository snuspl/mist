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
package edu.snu.mist.core.task.groupaware;

import edu.snu.mist.common.graph.DAG;
import edu.snu.mist.common.graph.MISTEdge;
import edu.snu.mist.common.operators.Operator;
import edu.snu.mist.common.operators.StateHandler;
import edu.snu.mist.core.task.*;
import edu.snu.mist.core.task.groupaware.parameters.ApplicationIdentifier;
import edu.snu.mist.core.task.groupaware.parameters.JarFilePath;
import edu.snu.mist.core.task.merging.ConfigExecutionVertexMap;
import edu.snu.mist.core.task.merging.QueryIdConfigDagMap;
import edu.snu.mist.formats.avro.*;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultApplicationInfoImpl implements ApplicationInfo {

  private static final Logger LOG = Logger.getLogger(DefaultApplicationInfoImpl.class.getName());

  private final QueryStarter queryStarter;
  private final QueryRemover queryRemover;
  private final ExecutionDags executionDags;
  private final List<Group> groups;
  private final QueryIdConfigDagMap queryIdConfigDagMap;
  private final ConfigExecutionVertexMap configExecutionVertexMap;

  private final AtomicInteger numGroups = new AtomicInteger(0);

  /**
   * The jar file path.
   */
  private final List<String> jarFilePath;

  /**
   * The application identifier.
   */
  private final String appId;

  @Inject
  private DefaultApplicationInfoImpl(final QueryStarter queryStarter,
                                     final QueryRemover queryRemover,
                                     final ExecutionDags executionDags,
                                     final QueryIdConfigDagMap queryIdConfigDagMap,
                                     @Parameter(ApplicationIdentifier.class) final String appId,
                                     @Parameter(JarFilePath.class) final String jarFilePath,
                                     final ConfigExecutionVertexMap configExecutionVertexMap) {
    this.queryStarter = queryStarter;
    this.queryRemover = queryRemover;
    this.executionDags = executionDags;
    this.groups = new LinkedList<>();
    this.queryIdConfigDagMap = queryIdConfigDagMap;
    this.configExecutionVertexMap = configExecutionVertexMap;
    this.jarFilePath = Arrays.asList(jarFilePath);
    this.appId = appId;
  }

  @Override
  public QueryStarter getQueryStarter() {
    return queryStarter;
  }

  @Override
  public QueryRemover getQueryRemover() {
    return queryRemover;
  }

  @Override
  public ExecutionDags getExecutionDags() {
    return executionDags;
  }

  @Override
  public List<Group> getGroups() {
    return groups;
  }

  @Override
  public boolean addGroup(final Group group) {
    group.setApplicationInfo(this);
    numGroups.incrementAndGet();
    return groups.add(group);
  }

  @Override
  public AtomicInteger numGroups() {
    return numGroups;
  }

  @Override
  public String getApplicationId() {
    return appId;
  }

  @Override
  public List<String> getJarFilePath() {
    return jarFilePath;
  }

  @Override
  public ApplicationInfoCheckpoint checkpoint() {
    final Map<String, AvroConfigDag> avroConfigDagMap = new HashMap<>();
    final GroupMinimumLatestWatermarkTimeStamp groupTimestamp = new GroupMinimumLatestWatermarkTimeStamp();

    if (queryIdConfigDagMap.getKeys().size() == 0) {
      LOG.log(Level.WARNING, "There are no queries in the queryIdConfigDagMap for checkpointing.");
    }
    for (final String queryId : queryIdConfigDagMap.getKeys()) {
      LOG.log(Level.INFO, "query with id {0} is being checkpointed", new Object[]{queryId});
      avroConfigDagMap.put(queryId, convertToAvroConfigDag(queryIdConfigDagMap.get(queryId), groupTimestamp));
    }

    return ApplicationInfoCheckpoint.newBuilder()
        .setAvroConfigDags(avroConfigDagMap)
        .setMinimumLatestCheckpointTimestamp(groupTimestamp.getValue())
        .setJarFilePaths(jarFilePath)
        .setApplicationId(appId)
        .build();
  }

  /**
   * Convert a ConfigDag to an AvroConfigDag.
   */
  private AvroConfigDag convertToAvroConfigDag(final DAG<ConfigVertex, MISTEdge> configDag,
                                               final GroupMinimumLatestWatermarkTimeStamp groupTimestamp) {

    // Find the minimum of the available checkpoint timestamps for the group.
    // Replaying will start from this timestamp, if this ConfigDag is used for recovery.
    // This is initiated as Long.MAX_VALUE, as this means that there are no stateful operators within this dag,
    // and therefore requires no replay.
    long latestWatermarkTimestamp = Long.MAX_VALUE;
    for (final ConfigVertex cv : configDag.getVertices()) {
      final ExecutionVertex ev = configExecutionVertexMap.get(cv);
      if (ev.getType() == ExecutionVertex.Type.OPERATOR) {
        final Operator op = ((DefaultPhysicalOperatorImpl) ev).getOperator();
        if (op instanceof StateHandler) {
          final StateHandler stateHandler = (StateHandler) op;
          latestWatermarkTimestamp = stateHandler.getLatestTimestampBeforeCheckpoint();
          groupTimestamp.compareAndSetIfSmaller(latestWatermarkTimestamp);
        }
      }
    }

    // Do the checkpointing according to the retrieved group timestamp.
    final Map<ConfigVertex, Integer> indexMap = new HashMap<>();
    final List<AvroConfigVertex> avroConfigVertexList = new ArrayList<>();
    final List<AvroConfigMISTEdge> avroConfigMISTEdgeList = new ArrayList<>();

    for (final ConfigVertex cv : configDag.getVertices()) {
      final ExecutionVertex ev = configExecutionVertexMap.get(cv);
      Map<String, Object> state = new HashMap<>();
      long checkpointTimestamp = 0L;
      if (ev.getType() == ExecutionVertex.Type.OPERATOR) {
        final Operator op = ((DefaultPhysicalOperatorImpl) ev).getOperator();
        if (op instanceof StateHandler) {
          final StateHandler stateHandler = (StateHandler) op;
          checkpointTimestamp = stateHandler.getMaxAvailableTimestamp(groupTimestamp.getValue());
          state = StateSerializer.serializeStateMap(stateHandler.getOperatorState(checkpointTimestamp));
        }
      }
      final AvroConfigVertexType type;
      if (cv.getType() == ExecutionVertex.Type.SOURCE) {
        type = AvroConfigVertexType.SOURCE;
      } else if (cv.getType() == ExecutionVertex.Type.OPERATOR) {
        type = AvroConfigVertexType.OPERATOR;
      } else {
        type = AvroConfigVertexType.SINK;
      }
      final AvroConfigVertex acv = AvroConfigVertex.newBuilder()
          .setId(cv.getId())
          .setType(type)
          .setConfiguration(cv.getConfiguration())
          .setState(state)
          .setLatestCheckpointTimestamp(checkpointTimestamp)
          .build();
      avroConfigVertexList.add(acv);
      indexMap.put(cv, avroConfigVertexList.size() - 1);
    }

    for (final ConfigVertex cv : configDag.getVertices()) {
      for (final Map.Entry<ConfigVertex, MISTEdge> entry : configDag.getEdges(cv).entrySet()) {
        final MISTEdge mEdge = entry.getValue();
        final AvroConfigMISTEdge edge = AvroConfigMISTEdge.newBuilder()
            .setIndex(mEdge.getIndex())
            .setDirection(mEdge.getDirection())
            .setFromVertexIndex(indexMap.get(cv))
            .setToVertexIndex(indexMap.get(entry.getKey()))
            .build();
        avroConfigMISTEdgeList.add(edge);
      }
    }

    return AvroConfigDag.newBuilder()
        .setAvroConfigVertices(avroConfigVertexList)
        .setAvroConfigMISTEdges(avroConfigMISTEdgeList)
        .build();
  }

  /**
   * This class serves as a wrapper for the Long class.
   * Its performance is better than that of an AtomicLong class or volatile long type
   * because there are no needs for synchronization.
   */
  private final class GroupMinimumLatestWatermarkTimeStamp {
    private long timestamp;

    public GroupMinimumLatestWatermarkTimeStamp() {
      this.timestamp = Long.MAX_VALUE;
    }

    public long getValue() {
      return timestamp;
    }

    public void compareAndSetIfSmaller(final long newValue) {
      if (newValue < timestamp) {
        timestamp = newValue;
      }
    }
  }
}