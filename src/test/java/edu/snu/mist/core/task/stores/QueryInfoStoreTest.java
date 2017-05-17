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

package edu.snu.mist.core.task.stores;

import edu.snu.mist.api.MISTQuery;
import edu.snu.mist.api.MISTQueryBuilder;
import edu.snu.mist.common.types.Tuple2;
import edu.snu.mist.core.parameters.TempFolderPath;
import edu.snu.mist.formats.avro.*;
import edu.snu.mist.utils.TestParameters;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class QueryInfoStoreTest {
  /**
   * Tests whether the PlanStore correctly saves, deletes and loads the operator chain dag.
   * @throws InjectionException
   * @throws IOException
   */
  @Test(timeout = 1000)
  public void diskStoreTest() throws InjectionException, IOException {
    // Generate a query
    final MISTQueryBuilder queryBuilder = new MISTQueryBuilder(TestParameters.GROUP_ID);
    queryBuilder.socketTextStream(TestParameters.LOCAL_TEXT_SOCKET_SOURCE_CONF)
        .flatMap(s -> Arrays.asList(s.split(" ")))
        .filter(s -> s.startsWith("A"))
        .map(s -> new Tuple2<>(s, 1))
        .reduceByKey(0, String.class, (Integer x, Integer y) -> x + y)
        .textSocketOutput(TestParameters.HOST, TestParameters.SINK_PORT);
    final MISTQuery query = queryBuilder.build();

    // Jar files
    final List<ByteBuffer> jarFiles = new LinkedList<>();
    final ByteBuffer byteBuffer1 = ByteBuffer.wrap(new byte[]{0, 1, 0, 1, 1, 1});
    final ByteBuffer byteBuffer2 = ByteBuffer.wrap(new byte[]{1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0});
    jarFiles.add(byteBuffer1);
    jarFiles.add(byteBuffer2);

    final Injector injector = Tang.Factory.getTang().newInjector();
    final QueryInfoStore store = injector.getInstance(QueryInfoStore.class);
    final String queryId1 = "testQuery1";
    final String queryId2 = "testQuery2";
    final String tmpFolderPath = injector.getNamedInstance(TempFolderPath.class);
    final File folder = new File(tmpFolderPath);

    // Store jar files
    final List<String> paths = store.saveJar(jarFiles);
    for (int i = 0; i < jarFiles.size(); i++) {
      final ByteBuffer buf = ByteBuffer.allocateDirect(jarFiles.get(i).capacity());
      final String path = paths.get(i);
      final FileInputStream fis = new FileInputStream(path);
      final FileChannel channel = fis.getChannel();
      channel.read(buf);
      Assert.assertEquals(jarFiles.get(i), buf);
    }

    // Generate logical plan
    final Tuple<List<AvroVertexChain>, List<Edge>> serializedDag = query.getAvroOperatorChainDag();
    final AvroOperatorChainDag.Builder avroOpChainDagBuilder = AvroOperatorChainDag.newBuilder();
    final AvroOperatorChainDag avroOpChainDag1 = avroOpChainDagBuilder
        .setGroupId(TestParameters.GROUP_ID)
        .setJarFilePaths(paths)
        .setAvroVertices(serializedDag.getKey())
        .setEdges(serializedDag.getValue())
        .build();
    final AvroOperatorChainDag avroOpChainDag2 = avroOpChainDagBuilder
        .setGroupId(TestParameters.GROUP_ID)
        .setJarFilePaths(paths)
        .setAvroVertices(serializedDag.getKey())
        .setEdges(serializedDag.getValue())
        .build();

    // Store the chained dag
    store.saveAvroOpChainDag(new Tuple<>(queryId1, avroOpChainDag1));
    store.saveAvroOpChainDag(new Tuple<>(queryId2, avroOpChainDag2));
    while(!(store.isStored(queryId1) && store.isStored(queryId2))) {
      // Wait until the plan is stored
    }
    Assert.assertTrue(new File(tmpFolderPath, queryId1 + ".plan").exists());
    Assert.assertTrue(new File(tmpFolderPath, queryId2 + ".plan").exists());

    // Test stored file
    final AvroOperatorChainDag loadedDag1 = store.load(queryId1);
    Assert.assertEquals(avroOpChainDag1.getEdges(), loadedDag1.getEdges());
    Assert.assertEquals(avroOpChainDag1.getSchema(), loadedDag1.getSchema());
    testVerticesEqual(avroOpChainDag1.getAvroVertices(), loadedDag1.getAvroVertices());

    final AvroOperatorChainDag loadedDag2 = store.load(queryId2);
    Assert.assertEquals(avroOpChainDag2.getEdges(), loadedDag2.getEdges());
    Assert.assertEquals(avroOpChainDag2.getSchema(), loadedDag2.getSchema());
    testVerticesEqual(avroOpChainDag2.getAvroVertices(), loadedDag2.getAvroVertices());

    // Test deletion
    store.delete(queryId1);
    store.delete(queryId2);
    Assert.assertFalse(store.isStored(queryId1));
    Assert.assertFalse(new File(tmpFolderPath, queryId1 + ".plan").exists());
    Assert.assertFalse(store.isStored(queryId2));
    Assert.assertFalse(new File(tmpFolderPath, queryId2 + ".plan").exists());
    for (final String path : paths) {
      Assert.assertFalse(new File(path).exists());
    }
    folder.delete();
  }

  /**
   * Tests that two lists of vertices are equal.
   * @param vertices the first list of vertices
   * @param loadedVertices the second list of vertices
   */
  private void testVerticesEqual(final List<AvroVertexChain> vertices, final List<AvroVertexChain> loadedVertices) {
    for (int i = 0; i < vertices.size(); i++) {
      final AvroVertexChain avroVertexChain = vertices.get(i);
      final AvroVertexChain loadedVertexChain = loadedVertices.get(i);
      for (int j = 0; j < avroVertexChain.getVertexChain().size(); j++) {
        final Vertex vertex = avroVertexChain.getVertexChain().get(j);
        final Vertex loadedVertex = loadedVertexChain.getVertexChain().get(j);
        Assert.assertEquals(vertex.getConfiguration(), loadedVertex.getConfiguration());
        Assert.assertEquals(vertex.getSchema(), loadedVertex.getSchema());
      }
    }
  }
}
