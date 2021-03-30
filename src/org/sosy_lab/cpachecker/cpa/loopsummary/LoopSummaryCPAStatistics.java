// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.loopsummary;

import com.google.common.base.Splitter;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.loopsummary.strategies.StrategyInterface;

@Options(prefix = "cpa.loopsummary")
class LoopSummaryCPAStatistics implements Statistics {

  @SuppressWarnings("unused")
  @Option(name = "test", secure = true, description = "test")
  private boolean test = false;

  @SuppressWarnings("unused")
  private final LogManager logger;

  @SuppressWarnings("unused")
  private final AbstractLoopSummaryCPA cpa;

  private final LinkedHashMap<String, Integer> strategiesUsed = new LinkedHashMap<>();

  public LoopSummaryCPAStatistics(Configuration pConfig, LogManager pLogger, AbstractLoopSummaryCPA pCpa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    cpa = pCpa;
    for (StrategyInterface s : pCpa.getStrategies()) {
      strategiesUsed.put(s.getClass().getName(), 0);
    }
  }

  @Override
  public String getName() {
    return "LoopSummaryCPA";
  }

  public void updateSummariesUsed(String summaryName, Integer timesUsed) {
    if (strategiesUsed.containsKey(summaryName)) {
      strategiesUsed.put(summaryName, strategiesUsed.get(summaryName) + timesUsed);
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
    out.println("Strategy Statistics:");
    for (String k : strategiesUsed.keySet()) {
      List<String> splitName = Splitter.on('.').splitToList(k);
      put(
          out,
          "Number of times Strategy " + splitName.get(splitName.size() - 1) + " was used: ",
          strategiesUsed.get(k));
    }
  }
}
