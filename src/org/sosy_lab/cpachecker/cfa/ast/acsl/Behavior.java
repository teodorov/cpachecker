// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.acsl;

public class Behavior {

  private final String name;
  private final EnsuresClause ensuresClause;
  private final RequiresClause requiresClause;
  private final AssumesClause assumesClause;

  public Behavior(String pName, EnsuresClause ens, RequiresClause req, AssumesClause ass) {
    name = pName;
    ensuresClause = new EnsuresClause(ens.getPredicate().simplify());
    requiresClause = new RequiresClause(req.getPredicate().simplify());
    assumesClause = new AssumesClause(ass.getPredicate().simplify());
  }

  public String getName() {
    return name;
  }

  public AssumesClause getAssumesClause() {
    return assumesClause;
  }

  public ACSLPredicate getPreStatePredicate() {
    ACSLPredicate requiresPredicate = requiresClause.getPredicate();
    ACSLPredicate negatedAssumesPredicate = assumesClause.getPredicate().negate();
    return new ACSLLogicalPredicate(requiresPredicate, negatedAssumesPredicate, ACSLBinaryOperator.OR);
  }

  public ACSLPredicate getPostStatePredicate() {
    ACSLPredicate ensuresPredicate = ensuresClause.getPredicate();
    ACSLPredicate negatedAssumesPredicate =
        new PredicateAt(assumesClause.getPredicate(), ACSLDefaultLabel.OLD).negate();
    return new ACSLLogicalPredicate(ensuresPredicate, negatedAssumesPredicate, ACSLBinaryOperator.OR);
  }

  @Override
  public String toString() {
    return "behavior "
        + name
        + ":\n"
        + assumesClause.toString()
        + '\n'
        + requiresClause.toString()
        + '\n'
        + ensuresClause.toString();
  }
}