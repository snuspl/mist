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
import edu.snu.mist.common.parameters.GroupId;
import edu.snu.mist.common.shared.KafkaSharedResource;
import edu.snu.mist.common.shared.MQTTResource;
import edu.snu.mist.common.shared.NettySharedResource;
import edu.snu.mist.core.task.*;
import edu.snu.mist.core.task.stores.QueryInfoStore;
import edu.snu.mist.formats.avro.AvroDag;
import edu.snu.mist.formats.avro.QueryControlResult;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.JavaConfigurationBuilder;
import org.apache.reef.tang.Tang;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This QueryManager is aware of the group and manages queries per group.
 * This has a global ThreadManager that manages event processors.
 * TODO[MIST-618]: Make GroupAwareGlobalSchedQueryManager use NextGroupSelector to schedule the group.
 */
@SuppressWarnings("unchecked")
public final class GroupAwareQueryManagerImpl implements QueryManager {

  private static final Logger LOG = Logger.getLogger(GroupAwareQueryManagerImpl.class.getName());

  /**
   * Scheduler for periodic watermark emission.
   */
  private final ScheduledExecutorService scheduler;

  /**
   * A plan store.
   */
  private final QueryInfoStore planStore;

  /**
   * A map which contains groups and their information.
   */
  private final GlobalSchedGroupInfoMap groupInfoMap;

  private final GroupMap groupMap;

  /**
   * Event processor manager.
   */
  private final EventProcessorManager eventProcessorManager;

  /**
   * A dag generator that creates DAG<ConfigVertex, MISTEdge> from avro dag.
   */
  private final ConfigDagGenerator configDagGenerator;

  /**
   * A globally shared MQTTSharedResource.
   */
  private final MQTTResource mqttSharedResource;

  /**
   * A globally shared KafkaSharedResource.
   */
  private final KafkaSharedResource kafkaSharedResource;

  /**
   * A globally shared NettySharedResource.
   */
  private final NettySharedResource nettySharedResource;

  private final DagGenerator dagGenerator;

  private final GroupAllocationTableModifier groupAllocationTableModifier;

  /**
   * Default query manager in MistTask.
   */
  @Inject
  private GroupAwareQueryManagerImpl(final ScheduledExecutorServiceWrapper schedulerWrapper,
                                     final GlobalSchedGroupInfoMap groupInfoMap,
                                     final QueryInfoStore planStore,
                                     final EventProcessorManager eventProcessorManager,
                                     final ConfigDagGenerator configDagGenerator,
                                     final MQTTResource mqttSharedResource,
                                     final KafkaSharedResource kafkaSharedResource,
                                     final NettySharedResource nettySharedResource,
                                     final DagGenerator dagGenerator,
                                     final GroupAllocationTableModifier groupAllocationTableModifier,
                                     final GroupMap groupMap) {
    this.scheduler = schedulerWrapper.getScheduler();
    this.planStore = planStore;
    this.groupInfoMap = groupInfoMap;
    this.eventProcessorManager = eventProcessorManager;
    this.configDagGenerator = configDagGenerator;
    this.mqttSharedResource = mqttSharedResource;
    this.kafkaSharedResource = kafkaSharedResource;
    this.nettySharedResource = nettySharedResource;
    this.dagGenerator = dagGenerator;
    this.groupAllocationTableModifier = groupAllocationTableModifier;
    this.groupMap = groupMap;
  }

  /**
   * Start a submitted query.
   * It converts the avro operator chain dag (query) to the execution dag,
   * and executes the sources in order to receives data streams.
   * Before the queries are executed, it stores the avro  dag into disk.
   * We can regenerate the queries from the stored avro dag.
   * @param tuple a pair of the query id and the avro dag
   * @return submission result
   */
  @Override
  public QueryControlResult create(final Tuple<String, AvroDag> tuple) {
    final QueryControlResult queryControlResult = new QueryControlResult();
    queryControlResult.setQueryId(tuple.getKey());
    try {
      // Create the submitted query
      // 1) Saves the avr dag to the PlanStore and
      // converts the avro dag to the logical and execution dag
      planStore.saveAvroDag(tuple);
      final String queryId = tuple.getKey();

      // Update group information
      final String groupId = tuple.getValue().getSuperGroupId();
      final String subGroupId = tuple.getValue().getSubGroupId();


      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "Create Query [gid: {0}, sgid: {1}, qid: {2}]",
            new Object[]{groupId, subGroupId, queryId});
      }

