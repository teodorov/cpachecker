// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.invariants;

import java.util.concurrent.atomic.AtomicBoolean;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public abstract class AbstractInvariantGenerator implements InvariantGenerator {

  private AtomicBoolean started = new AtomicBoolean();

  @Override
  public final void start(CFANode pInitialLocation) {
    startImpl(pInitialLocation);
    started.set(true);
  }

  protected abstract void startImpl(CFANode pInitialLocation);

  @Override
  public abstract void cancel();

  @Override
  public AggregatedReachedSets get() throws CPAException, InterruptedException {
    return AggregatedReachedSets.empty();
  }

  @Override
  public abstract boolean isProgramSafe();

  @Override
  public boolean isStarted() {
    return started.get();
  }

}
