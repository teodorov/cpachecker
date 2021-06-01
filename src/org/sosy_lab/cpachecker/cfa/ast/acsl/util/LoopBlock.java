// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.acsl.util;

import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;

class LoopBlock implements Block {
  private final int startOffset;
  private final int endOffset;
  private final Loop loop;

  LoopBlock(int start, int end, Loop pLoop) {
    startOffset = start;
    endOffset = end;
    loop = pLoop;
  }

  public boolean isFunction() {
    return false;
  }

  public boolean isLoop() {
    return true;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  @Override
  public Set<CFAEdge> getEnteringEdges() {
    return loop.getIncomingEdges();
  }

  @Override
  public Set<CFAEdge> getLeavingEdges() {
    return loop.getOutgoingEdges();
  }

  public Set<CFANode> getContainedNodes() {
    return loop.getLoopNodes();
  }

  public void addEnteringEdge(CFAEdge edge) {
    throw new UnsupportedOperationException();
  }

  public void addLeavingEdge(CFAEdge edge) {
    throw new UnsupportedOperationException();
  }
}