      final List<String> jarFilePaths = tuple.getValue().getJarFilePaths();
      final Query query = addNewQueryInfo(groupId, queryId, jarFilePaths);

      // Start the submitted dag
      final DAG<ConfigVertex, MISTEdge> configDag = configDagGenerator.generate(tuple.getValue());
      final Tuple<MetaGroup, AtomicBoolean> mGroup = groupMap.get(groupId);
      mGroup.getKey().getQueryStarter().start(queryId, query, configDag, jarFilePaths);

      queryControlResult.setIsSuccess(true);
      queryControlResult.setMsg(ResultMessage.submitSuccess(tuple.getKey()));
      return queryControlResult;
    } catch (final Exception e) {
      e.printStackTrace();
      // [MIST-345] We need to release all of the information that is required for the query when it fails.
      LOG.log(Level.SEVERE, "An exception occurred while starting {0} query: {1}",
          new Object[] {tuple.getKey(), e.toString()});

      queryControlResult.setIsSuccess(false);
      queryControlResult.setMsg(e.getMessage());
      return queryControlResult;
    }
  }

  @Override
  public Query addNewQueryInfo(final String groupId, final String queryId, final List<String> jarFilePaths) {
    try {
      if (groupMap.get(groupId) == null) {
        // Add new group id, if it doesn't exist
        final JavaConfigurationBuilder jcb = Tang.Factory.getTang().newConfigurationBuilder();
        jcb.bindNamedParameter(GroupId.class, groupId);

        final Injector injector = Tang.Factory.getTang().newInjector(jcb.build());
        injector.bindVolatileInstance(MQTTResource.class, mqttSharedResource);
        injector.bindVolatileInstance(KafkaSharedResource.class, kafkaSharedResource);
        injector.bindVolatileInstance(NettySharedResource.class, nettySharedResource);
        injector.bindVolatileInstance(QueryInfoStore.class, planStore);

        final MetaGroup metaGroup = injector.getInstance(MetaGroup.class);
        metaGroup.setJarFilePaths(jarFilePaths);

        if (groupMap.putIfAbsent(groupId, new Tuple<>(metaGroup, new AtomicBoolean(false))) == null) {
          LOG.log(Level.FINE, "Create Group: {0}", new Object[]{groupId});
          final Group group = injector.getInstance(Group.class);
          groupAllocationTableModifier.addEvent(
              new WritingEvent(WritingEvent.EventType.GROUP_ADD, new Tuple<>(metaGroup, group)));

          final Tuple<MetaGroup, AtomicBoolean> mGroup = groupMap.get(groupId);
          synchronized (mGroup) {
            mGroup.getValue().set(true);
            mGroup.notifyAll();
          }
        }
      }

      final Tuple<MetaGroup, AtomicBoolean> mGroup = groupMap.get(groupId);
      synchronized (mGroup) {
        if (!mGroup.getValue().get()) {
          mGroup.wait();
        }
      }

      final Query query = new DefaultQueryImpl(queryId);
      groupAllocationTableModifier.addEvent(new WritingEvent(WritingEvent.EventType.QUERY_ADD,
          new Tuple<>(mGroup.getKey(), query)));
      return query;
    } catch (final Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public void close() throws Exception {
    scheduler.shutdown();
    planStore.close();
    eventProcessorManager.close();
  }

  /**
   * Deletes queries from MIST.
   */
  @Override
  public QueryControlResult delete(final String groupId, final String queryId) {
    groupMap.get(groupId).getKey().getQueryRemover().deleteQuery(queryId);
    final QueryControlResult queryControlResult = new QueryControlResult();
    queryControlResult.setQueryId(queryId);
    queryControlResult.setIsSuccess(true);
    queryControlResult.setMsg(ResultMessage.deleteSuccess(queryId));
    return queryControlResult;
  }
}
