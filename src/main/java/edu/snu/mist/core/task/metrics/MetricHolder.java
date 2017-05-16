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
package edu.snu.mist.core.task.metrics;

import edu.snu.mist.core.task.metrics.parameters.*;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;

/**
 * A class represents a metric holder.
 * If this holder is placed in a group info, the metrics in this holder will represent the status of the group.
 * Else if this holder is placed in a query manager, the metrics in this holder will represent the global status.
 */
public final class MetricHolder {

  /**
   * The number of events metric with EWMA.
   */
  private final EWMAMetric numEventsMetric;

  /**
   * The system CPU utilization metric with EWMA.
   */
  private final EWMAMetric cpuSysUtilMetric;

  /**
   * The process CPU utilization metric with EWMA.
   */
  private final EWMAMetric cpuProcUtilMetric;

  /**
   * The heap memory usage metric with EWMA.
   */
  private final EWMAMetric heapMemUsageMetric;

  /**
   * The non heap memory usage metric with EWMA.
   */
  private final EWMAMetric nonHeapMemUsageMetric;

  /**
   * The weight metric.
   */
  private final NormalMetric<Double> weightMetric;

  /**
   * The number of groups metric.
   */
  private final NormalMetric<Integer> numGroupsMetric;

  @Inject
  private MetricHolder(@Parameter(NumEventAlpha.class) final double numEventAlpha,
                       @Parameter(SysCpuUtilAlpha.class) final double sysCpuUtilAlpha,
                       @Parameter(ProcCpuUtilAlpha.class) final double procCpuUtilAlpha,
                       @Parameter(HeapMemoryUsageAlpha.class) final double heapMemUsageAlpha,
                       @Parameter(NonHeapMemoryUsageAlpha.class) final double nonHeapMemUsageAlpha) {
    this.numEventsMetric = new EWMAMetric(0.0, numEventAlpha);
    this.cpuSysUtilMetric = new EWMAMetric(0.0, sysCpuUtilAlpha);
    this.cpuProcUtilMetric = new EWMAMetric(0.0, procCpuUtilAlpha);
    this.heapMemUsageMetric = new EWMAMetric(0.0, heapMemUsageAlpha);
    this.nonHeapMemUsageMetric = new EWMAMetric(0.0, nonHeapMemUsageAlpha);
    this.weightMetric = new NormalMetric<>(1.0);
    this.numGroupsMetric = new NormalMetric<>(0);
  }

  /**
   * @return the number of events metric
   */
  public EWMAMetric getNumEventsMetric() throws RuntimeException {
    return numEventsMetric;
  }

  /**
   * @return the CPU system utilization metric
   */
  public EWMAMetric getCpuSysUtilMetric() throws RuntimeException {
    return cpuSysUtilMetric;
  }

  /**
   * @return the CPU process utilization metric
   */
  public EWMAMetric getCpuProcUtilMetric() throws RuntimeException {
    return cpuProcUtilMetric;
  }

  /**
   * @return the heap memory usage metric
   */
  public EWMAMetric getHeapMemUsageMetric() throws RuntimeException {
    return heapMemUsageMetric;
  }

  /**
   * @return the non heap memory usage metric
   */
  public EWMAMetric getNonHeapMemUsageMetric() throws RuntimeException {
    return nonHeapMemUsageMetric;
  }

  /**
   * @return the weight metric
   */
  public NormalMetric<Double> getWeightMetric() throws RuntimeException {
    return weightMetric;
  }

  /**
   * @return the number of groups metric
   */
  public NormalMetric<Integer> getNumGroupsMetric() throws RuntimeException {
    return numGroupsMetric;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MetricHolder that = (MetricHolder) o;

    if (!getNumEventsMetric().equals(that.getNumEventsMetric())) {
      return false;
    }
    if (!getCpuSysUtilMetric().equals(that.getCpuSysUtilMetric())) {
      return false;
    }
    if (!getCpuProcUtilMetric().equals(that.getCpuProcUtilMetric())) {
      return false;
    }
    if (!getHeapMemUsageMetric().equals(that.getHeapMemUsageMetric())) {
      return false;
    }
    if (!getNonHeapMemUsageMetric().equals(that.getNonHeapMemUsageMetric())) {
      return false;
    }
    if (!getWeightMetric().equals(that.getWeightMetric())) {
      return false;
    }
    return getNumGroupsMetric().equals(that.getNumGroupsMetric());
  }

  @Override
  public int hashCode() {
    int result = getNumEventsMetric().hashCode();
    result = 31 * result + getCpuSysUtilMetric().hashCode();
    result = 31 * result + getCpuProcUtilMetric().hashCode();
    result = 31 * result + getHeapMemUsageMetric().hashCode();
    result = 31 * result + getNonHeapMemUsageMetric().hashCode();
    result = 31 * result + getWeightMetric().hashCode();
    result = 31 * result + getNumGroupsMetric().hashCode();
    return result;
  }
}