// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.concurrent.task.forward;

import static org.sosy_lab.cpachecker.core.AnalysisDirection.FORWARD;
import static org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus.NO_PROPERTY_CHECKED;
import static org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition.getDefaultPartition;
import static org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.mkNonAbstractionStateWithNewPathFormula;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;
import static org.sosy_lab.cpachecker.util.AbstractStates.filterLocation;
import static org.sosy_lab.cpachecker.util.AbstractStates.isTargetState;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.blockgraph.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.message.MessageFactory;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.Task;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.util.ConfigurationLoader;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.util.ErrorOrigin;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.util.ShareableBooleanFormula;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.composite.BlockAwareCompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.BlockAwareCompositeState;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverException;

public class ForwardAnalysis extends Task {
  private static volatile ConfigurationLoader configLoader = null;

  private final Block target;
  private final PathFormula newSummary;
  private final PathFormula oldSummary;
  private final int expectedVersion;
  private final ImmutableList<PathFormula> predecessorSummaries;
  private final ReachedSet reached;
  private final Algorithm algorithm;
  private final BlockAwareCompositeCPA cpa;
  private final Solver solver;
  private final FormulaManagerView fMgr;
  private final BooleanFormulaManagerView bfMgr;
  private final PathFormulaManager pfMgr;

  public ForwardAnalysis(
      final Block pTarget,
      @Nullable final ShareableBooleanFormula pOldSummary,
      @Nullable final ShareableBooleanFormula pNewSummary,
      final int pExpectedVersion,
      final Collection<ShareableBooleanFormula> pPredecessorSummaries,
      final ReachedSet pReachedSet,
      final Algorithm pAlgorithm,
      final BlockAwareCompositeCPA pCPA,
      final MessageFactory pMessageFactory,
      final LogManager pLogManager,
      final ShutdownNotifier pShutdownNotifier) {
    super(pMessageFactory, pLogManager, pShutdownNotifier);
    
    PredicateCPA predCPA = pCPA.retrieveWrappedCpa(PredicateCPA.class);
    assert predCPA != null;

    solver = predCPA.getSolver();
    fMgr = solver.getFormulaManager();
    bfMgr = fMgr.getBooleanFormulaManager();
    pfMgr = predCPA.getPathFormulaManager();

    target = pTarget;
    oldSummary = pOldSummary == null ? null : pOldSummary.getFor(fMgr, pfMgr);
    newSummary = pNewSummary == null ? null : pNewSummary.getFor(fMgr, pfMgr);
    expectedVersion = pExpectedVersion;
    predecessorSummaries =
        pPredecessorSummaries.stream()
            .map(formula -> formula.getFor(fMgr, pfMgr))
            .collect(ImmutableList.toImmutableList());

    reached = pReachedSet;
    algorithm = pAlgorithm;
    cpa = pCPA;
  }

  public static Configuration getConfiguration(
      final LogManager pLogManager,
      @Nullable final Path pConfigFile) throws InvalidConfigurationException {
    if (configLoader == null) {
      synchronized (ForwardAnalysis.class) {
        if (configLoader == null) {
          configLoader =
              new ConfigurationLoader(ForwardAnalysis.class, "predicateForward.properties",
                  pConfigFile, pLogManager);
        }
      }
    }
    return configLoader.getConfiguration();
  }

  @Override
  protected void execute()
      throws CPAException, InterruptedException, InvalidConfigurationException, SolverException {
    AlgorithmStatus status = NO_PROPERTY_CHECKED;

    if (isSummaryUnchanged()) {
      logManager.log(Level.INFO, "Summary unchanged, refined analysis aborted.");
      messageFactory.sendTaskCompletionMessage(this);
      return;
    }

    PathFormula cumPredSummary = buildCumulativePredecessorSummary();
    if (thereIsNoRelevantChange(cumPredSummary)) {
      logManager.log(Level.INFO, "No relevant change on summary, refined analysis aborted.");
      messageFactory.sendTaskCompletionMessage(this);
      return;
    }

    BlockAwareCompositeState entryState = buildEntryState(cumPredSummary);
    Precision precision = cpa.getInitialPrecision(target.getEntry(), getDefaultPartition());
    reached.add(entryState, precision);

    logManager.log(Level.FINE, "Starting ForwardAnalysis on ", target);
    do {
      shutdownNotifier.shutdownIfNecessary();
      status = status.update(algorithm.run(reached));
    } while(reached.hasWaitingState());
    
    handleTargetStates();
    propagateThroughExits();

    logManager.log(Level.FINE, "Completed ForwardAnalysis on ", target);
    messageFactory.sendTaskCompletionMessage(this);
  }

