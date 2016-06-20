/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.automaton;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class AutomatonTopMergeOperator<T extends AbstractState> implements MergeOperator {

  private final AbstractDomain<T> domain;
  private final T topState;

  public AutomatonTopMergeOperator(AbstractDomain<T> pDomain, T pTopState) {
    this.domain = pDomain;
    this.topState = pTopState;
  }

  @Override
  @SuppressWarnings("unchecked")
  public AbstractState merge(AbstractState el1, AbstractState el2, Precision p)
    throws CPAException, InterruptedException {

    boolean anyAutomatonTop =
        domain.isLessOrEqual(topState, (T) el1)
        || domain.isLessOrEqual(topState, (T) el2);

    if (anyAutomatonTop) {
      return topState;
    } else {
      return el2;
    }
  }

}
