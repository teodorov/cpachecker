// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.acsl;

import java.util.Set;

public interface LogicExpression {

  /**
   * Returns whether the logic expression may be used in a clause of the given type.
   *
   * @param clauseType the type of the clause the logic expression should be used in
   * @return true if the logic expression may be used in a clause of the given type, false otherwise
   */
  boolean isAllowedIn(Class<?> clauseType);

  /**
   * Returns a set of all the ACSL built-ins that are used in the logic expression.
   */
  Set<ACSLBuiltin> getUsedBuiltins();

  /**
   * Replaces all identifiers that are captured by the given binders with bound identifiers
   * and returns the new logic expression.
   */
  LogicExpression apply(Set<Binder> binders, Binder.Quantifier quantifier);

}
