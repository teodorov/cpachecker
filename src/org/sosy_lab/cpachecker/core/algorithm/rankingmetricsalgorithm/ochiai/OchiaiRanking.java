// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0
package org.sosy_lab.cpachecker.core.algorithm.rankingmetricsalgorithm.ochiai;

import org.sosy_lab.cpachecker.core.algorithm.rankingmetricsalgorithm.Ranking;

public class OchiaiRanking extends Ranking {
  /**
   * Calculates suspicious of Ochiai algorithm.
   *
   * @param pFailed Is the number of pFailed cases in each edge.
   * @param pPassed Is the number of pPassed cases in each edge.
   * @param totalFailed Is the total number of all possible error paths.
   * @return Calculated suspicious.
   */
  @Override
  public double computeSuspicious(
      double pFailed, double pPassed, double totalFailed, double totalPassed) {

    double denominator = Math.sqrt(totalFailed * (pFailed + pPassed));
    if (denominator == 0.0) {
      return 0.0;
    }
    return pFailed / denominator;
  }
}
