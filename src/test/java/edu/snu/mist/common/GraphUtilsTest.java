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
package edu.snu.mist.common;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class GraphUtilsTest {

  private DAG<Integer> srcDAG = new AdjacentListDAG<>();

  @Before
  public void setUp() {
    /*
     * Create a graph:
     * 1 -> 2 -> 3 -> 6 -> 7
     *        -> 4 -> 5.
     */
    srcDAG = new AdjacentListDAG<>();
    srcDAG.addVertex(1); srcDAG.addVertex(2); srcDAG.addVertex(3);
    srcDAG.addVertex(4); srcDAG.addVertex(5);
    srcDAG.addVertex(6); srcDAG.addVertex(7);

    srcDAG.addEdge(1, 2);
    srcDAG.addEdge(2, 3);
    srcDAG.addEdge(2, 4);
    srcDAG.addEdge(4, 5);
    srcDAG.addEdge(3, 6);
    srcDAG.addEdge(6, 7);
  }

  /**
   * Copy from src graph to dest graph.
   */
  @Test
  public void copyGraphTest() {
    final DAG<Integer> destDAG = new AdjacentListDAG<>();
    GraphUtils.copy(srcDAG, destDAG);

    final Set<Integer> expectedRoot = new HashSet<>();
    expectedRoot.add(1);
    Assert.assertEquals(expectedRoot, destDAG.getRootVertices());
    Assert.assertTrue(destDAG.isAdjacent(1, 2));
    Assert.assertTrue(destDAG.isAdjacent(2, 3));
    Assert.assertTrue(destDAG.isAdjacent(2, 4));
    Assert.assertTrue(destDAG.isAdjacent(4, 5));
    Assert.assertTrue(destDAG.isAdjacent(3, 6));
    Assert.assertTrue(destDAG.isAdjacent(6, 7));
  }

  /**
   * Test topological sort.
   */
  @Test
  public void topologicalSortTest() {
    final Iterator<Integer> vertices = GraphUtils.topologicalSort(srcDAG);
    while (vertices.hasNext()) {
      final Integer vertex = vertices.next();
      Assert.assertTrue("Vertex " + vertex + " should be root vertex",
          srcDAG.getRootVertices().contains(vertex));
      srcDAG.removeVertex(vertex);
    }
  }
}
