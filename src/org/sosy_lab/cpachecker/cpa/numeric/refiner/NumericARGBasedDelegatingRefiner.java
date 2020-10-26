// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.numeric.refiner;

import com.google.common.collect.Multimap;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.configuration.TimeSpanOption;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.numeric.NumericCPA;
import org.sosy_lab.cpachecker.cpa.numeric.NumericState;
import org.sosy_lab.cpachecker.cpa.octagon.refiner.OctagonAnalysisFeasibilityChecker;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPARefiner;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.refiner.ValueAnalysisPathInterpolator;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.refinement.FeasibilityChecker;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;
import org.sosy_lab.cpachecker.util.resources.WalltimeLimit;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.numericdomains.Manager;

/**
 * Refiner implementation that delegates to {@link ValueAnalysisPathInterpolator}, and if this
 * fails, optionally delegates also to {@link PredicateCPARefiner}.
 */
@Options(prefix = "cpa.numeric.refiner")
class NumericARGBasedDelegatingRefiner implements ARGBasedRefiner, Statistics, StatisticsProvider {

  /** refiner used for value-analysis interpolation refinement */
  private final ValueAnalysisPathInterpolator interpolatingRefiner;

  private final FeasibilityChecker<ValueAnalysisState> valueAnalysisChecker;

  /** the hash code of the previous error path */
  private int previousErrorPathID = -1;

  @Option(
      secure = true,
      description =
          "Timelimit for the backup feasibility check with the numeric analysis."
              + "(use seconds or specify a unit; 0 for infinite)")
  @TimeSpanOption(codeUnit = TimeUnit.NANOSECONDS, defaultUserUnit = TimeUnit.SECONDS, min = 0)
  private TimeSpan timeForNumericFeasibilityCheck = TimeSpan.ofNanos(0);

  // statistics
  private int numberOfValueAnalysisRefinements = 0;
  private int numberOfSuccessfulValueAnalysisRefinements = 0;

  /**
   * if this variable is toggled, only octagon refinements will be done as value analysis
   * refinements will make no sense any more because they are too imprecise
   */
  private boolean existsExplicitNumericRefinement = false;

  private final CFA cfa;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final Configuration config;

  private final Manager domainManager;
  private final TransferRelation transferRelation;

  NumericARGBasedDelegatingRefiner(
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier,
      final CFA pCfa,
      final Manager pDomainManager,
      final TransferRelation pTransferRelation,
      final FeasibilityChecker<ValueAnalysisState> pValueAnalysisFeasibilityChecker,
      final ValueAnalysisPathInterpolator pValueAnalysisPathInterpolator)
      throws InvalidConfigurationException {

    pConfig.inject(this);
    config = pConfig;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    cfa = pCfa;

    domainManager = pDomainManager;
    transferRelation = pTransferRelation;
    valueAnalysisChecker = pValueAnalysisFeasibilityChecker;
    interpolatingRefiner = pValueAnalysisPathInterpolator;
  }

  @Override
  public CounterexampleInfo performRefinementForPath(
      final ARGReachedSet reached, final ARGPath pErrorPath)
      throws CPAException, InterruptedException {

    // if path is infeasible, try to refine the precision
    if (!isPathFeasible(pErrorPath) && !existsExplicitNumericRefinement) {
      if (performValueAnalysisRefinement(reached, pErrorPath)) {
        return CounterexampleInfo.spurious();
      }
    }

    // if the path is infeasible, try to refine the precision, this time
    // only with numeric states, this is more precise than only using the value analysis
    // refinement
    OctagonAnalysisFeasibilityChecker numericChecker;
    numericChecker = createNumericFeasibilityChecker(pErrorPath);
    if (!numericChecker.isFeasible()) {
      if (performNumericAnalysisRefinement(reached, numericChecker)) {
        existsExplicitNumericRefinement = true;
        return CounterexampleInfo.spurious();
      }
    }

    // we use the imprecise version of the CounterexampleInfo, due to the possible
    // merges which are done in the NumericCPA
    return CounterexampleInfo.feasibleImprecise(pErrorPath);
  }

  /**
   * This method performs an value-analysis refinement.
   *
   * @param reached the current reached set
   * @param errorPath the current error path
   * @return true, if the value-analysis refinement was successful, else false
   * @throws CPAException when value-analysis interpolation fails
   */
  private boolean performValueAnalysisRefinement(
      final ARGReachedSet reached, final ARGPath errorPath)
      throws CPAException, InterruptedException {
    numberOfValueAnalysisRefinements++;

    UnmodifiableReachedSet reachedSet = reached.asReachedSet();
    Precision precision = reachedSet.getPrecision(reachedSet.getLastState());
    VariableTrackingPrecision numericPrecision =
        (VariableTrackingPrecision)
            Precisions.asIterable(precision)
                .filter(VariableTrackingPrecision.isMatchingCPAClass(NumericCPA.class))
                .get(0);

    VariableTrackingPrecision refinedNumericPrecision;
    Pair<ARGState, CFAEdge> refinementRoot;

    Multimap<CFANode, MemoryLocation> increment =
        interpolatingRefiner.determinePrecisionIncrement(errorPath);
    refinementRoot = interpolatingRefiner.determineRefinementRoot(errorPath, increment);

    // no increment - value-analysis refinement was not successful
    if (increment.isEmpty()) {
      return false;
    }

    refinedNumericPrecision = numericPrecision.withIncrement(increment);

    if (valueAnalysisRefinementWasSuccessful(
        errorPath, numericPrecision, refinedNumericPrecision)) {
      numberOfSuccessfulValueAnalysisRefinements++;
      reached.removeSubtree(
          refinementRoot.getFirst(),
          refinedNumericPrecision,
          VariableTrackingPrecision.isMatchingCPAClass(NumericCPA.class));
      return true;

    } else {
      return false;
    }
  }

