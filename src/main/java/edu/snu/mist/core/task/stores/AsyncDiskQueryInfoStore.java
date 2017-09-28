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

import edu.snu.mist.core.parameters.TempFolderPath;
import edu.snu.mist.core.task.HashUtils;
import edu.snu.mist.formats.avro.AvroOperatorChainDag;
import io.netty.util.internal.ConcurrentSet;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.reef.io.Tuple;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * It saves the information of a query (operator chain dag, jar files) into the disk.
 * The store request is done by separated thread asynchronously.
 */
final class AsyncDiskQueryInfoStore implements QueryInfoStore {

  private static final Logger LOG = Logger.getLogger(QueryInfoStore.class.getName());
  /**
   * A path for the temporary folder that stores jar files and operator chain dags of queries.
   */
  private final String tmpFolderPath;

  /**
   * A writer that stores the dag.
   */
  private final DatumWriter<AvroOperatorChainDag> datumWriter;

  /**
   * A reader that reads stored the dag.
   */
  private final DatumReader<AvroOperatorChainDag> datumReader;

  /**
   * A file name generator that generates jar file's names.
   */
  private final FileNameGenerator fileNameGenerator;

  /**
   * A executor service that contains a thread doing plan store.
   */
  private final ExecutorService planStoreExecutorService;

  /**
   * A blocking queue that contains the plans to be stored.
   */
  private final BlockingQueue<Tuple<String, AvroOperatorChainDag>> planQueue;

  /**
   * A set that contains the query ids that were stored properly.
   */
  private final Set<String> storedQuerySet;

  /**
   * A map that contains the SHA-256 hash value (which is a byte array) converted to a ByteBuffer as the key,
   * and a set of tuples that correspond to that SHA-256 hash value.
   * A tuple is consisted of the ByteBuffer(of a jar file) and location(as a string) as a value for submitted jar files.
   * Multiple tuples may correspond to a single SHA-256 hash value, in case of hash collisions.
   * The key is a ByteBuffer because a byte array cannot be used as a key without a separate wrapper.
   */
  private final Map<ByteBuffer, Set<Tuple<ByteBuffer, String>>> hashInfoMap;

