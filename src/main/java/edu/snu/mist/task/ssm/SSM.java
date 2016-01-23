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

package edu.snu.mist.task.ssm;

import org.apache.reef.wake.Identifier;

import java.util.HashMap;

/**
 * This interface is the basic representation of the Stream State Manager.
 * It allows queries that use stateful operators to manage its states either into
 * the memory's CacheStorage or the DatabaseStorage.
 * In the memory, SSM keeps a map, 'queryStateMap', where the key is the query Identifier.
 * The value is another map, 'operatorStateMap', which has the operator Identifier as its key
 * and the state as its value.
 * By following the caching policy, the SSM evicts the entire query's states (queryState) to the database.
 *
 * TODO[MIST-48]: We could later save other objects other than states.
 */
public interface SSM{

  /**
   * Creates the initial states of the relevant operators in the query, and stores it in the CacheStorage.
   * This is called when the query is submitted.
   * @param operatorStateMap A map that has operators as its keys and their states as values.
   */
  void create(final HashMap<Identifier, OperatorState> operatorStateMap);

  /**
   * Reads the state of the operator from the queryStateMap's operatorStateMap in CacheStorage.
   * If it is not present in the CacheStorage, CacheStorage will fetch the relevant queryStateMap
   * from the DatabaseStorage synchronously.
   * If the CacheStorage is full, it will evict all the states of a certain query (queryStateMap)
   * according to the caching policy.
   * This is called from the operator to get its states.
   * @param operatorId Key of the operatorState map. Identifier of the operator.
   * @return the state of type I if it worked well, null if not.
   * @throws DatabaseReadException when there is an error in reading the database.
   * //TODO[MIST-108]: Return something else other than null if it fails.
   */
  OperatorState read(final Identifier operatorId) throws DatabaseReadException;

  /**
   * Update the states in the CacheStorage. The updated values will go into the DatabaseStorage when they are evicted.
   * @param operatorId The identifier of the operator to update.
   * @param state The state to update.
   * @return true if update worked well, false if not.
   * @throws DatabaseReadException when there is an error in reading the database.
   */
  boolean update(final Identifier operatorId, final OperatorState state) throws DatabaseReadException;

  /**
   * Delete all states associated with the query identifier (deleting an entire operatorStateMap of the query)
   * The deletion occurs for both the CacheStorage and the DatabaseStorage.
   * It is assumed that the query has already been deleted in the Task part.
   * @return true if deletion worked well, false if not.
   * @throws DatabaseDeleteException when there is an error in deleting the database.
   */
  boolean delete() throws DatabaseDeleteException;

  //TODO[MIST-50]: The policy on where to keep the states - in the memory or the database - should be implemented.
  //Currently, everything is saved to the memory.
}