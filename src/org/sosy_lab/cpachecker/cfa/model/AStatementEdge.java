// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.model;

import com.google.common.base.Optional;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;


public class AStatementEdge extends AbstractCFAEdge {

  private static final long serialVersionUID = 2639832981364107114L;
  protected final AStatement statement;

  protected AStatementEdge(String pRawStatement, AStatement pStatement,
      FileLocation pFileLocation, CFANode pPredecessor, CFANode pSuccessor) {

    super(pRawStatement, pFileLocation, pPredecessor, pSuccessor);
    statement = pStatement;
  }

  @Override
  public CFAEdgeType getEdgeType() {
    return CFAEdgeType.StatementEdge;
  }

  public AStatement getStatement() {
    return statement;
  }

  @Override
  public Optional<? extends AStatement> getRawAST() {
    return Optional.of(statement);
  }

  @Override
  public String getCode() {
    return statement.toASTString();
  }

  @Override
  public CFAEdge copyWith(CFANode pNewPredecessorNode, CFANode pNewSuccessorNode) {
    return new AStatementEdge(
        getRawStatement(),
        getStatement(),
        getFileLocation(),
        pNewPredecessorNode,
        pNewSuccessorNode);
  }
}