  @Inject
  private AsyncDiskQueryInfoStore(@Parameter(TempFolderPath.class) final String tmpFolderPath,
                                  final FileNameGenerator fileNameGenerator) {
    this.tmpFolderPath = tmpFolderPath;
    this.fileNameGenerator = fileNameGenerator;
    this.datumWriter = new SpecificDatumWriter<>(AvroOperatorChainDag.class);
    this.datumReader = new SpecificDatumReader<>(AvroOperatorChainDag.class);
    this.hashInfoMap = new ConcurrentHashMap<>();
    // Create a folder that stores the dags and jar files
    final File folder = new File(tmpFolderPath);
    if (!folder.exists()) {
      folder.mkdir();
    } else {
      final File[] destroy = folder.listFiles();
      for (final File des : destroy) {
        des.delete();
      }
    }

    this.planQueue = new LinkedBlockingQueue<>();
    this.planStoreExecutorService = Executors.newSingleThreadExecutor();
    this.storedQuerySet = new ConcurrentSet<>();

    planStoreExecutorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          while (true) {
            final Tuple<String, AvroOperatorChainDag> tuple = planQueue.take();
            final String queryId = tuple.getKey();
            final AvroOperatorChainDag dag = tuple.getValue();
            final File storedFile = getAvroOperatorChainDagFile(queryId);
            if (!storedFile.exists()) {
              final DataFileWriter<AvroOperatorChainDag> dataFileWriter = new DataFileWriter<>(datumWriter);
              dataFileWriter.create(dag.getSchema(), storedFile);
              dataFileWriter.append(dag);
              dataFileWriter.close();
              storedQuerySet.add(queryId);
            }
          }
        } catch (final Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Gets a file of the stored the dag corresponding to the queryId.
   * @param queryId query id
   * @return file of the stored dag
   */
  private File getAvroOperatorChainDagFile(final String queryId) {
    final StringBuilder sb = new StringBuilder(queryId);
    sb.append(".plan");
    return new File(tmpFolderPath, sb.toString());
  }

  /**
   * Saves the dag as queryId.plan to disk asynchronously.
   * @param tuple the tuple to save
   */
  @Override
  public void saveAvroOpChainDag(final Tuple<String, AvroOperatorChainDag> tuple) {
    try {
      planQueue.put(tuple);
    } catch (final InterruptedException ie) {
      ie.printStackTrace();
    }
  }

  /**
   * Check whether the query is stored properly or not.
   * @param queryId the query id to check
   * @return true if saving is success. Otherwise return false
   */
  @Override
  public boolean isStored(final String queryId) {
    return storedQuerySet.contains(queryId);
  }

  /**
   * Check for hash collisions within the jarInfoSet and add the path(s) if collisions exist.
   * @param jarFileBytes
   * @param jarInfoSet
   * @param paths
   * @return true if there was a hash collision.
   */
  private boolean checkForHashCollision(final ByteBuffer jarFileBytes,
                                        final Set<Tuple<ByteBuffer, String>> jarInfoSet,
                                        final List<String> paths) {
    // The following boolean is used to see if a hash collision has occurred.
    boolean hashCollisionOccurred = true;
    // Check if any of the actual ByteBuffers are also the same.
    for (final Tuple<ByteBuffer, String> jarInfo : jarInfoSet) {
      if (Arrays.equals(jarFileBytes.array(), jarInfo.getKey().array())) {
        LOG.log(Level.INFO, "The jar file submitted was already previously submitted.");
        final String path = jarInfo.getValue();
        paths.add(path);
        hashCollisionOccurred = false;
        break;
      }
    }
    return hashCollisionOccurred;
  }

  /**
   * Create a JarFile with the given bytes and path, and also add it to the paths.
   * @param jarFileBytes
   * @param jarFilePath
   * @param paths
   * @throws IOException
   */
  private void createJarFile(final ByteBuffer jarFileBytes,
                             final Path jarFilePath,
                             final List<String> paths) throws IOException {
    final File jarFile = jarFilePath.toFile();
    final FileChannel wChannel = new FileOutputStream(jarFile, false).getChannel();
    wChannel.write(jarFileBytes);
    wChannel.close();
    paths.add(jarFile.getAbsolutePath());
  }

  /**
   * Saves the serialized jar files into the disk.
   * This generates file names for the jar files from fileNameGenerator.
   * @param jarFiles jar files
   * @return paths of the stored jar files
   * @throws IOException throws an exception when the jar file is not able to be saved.
   */
  @Override
  public List<String> saveJar(final List<ByteBuffer> jarFiles) throws IOException {
    final List<String> paths = new LinkedList<>();
    for (final ByteBuffer jarFileBytes : jarFiles) {
      final byte[] byteBufferHash = HashUtils.getByteBufferHash(jarFileBytes);
      // Check if the jarFiles already exist.
      final ByteBuffer wrappedHash = ByteBuffer.wrap(byteBufferHash);
      Set<Tuple<ByteBuffer, String>> jarInfoSet = hashInfoMap.get(wrappedHash);
      if (jarInfoSet != null) {
        synchronized (jarInfoSet) {
          // This is the case when a jar file with the same hash was submitted previously.
          if (checkForHashCollision(jarFileBytes, jarInfoSet, paths)) {
            // This is when there was a SHA-256 hash collision between two different files, so a new jar is created.
            final String path = String.format("submitted-%s.jar", fileNameGenerator.generate());
            LOG.log(Level.INFO, "New jar " + path + " was submitted with a SHA-256 hash collision.");
            final Path jarFilePath = Paths.get(tmpFolderPath, path);
            jarInfoSet.add(new Tuple<>(jarFileBytes, jarFilePath.toString()));
            createJarFile(jarFileBytes, jarFilePath, paths);
          }
        }
      } else {
        // If the hash value is new to the MistTask, create the new jar.
        final String path = String.format("submitted-%s.jar", fileNameGenerator.generate());
        final Path jarFilePath = Paths.get(tmpFolderPath, path);
        final Set<Tuple<ByteBuffer, String>> newPathsSet = new HashSet<>();
        final Tuple<ByteBuffer, String> newJarTuple = new Tuple<>(jarFileBytes, jarFilePath.toString());
        newPathsSet.add(newJarTuple);
        if (hashInfoMap.putIfAbsent(wrappedHash, newPathsSet) == null) {
          LOG.log(Level.INFO, "New jar " + path + " was submitted.");
          createJarFile(jarFileBytes, jarFilePath, paths);
        } else {
          // Two jar files with the same hash were submitted concurrently.
          jarInfoSet = hashInfoMap.get(wrappedHash);
          synchronized (jarInfoSet) {
            if (checkForHashCollision(jarFileBytes, jarInfoSet, paths)) {
              LOG.log(Level.INFO, "New jar " + path + " was submitted with a SHA-256 hash collision.");
              jarInfoSet.add(newJarTuple);
              createJarFile(jarFileBytes, jarFilePath, paths);
            }
          }
        }
      }
    }
    return paths;
  }

  /**
   * Load the stored dag from File.
   * @param storedPlanFile file
   * @return chained dag
   * @throws IOException
   */
  private AvroOperatorChainDag loadFromFile(final File storedPlanFile) throws IOException {
    final DataFileReader<AvroOperatorChainDag> dataFileReader =
        new DataFileReader<AvroOperatorChainDag>(storedPlanFile, datumReader);
    AvroOperatorChainDag dag = null;
    dag = dataFileReader.next(dag);
    return dag;
  }

  /**
   * Loads the dag, queryId.plan, from disk.
   * @param queryId
   * @return the dag corresponding to queryId
   * @throws IOException
   */
  @Override
  public AvroOperatorChainDag load(final String queryId) throws IOException {
    final File storedFile = getAvroOperatorChainDagFile(queryId);
    return loadFromFile(storedFile);
  }

  /**
   * Deletes the dag and jar files.
   * @param queryId
   * @throws IOException
   */
  @Override
  public void delete(final String queryId) throws IOException {
    storedQuerySet.remove(queryId);
    final File storedFile = getAvroOperatorChainDagFile(queryId);
    final AvroOperatorChainDag logicalPlan = loadFromFile(storedFile);
    final List<String> paths = logicalPlan.getJarFilePaths();

    // Delete jar files for the query
    for (final String path : paths) {
      if (path.startsWith(tmpFolderPath)) {
        // Delete the jar file if it is in the temp folder
        final File jarFile = new File(path);
        if (jarFile.exists()) {
          jarFile.delete();
        }
      }
    }

    // Delete the logical plan
    if (storedFile.exists()) {
      storedFile.delete();
    }
  }

  @Override
  public void close() {
    planStoreExecutorService.shutdown();
  }
}
