/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.ci.redundancyremover;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.interval.IntegerIntervalCreator;
import org.sosy_lab.cpachecker.cpa.interval.IntegerInterval;
import org.sosy_lab.cpachecker.cpa.interval.NumberInterface;
import org.sosy_lab.cpachecker.cpa.interval.UnifyAnalysisState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.ci.redundancyremover.RedundantRequirementsRemover.RedundantRequirementsRemoverImplementation;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;


public class RedundantRequirementsRemoverIntervalStateImplementation extends
    RedundantRequirementsRemoverImplementation<UnifyAnalysisState, NumberInterface> {

  private static final long serialVersionUID = 1323131138350817689L;

  @Override
  public int compare(NumberInterface pO1, NumberInterface pO2) {
    // one of arguments null -> NullPointerException
    // 0 if bounds the same for both
    // -1 if both bounds of p01 contained in bounds of p02
    // 1 if both bounds p02 contained in bounds of p01
    // -1 if p01 lower bound smaller than p02 bound
    // -1 if p01 lower bound equal to p02 lower bound and p01 higher bound smaller than p02 higher bound
    // otherwise 1

    if (pO1 == null || pO2 == null) {
      throw new NullPointerException("At least one of the arguments " + pO1 + " or " + pO2 + " is null.");
    } else if (pO1.getLow().equals(pO2.getLow()) && pO1.getHigh().equals(pO2.getHigh())) {
      return 0;
    } else if (pO2.contains(pO1)) {
      return -1;
    } else if (pO1.contains(pO2)) {
      return 1;
//    } else if (pO1.getLow().compareTo(pO2.getLow()) < 0) {
      //TODO temporary solution
    } else if (pO1.getLow().longValue()<pO2.getLow().longValue()) {
      return -1;
//    } else if (pO1.getLow().equals(pO2.getLow()) && pO1.getHigh().compareTo(pO2.getHigh()) < 0) {
    } else if (pO1.getLow().equals(pO2.getLow()) && pO1.getHigh().longValue() < pO2.getHigh().longValue()) {
      return -1;
    }

    return 1;
  }

  @Override
  protected boolean covers(NumberInterface pCovering, NumberInterface pCovered) {
    // return pCovering contains pCovered
    return pCovering.contains(pCovered);
  }

  @Override
  protected NumberInterface getAbstractValue(UnifyAnalysisState pAbstractState, String pVarOrConst) {
    // if pVarOrConst number, return interval [pVarOrConst,pVarOrConst]
    // if state contains pVarOrConst return interval saved in state
    // otherwise unboundedInterval

    try {
      long constant = Long.parseLong(pVarOrConst);
      return new IntegerIntervalCreator().factoryMethod(constant);
    } catch (NumberFormatException e) {
      if (pAbstractState.contains(MemoryLocation.valueOf(pVarOrConst))) {
        return pAbstractState.getElement(MemoryLocation.valueOf(pVarOrConst));
      }
    }
//TODO should be a NumberInerface
    return IntegerInterval.UNBOUND;
  }


  @Override
  protected NumberInterface[] emptyArrayOfSize(int pSize) {
    return new NumberInterface[Math.max(0, pSize)];
  }

  @Override
  protected NumberInterface[][] emptyMatrixOfSize(int pSize) {
    return new NumberInterface[Math.max(0, pSize)][];
  }

  @Override
  protected UnifyAnalysisState extractState(AbstractState pWrapperState) {
    // AbstractStates.extractState..
    return AbstractStates.extractStateByType(pWrapperState, UnifyAnalysisState.class); // TODO so?
  }

}
