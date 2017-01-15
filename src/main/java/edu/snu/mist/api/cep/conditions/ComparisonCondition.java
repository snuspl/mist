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
package edu.snu.mist.api.cep.conditions;

/**
 * Operators used for comparison.
 */
public final class ComparisonCondition extends Condition {

  private final String fieldName;
  private final Object comparisonValue;

  /**
   * Creates a immutable comparison operator by given inputs.
   * @param conditionType
   * @param fieldName
   * @param comparisonValue
   */
  public ComparisonCondition(final ConditionType conditionType, final String fieldName, final Object comparisonValue) {
    super(conditionType);
    this.fieldName = fieldName;
    this.comparisonValue = comparisonValue;
  }

  /**
   * @return the target field name for comparison
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * @return the comparison value
   */
  public Object getComparisonValue() {
    return comparisonValue;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof ComparisonCondition)) {
      return false;
    }
    final ComparisonCondition cond = (ComparisonCondition) o;
    return this.conditionType.equals(cond.conditionType)
        && this.fieldName.equals(cond.fieldName)
        && this.comparisonValue.equals(cond.comparisonValue);
  }

  @Override
  public int hashCode() {
    return this.conditionType.hashCode() * 100 + this.fieldName.hashCode() * 10 + this.comparisonValue.hashCode();
  }
}