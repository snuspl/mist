/*
 * Copyright (C) 2017 Seoul National University
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
package edu.snu.mist.core.task;

import edu.snu.mist.common.graph.DAG;
import edu.snu.mist.common.graph.MISTEdge;

import javax.inject.Inject;
import java.util.*;

/**
 * This algorithm finds the sub-dag between submitted dag and execution dag in DFS order.
 */
final class DfsSubDagFindAlgorithm implements SubDagFindAlgorithm {


  @Inject
  private DfsSubDagFindAlgorithm() {
  }

  /**
   * Find the sub-dag in DFS order.
   * @param executionDag execution dag
   * @param submittedDag submitted dag that is newly submitted
   * @return
   */
  @Override
  public Map<ExecutionVertex, ExecutionVertex> findSubDag(final DAG<ExecutionVertex, MISTEdge> executionDag,
                                                          final DAG<ExecutionVertex, MISTEdge> submittedDag) {
    // Key: vertex of the submitted dag, Value: vertex of the execution dag
    final Map<ExecutionVertex, ExecutionVertex> vertexMap = new HashMap<>();
    final Set<ExecutionVertex> visited = new HashSet<>(submittedDag.numberOfVertices());
    final Set<ExecutionVertex> markedVertices = new HashSet<>();

    final Set<ExecutionVertex> sourcesInExecutionDag = executionDag.getRootVertices();
    for (final ExecutionVertex executionVertex : submittedDag.getRootVertices()) {
      final PhysicalSource sourceInSubmitDag = (PhysicalSource) executionVertex;
      final ExecutionVertex sameVertex = findSameVertex(sourcesInExecutionDag, sourceInSubmitDag);
      if (sameVertex != null) {
        // do dfs search
        dfsSearch(executionDag, submittedDag, markedVertices, sameVertex, sourceInSubmitDag, vertexMap, visited);
      }
    }
    return vertexMap;
  }

  /**
   * Recursive function that traverses the dag in DFS order.
   * @param executionDag execution dag
   * @param submittedDag submitted dag
   * @param markedVertices a set for checking join/union operator
   * @param currExecutionDagVertex currently searched vertex of the execution dag
   * @param currSubmitDagVertex currently searched vertex of the submitted dag
   * @param vertexMap a map that holds vertices of the sub-dag
   * @param visited a set for checking visited vertices
   */
  private void dfsSearch(final DAG<ExecutionVertex, MISTEdge> executionDag,
                         final DAG<ExecutionVertex, MISTEdge> submittedDag,
                         final Set<ExecutionVertex> markedVertices,
                         final ExecutionVertex currExecutionDagVertex,
                         final ExecutionVertex currSubmitDagVertex,
                         final Map<ExecutionVertex, ExecutionVertex> vertexMap,
                         final Set<ExecutionVertex> visited) {
    // Check if the vertex is already visited
    if (visited.contains(currSubmitDagVertex)) {
      return;
    }

    // Check if the operator is join or union
    if (submittedDag.getInDegree(currSubmitDagVertex) > 1 && !markedVertices.contains(currSubmitDagVertex)) {
      markedVertices.add(currSubmitDagVertex);
      return;
    }

    // Set the same vertex btw submitted dag and mergeable execution Dag.
    vertexMap.put(currSubmitDagVertex, currExecutionDagVertex);
    visited.add(currSubmitDagVertex);

    // traverse edges of the submitted dag
    // We should compare each child node with the child nodes of the execution dag
    for (final Map.Entry<ExecutionVertex, MISTEdge> entry :
        submittedDag.getEdges(currSubmitDagVertex).entrySet()) {
      final Map<ExecutionVertex, MISTEdge> childNodesOfExecutionDag =
          executionDag.getEdges(currExecutionDagVertex);
      final ExecutionVertex sameVertex =
          findSameVertex(childNodesOfExecutionDag.keySet(), entry.getKey());
      if (sameVertex != null) {
        // First, we need to check if the vertex has union or join operator
        // dfs search
        dfsSearch(executionDag, submittedDag, markedVertices, sameVertex, entry.getKey(), vertexMap, visited);
      }
    }
  }


  /**
   * Get the configuration of the operator chain.
   * @param operatorChain operator chain
   * @return configuration of the operator chain
   */
  private List<String> getOperatorChainConfig(final OperatorChain operatorChain) {
    final List<String> conf = new ArrayList<>(operatorChain.size());
    for (int i = 0; i < operatorChain.size(); i++) {
      conf.add(operatorChain.get(i).getConfiguration());
    }
    return conf;
  }

  /**
   * Find a same execution vertex from the set of vertices.
   * @param vertices a set of vertices
   * @param v vertex to be found in the set of vertices
   * @return same vertex with v
   */
  private ExecutionVertex findSameVertex(final Collection<ExecutionVertex> vertices, final ExecutionVertex v) {
    switch (v.getType()) {
      case OPERATOR_CHIAN:
        // We need to consider OperatorChain
        for (final ExecutionVertex vertex : vertices) {
          if (vertex.getType() == ExecutionVertex.Type.OPERATOR_CHIAN) {
            final List<String> vConf = getOperatorChainConfig((OperatorChain)v);
            final List<String> vertexConf = getOperatorChainConfig((OperatorChain)vertex);
            if (vConf.equals(vertexConf)) {
              return vertex;
            }
          }
        }
        break;
      case SOURCE:
        for (final ExecutionVertex vertex : vertices) {
          if (vertex.getType() == ExecutionVertex.Type.SOURCE) {
            final PhysicalSource vertexSource = (PhysicalSource)vertex;
            final PhysicalSource vSource = (PhysicalSource)v;
            if (vertexSource.getConfiguration().equals(vSource.getConfiguration())) {
              return vertex;
            }
          }
        }
      default:
        return null;
    }
    return null;
  }
}
