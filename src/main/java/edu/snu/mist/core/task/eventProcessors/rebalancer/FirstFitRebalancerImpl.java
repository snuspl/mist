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
package edu.snu.mist.core.task.eventProcessors.rebalancer;

import edu.snu.mist.core.task.eventProcessors.EventProcessor;
import edu.snu.mist.core.task.eventProcessors.GroupAllocationTable;
import edu.snu.mist.core.task.globalsched.GlobalSchedGroupInfo;
import org.apache.reef.io.Tuple;

import javax.inject.Inject;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the first-fit group balancer.
 */
public final class FirstFitRebalancerImpl implements GroupRebalancer {
  private static final Logger LOG = Logger.getLogger(FirstFitRebalancerImpl.class.getName());

  private final GroupAllocationTable groupAllocationTable;

  @Inject
  private FirstFitRebalancerImpl(final GroupAllocationTable groupAllocationTable) {
    this.groupAllocationTable = groupAllocationTable;
  }

  /**
   * This is for logging.
   * @param loadTable loadTable
   */
  private String printMap(final Map<EventProcessor, Double> loadTable) {
    final StringBuilder sb = new StringBuilder();
    for (final Map.Entry<EventProcessor, Double> entry : loadTable.entrySet()) {
      sb.append(entry.getKey());
      sb.append(" -> ");
      sb.append(entry.getValue());
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Calculate the load of groups.
   * @param groups groups
   * @return total load
   */
  private double calculateLoadOfGroupsForLogging(final Collection<GlobalSchedGroupInfo> groups) {
    double sum = 0;
    for (final GlobalSchedGroupInfo group : groups) {
      final double fixedLoad = group.getFixedLoad();
      sum += fixedLoad;
    }
    return sum;
  }

  @Override
  public void triggerRebalancing() {
    final List<EventProcessor> eventProcessors = groupAllocationTable.getKeys();
    // Calculate each load and total load
    final Map<EventProcessor, Double> loadTable = new HashMap<>();
    double totalLoad = 0.0;
    for (final EventProcessor eventProcessor : eventProcessors) {
      final double load = calculateLoadOfGroups(groupAllocationTable.getValue(eventProcessor));
      totalLoad += load;
      loadTable.put(eventProcessor, load);
    }

    // Desirable load
    // Each event processor should has the load evenly
    final double desirableLoad = totalLoad * (1.0 / eventProcessors.size());

    // Make bins and items.
    final Tuple<Map<EventProcessor, Double>, List<GlobalSchedGroupInfo>> binsAndItems =
        makeBinsAndItems(desirableLoad, loadTable, eventProcessors);

    // First-fit heuristic algorithm
    final Map<GlobalSchedGroupInfo, EventProcessor> mapping = firstFitHeuristic(
        eventProcessors, binsAndItems.getKey(), binsAndItems.getValue());

    // Reassign the groups to the event processor
    for (final EventProcessor eventProcessor : eventProcessors) {
      final Iterator<GlobalSchedGroupInfo> iterator = groupAllocationTable.getValue(eventProcessor).iterator();
      while (iterator.hasNext()) {
        final GlobalSchedGroupInfo group = iterator.next();
        final EventProcessor destEP = mapping.remove(group);
        if (destEP != null) {
          iterator.remove();
          final Collection<GlobalSchedGroupInfo> dest = groupAllocationTable.getValue(destEP);
          dest.add(group);
        }
      }
    }

    if (LOG.isLoggable(Level.FINE)) {
      final Map<EventProcessor, Double> afterRebalancingLoadTable = new HashMap<>();
      for (final EventProcessor ep : groupAllocationTable.getKeys()) {
        afterRebalancingLoadTable.put(ep, calculateLoadOfGroupsForLogging(groupAllocationTable.getValue(ep)));
      }

      LOG.log(Level.FINE, "Rebalanacing Groups (Desirable load={0}) \n"
          + "=========== Before LB ==========\n {1} \n"
          + "=========== After  LB ==========\n {2} \n",
          new Object[] {desirableLoad, printMap(loadTable), printMap(afterRebalancingLoadTable)});
    }
  }

  private Map<GlobalSchedGroupInfo, EventProcessor> firstFitHeuristic(
      final List<EventProcessor> eventProcessors,
      final Map<EventProcessor, Double> bins,
      final List<GlobalSchedGroupInfo> items) {
    final Map<GlobalSchedGroupInfo, EventProcessor> mapping = new HashMap<>(items.size());

    final Iterator<GlobalSchedGroupInfo> iterator = items.iterator();
    while (iterator.hasNext()) {
      final GlobalSchedGroupInfo item = iterator.next();
      // find the first bin that can hold the item
      for (final EventProcessor eventProcessor : eventProcessors) {
        final double size = bins.get(eventProcessor);
        final double itemSize = item.getFixedLoad();
        if (size >= itemSize) {
          // This is the first bin that can hold the item!
          iterator.remove();
          mapping.put(item, eventProcessor);
          bins.put(eventProcessor, size - itemSize);
          break;
        }
      }
    }

    if (!items.isEmpty()) {
      // Second
      final Iterator<GlobalSchedGroupInfo> secondIter = items.iterator();
      while (secondIter.hasNext()) {
        final GlobalSchedGroupInfo item = secondIter.next();
        // find the first bin that can hold the item
        for (final EventProcessor eventProcessor : eventProcessors) {
          final double size = bins.get(eventProcessor);
          final double itemSize = item.getFixedLoad();
          if (size > 0) {
            // This is the first bin that can hold the item!
            secondIter.remove();
            mapping.put(item, eventProcessor);
            bins.put(eventProcessor, size - itemSize);
            break;
          }
        }
      }
    }

    if (!items.isEmpty()) {
      throw new RuntimeException("First-fit algorithm is incorrect");
    }

    return mapping;
  }

  /**
   * Makes bins (event processors with the available size) and items (groups).
   * @param desirableLoad desirable load (size)
   * @param loadTable load table
   * @param eventProcessors event processors
   * @return bins and items
   */
  private Tuple<Map<EventProcessor, Double>, List<GlobalSchedGroupInfo>> makeBinsAndItems(
      final double desirableLoad,
      final Map<EventProcessor, Double> loadTable,
      final List<EventProcessor> eventProcessors) {
    // Make bins
    final Map<EventProcessor, Double> bins = new HashMap<>(groupAllocationTable.size());

    // Make items
    final List<GlobalSchedGroupInfo> items = new LinkedList<>();
    for (final EventProcessor eventProcessor : eventProcessors) {
      double load = loadTable.get(eventProcessor);
      if (load > desirableLoad) {
        // Add groups until the load is less than the desirable load
        final Iterator<GlobalSchedGroupInfo> iterator = groupAllocationTable.getValue(eventProcessor).iterator();
        while (load > desirableLoad && iterator.hasNext()) {
          final GlobalSchedGroupInfo group = iterator.next();
          items.add(group);
          load -= group.getFixedLoad();
        }
      }

      // Put the bin with the size
      bins.put(eventProcessor, desirableLoad - load);
    }

    return new Tuple<>(bins, items);
  }

  /**
   * Calculate the load of groups.
   * @param groups groups
   * @return total load
   */
  private double calculateLoadOfGroups(final Collection<GlobalSchedGroupInfo> groups) {
    double sum = 0;
    for (final GlobalSchedGroupInfo group : groups) {
      final double fixedLoad = group.getEWMALoad();
      group.setFixedLoad(fixedLoad);
      sum += fixedLoad;
    }
    return sum;
  }
}
