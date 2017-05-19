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
package edu.snu.mist.core.task.merging;

import edu.snu.mist.core.task.ConfigVertex;
import edu.snu.mist.core.task.ExecutionVertex;
import org.apache.reef.tang.annotations.DefaultImplementation;
import org.apache.reef.tang.exceptions.InjectionException;

import java.io.IOException;
import java.net.URL;

/**
 * This interface is for generating the execution vertex from configuration vertex.
 */
@DefaultImplementation(DefaultExecutionVertexGeneratorImpl.class)
public interface ExecutionVertexGenerator {

  /**
   * Generates the execution vertex from the configuration vertex.
   * @param configVertex configuration vertex
   * @param urls urls
   * @param classLoader class loader
   * @return execution vertex
   */
  ExecutionVertex generate(ConfigVertex configVertex,
                           URL[] urls,
                           ClassLoader classLoader) throws IOException, InjectionException;
}
