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
package edu.snu.mist.client.datastreams.configurations;

import edu.snu.mist.common.operators.Operator;
import edu.snu.mist.common.parameters.CepEventPatterns;
import edu.snu.mist.common.parameters.WindowTime;
import org.apache.reef.tang.formats.ConfigurationModule;
import org.apache.reef.tang.formats.ConfigurationModuleBuilder;
import org.apache.reef.tang.formats.RequiredImpl;
import org.apache.reef.tang.formats.RequiredParameter;

/**
 * A configuration for cep operator that binds a list of serialized cep events and window time.
 */
public final class CepOperatorConfiguration extends ConfigurationModuleBuilder {

  /**
   * Required event list.
   */
  public static final RequiredParameter<String> CEP_EVENTS = new RequiredParameter<>();

  /**
   * Required window time.
   */
  public static final RequiredParameter<Long> WINDOW_TIME = new RequiredParameter<>();

  /**
   * Required operator class.
   */
  public static final RequiredImpl<Operator> OPERATOR = new RequiredImpl<>();

  /**
   * A configuration for binding the class of the user-defined function.
   */
  public static final ConfigurationModule CONF = new CepOperatorConfiguration()
      .bindNamedParameter(CepEventPatterns.class, CEP_EVENTS)
      .bindNamedParameter(WindowTime.class, WINDOW_TIME)
      .bindImplementation(Operator.class, OPERATOR)
      .build();
}
