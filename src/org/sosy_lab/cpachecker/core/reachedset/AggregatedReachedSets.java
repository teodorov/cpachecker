/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.core.reachedset;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class AggregatedReachedSets {
  protected Set<UnmodifiableReachedSet> reachedSets;

  public AggregatedReachedSets() {
    reachedSets = Collections.emptySet();
  }

  public AggregatedReachedSets(Set<UnmodifiableReachedSet> pReachedSets) {
    reachedSets = new HashSet<>(pReachedSets);
  }

  public Set<UnmodifiableReachedSet> snapShot() {
    return reachedSets;
  }

  private static class AggregatedThreadedReachedSets extends AggregatedReachedSets {
    private final ReentrantReadWriteLock lock;
    private final List<AggregatedThreadedReachedSets> otherAggregators = new ArrayList<>();

    private AggregatedThreadedReachedSets(final ReentrantReadWriteLock pLock) {
      lock = pLock;
    }

    @Override
    public Set<UnmodifiableReachedSet> snapShot() {
      lock.readLock().lock();
      try {
        return Sets.union(
            reachedSets,
            otherAggregators
                .stream()
                .flatMap(s -> s.snapShot().stream())
                .collect(Collectors.toSet()));
      } finally {
        lock.readLock().unlock();
      }
    }

    public void concat(AggregatedThreadedReachedSets other) {
      otherAggregators.add(other);
    }
  }

  public static class AggregatedReachedSetManager {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private AggregatedThreadedReachedSets reachedView = new AggregatedThreadedReachedSets(lock);
    private Set<UnmodifiableReachedSet> reachedSets = new HashSet<>();

    public void addReachedSet(UnmodifiableReachedSet reached) {
      synchronized (reachedSets) {
        reachedSets.add(reached);
      }
    }

    public void updateReachedSet(
        UnmodifiableReachedSet oldReached, UnmodifiableReachedSet newReached) {
      synchronized (reachedSets) {
        reachedSets.remove(oldReached);
        reachedSets.add(newReached);
      }

      lock.writeLock().lock();
      try {
        reachedView.reachedSets = Collections.unmodifiableSet(new HashSet<>(reachedSets));
      } finally {
        lock.writeLock().unlock();
      }
    }

    public AggregatedReachedSets asView() {
      return reachedView;
    }

    public synchronized void addAggregated(AggregatedReachedSets pAggregatedReachedSets) {
      lock.writeLock().lock();

      try {
        if (pAggregatedReachedSets instanceof AggregatedThreadedReachedSets) {
          reachedView.concat((AggregatedThreadedReachedSets) pAggregatedReachedSets);

        } else {
          HashSet<UnmodifiableReachedSet> sets = new HashSet<>(reachedSets);
          sets.addAll(pAggregatedReachedSets.reachedSets);
          reachedView.reachedSets = Collections.unmodifiableSet(sets);
        }

      } finally {
        lock.writeLock().unlock();
      }
    }
  }
}