  private boolean performNumericAnalysisRefinement(
      final ARGReachedSet reached, final OctagonAnalysisFeasibilityChecker checker)
      throws InterruptedException {
    UnmodifiableReachedSet reachedSet = reached.asReachedSet();
    Precision precision = reachedSet.getPrecision(reachedSet.getLastState());
    VariableTrackingPrecision numericPrecision =
        (VariableTrackingPrecision)
            Precisions.asIterable(precision)
                .filter(VariableTrackingPrecision.isMatchingCPAClass(NumericCPA.class))
                .get(0);

    Multimap<CFANode, MemoryLocation> increment = checker.getPrecisionIncrement();
    // no newly tracked variables, so the refinement was not successful
    if (increment.isEmpty()) {
    //  return false;
    }

    reached.removeSubtree(
        ((ARGState) reachedSet.getFirstState()).getChildren().iterator().next(),
        numericPrecision.withIncrement(increment),
        VariableTrackingPrecision.isMatchingCPAClass(NumericCPA.class));

    logger.log(
        Level.INFO,
        "Refinement successful, precision incremented, following variables are now tracked additionally:\n"
            + new TreeSet<>(increment.values()));

    return true;
  }

  /**
   * This helper method checks if the refinement was successful, i.e., that either the
   * counterexample is not a repeated counterexample, or that the precision did grow.
   *
   * <p>Repeated counterexamples might occur when combining the analysis with thresholding, or when
   * ignoring variable classes, i.e. when combined with BDD analysis (i.e.
   * cpa.value.precision.ignoreBoolean).
   *
   * @param errorPath the current error path
   * @param valueAnalysisPrecision the previous precision
   * @param refinedValueAnalysisPrecision the refined precision
   */
  private boolean valueAnalysisRefinementWasSuccessful(
      ARGPath errorPath,
      VariableTrackingPrecision valueAnalysisPrecision,
      VariableTrackingPrecision refinedValueAnalysisPrecision) {
    // new error path or precision refined -> success
    boolean success =
        (errorPath.toString().hashCode() != previousErrorPathID)
            || (refinedValueAnalysisPrecision.getSize() > valueAnalysisPrecision.getSize());

    previousErrorPathID = errorPath.toString().hashCode();

    return success;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(this);
    pStatsCollection.add(interpolatingRefiner);
  }

  @Override
  public String getName() {
    return "NumericAnalysisDelegatingRefiner";
  }

  @Override
  public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
    out.println(
        "  number of value analysis refinements:                "
            + numberOfValueAnalysisRefinements);
    out.println(
        "  number of successful valueAnalysis refinements:      "
            + numberOfSuccessfulValueAnalysisRefinements);
  }

  /**
   * This method checks if the given path is feasible, when doing a full-precision check.
   *
   * @param path the path to check
   * @return true, if the path is feasible, else false
   * @throws CPAException if the path check gets interrupted
   */
  boolean isPathFeasible(ARGPath path) throws CPAException, InterruptedException {
    return valueAnalysisChecker.isFeasible(path);
  }

  /** Creates a new OctagonAnalysisPathChecker, which checks the given path at full precision. */
  private OctagonAnalysisFeasibilityChecker createNumericFeasibilityChecker(ARGPath path)
      throws CPAException {
    try {
      OctagonAnalysisFeasibilityChecker checker;

      // no specific timelimit set for octagon feasibility check
      if (timeForNumericFeasibilityCheck.isEmpty()) {
        checker =
            new OctagonAnalysisFeasibilityChecker(
                config,
                shutdownNotifier,
                path,
                NumericCPA.class,
                cfa.getVarClassification(),
                transferRelation,
                new NumericState(domainManager, logger));

      } else {
        ShutdownManager shutdown = ShutdownManager.createWithParent(shutdownNotifier);
        WalltimeLimit l = WalltimeLimit.fromNowOn(timeForNumericFeasibilityCheck);
        ResourceLimitChecker limits =
            new ResourceLimitChecker(shutdown, Collections.singletonList(l));

        limits.start();
        checker =
            new OctagonAnalysisFeasibilityChecker(
                config,
                shutdown.getNotifier(),
                path,
                NumericCPA.class,
                cfa.getVarClassification(),
                transferRelation,
                new NumericState(domainManager, logger));
        limits.cancel();
      }

      return checker;
    } catch (InterruptedException | InvalidConfigurationException e) {
      throw new CPAException("counterexample-check failed: ", e);
    }
  }
}