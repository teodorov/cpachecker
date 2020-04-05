/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.faultlocalization.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.util.faultlocalization.Fault;
import org.sosy_lab.cpachecker.util.faultlocalization.FaultExplanation;
import org.sosy_lab.cpachecker.util.faultlocalization.FaultRanking;
import org.sosy_lab.cpachecker.util.faultlocalization.FaultContribution;
import org.sosy_lab.cpachecker.util.faultlocalization.FaultReason;
import org.sosy_lab.cpachecker.util.faultlocalization.FaultReason.ReasonType;

public class HintRanking implements FaultRanking {
  private int maxNumberOfHints;
  /**
   * Custom explanation for a singleton.
   * This explanation should be restricted to explain single edges only.
   */
  private FaultExplanation explanation;

  /**
   * Create hints for the first pMaxNumberOfHints sets in the ErrorIndicatorSet
   * @param pMaxNumberOfHints number of hints to be printed. Passing -1 leads to hints for all elements in the set.
   */
  public HintRanking(int pMaxNumberOfHints){
    maxNumberOfHints=pMaxNumberOfHints;
    explanation = new NoContextExplanation();
  }

  /**
   * Create hints for the first pMaxNumberOfHints sets in the ErrorIndicatorSet
   * @param pMaxNumberOfHints number of hints to be printed. Passing -1 leads to hints for all elements in the set.
   * @param pFaultExplanation explanation for Faults containing only one FaultContribution.
   */
  public HintRanking(int pMaxNumberOfHints, FaultExplanation pFaultExplanation){
    maxNumberOfHints=pMaxNumberOfHints;
    explanation = pFaultExplanation;
  }

  @Override
  public List<Fault> rank(
      Set<Fault> result) {
    // if maxNumberOfHints is negative create hints for all elements in the set.
    boolean maxNumberOfHintsNegative = maxNumberOfHints < 0;
    for (Fault faultLocalizationOutputs : result) {
      int hints = 0;
      for (FaultContribution faultContribution : faultLocalizationOutputs) {
        FaultReason reason = FaultReason.explain(ReasonType.HINT, explanation, new Fault(faultContribution),0);
        if(maxNumberOfHintsNegative || hints < maxNumberOfHints){
          faultLocalizationOutputs.addReason(reason);
        }
        faultContribution.addReason(reason);
        hints++;
      }
    }
    return new ArrayList<>(result);
  }
}