  private boolean thereIsNoRelevantChange(final PathFormula cumPredSummary)
      throws SolverException, InterruptedException {
    if (oldSummary == null || newSummary == null) {
      return false;
    }

    BooleanFormula addedContext
        = bfMgr.and(oldSummary.getFormula(), bfMgr.not(newSummary.getFormula()));
    BooleanFormula relevantChange = bfMgr.implication(cumPredSummary.getFormula(), addedContext);

    shutdownNotifier.shutdownIfNecessary();
    return solver.isUnsat(relevantChange);
  }

  private PathFormula buildCumulativePredecessorSummary() throws InterruptedException {
    PathFormula cumPredSummary = pfMgr.makeEmptyPathFormula();
    for (final PathFormula formula : predecessorSummaries) {
      cumPredSummary = pfMgr.makeOr(cumPredSummary, formula);
    }

    return cumPredSummary;
  }

  private PredicateAbstractState getRawPredicateEntryState() throws InterruptedException {
    AbstractState rawInitialState = null;
    while (rawInitialState == null) {
      try {
        rawInitialState = cpa.getInitialState(target.getEntry(), getDefaultPartition());
      } catch (InterruptedException ignored) {
        shutdownNotifier.shutdownIfNecessary();
      }
    }

    PredicateAbstractState rawPredicateState =
        extractStateByType(rawInitialState, PredicateAbstractState.class);
    assert rawPredicateState != null;

    return rawPredicateState;
  }

  private boolean isSummaryUnchanged() throws SolverException, InterruptedException {
    if (oldSummary == null || newSummary == null) {
      return false;
    }

    BooleanFormula newRaw = newSummary.getFormula();
    BooleanFormula oldRaw = oldSummary.getFormula();
    BooleanFormula equivalence = bfMgr.equivalence(newRaw, oldRaw);

    shutdownNotifier.shutdownIfNecessary();
    return !solver.isUnsat(equivalence);
  }

  private BlockAwareCompositeState buildEntryState(final PathFormula cumPredSummary)
      throws InterruptedException {
    PredicateAbstractState predicateEntryState = buildPredicateEntryState(cumPredSummary);

    List<AbstractState> componentStates = new ArrayList<>();
    for (ConfigurableProgramAnalysis componentCPA : cpa.getWrappedCPAs()) {
      AbstractState componentState = null;
      if (componentCPA instanceof PredicateCPA) {
        componentState = predicateEntryState;
      } else {
        while (componentState == null) {
          try {
            componentState = componentCPA.getInitialState(target.getEntry(), getDefaultPartition());
          } catch (InterruptedException ignored) {
            shutdownNotifier.shutdownIfNecessary();
          }
        }
      }
      componentStates.add(componentState);
    }

    return BlockAwareCompositeState.create(new CompositeState(componentStates), target, FORWARD);
  }

  private PredicateAbstractState buildPredicateEntryState(final PathFormula cumPredSummary)
      throws InterruptedException {
    PathFormula newContext = pfMgr.makeOr(cumPredSummary, newSummary);

    PredicateAbstractState rawPredicateState = getRawPredicateEntryState();

    return mkNonAbstractionStateWithNewPathFormula(
        newContext, rawPredicateState);
  }

  private void handleTargetStates()
      throws InterruptedException, CPAException, InvalidConfigurationException {
    for (final AbstractState state : reached.asCollection()) {
      CFANode location = extractLocation(state);
      assert location != null;

      if (isTargetState(state) && targetIsError(state)) {
        logManager.log(Level.FINE, "Target State:", state);
        
        ErrorOrigin origin = ErrorOrigin.create(state, reached.getPrecision(state));
        messageFactory.sendBackwardAnalysisRequest(target, location, origin);
      }
    }
  }

  private boolean targetIsError(final AbstractState state) {
    assert state instanceof CompositeState;
    CompositeState compositeState = (CompositeState) state;

    boolean foundError = false;
    for (final AbstractState componentState : compositeState.getWrappedStates()) {
      if (componentState instanceof Targetable) {
        Targetable targetableState = (Targetable) componentState;
        if (targetableState.isTarget() && targetableState instanceof AutomatonState) {
          foundError = true;
        }
      }
    }

    return foundError;
  }

  private void propagateThroughExits()
      throws InterruptedException, CPAException, InvalidConfigurationException {
    for (final CFANode exit : target.getExits().keySet()) {
      PathFormula exitFormula = pfMgr.makeEmptyPathFormula();

      for (final AbstractState exitState : filterLocation(reached, exit)) {
        final PredicateAbstractState predState =
            extractStateByType(exitState, PredicateAbstractState.class);
        assert predState != null;

        exitFormula = pfMgr.makeOr(exitFormula, predState.getPathFormula());
      }

      Block successor = target.getExits().get(exit);
      final ShareableBooleanFormula shareableFormula =
          new ShareableBooleanFormula(fMgr, exitFormula);

      messageFactory.sendForwardAnalysisRequest(target, expectedVersion, successor,
          shareableFormula);
    }
  }
  
  public String toString() {
    return "ForwardAnalysis on block with entry location " + target.getEntry();
  }
}
