// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.acsl.util;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.util.CFAUtils;

public class BlockStructure {
  private final ImmutableSet<ACSLBlock> blocks;

  BlockStructure(Collection<ACSLBlock> pBlocks) {
    blocks = ImmutableSet.copyOf(pBlocks);
  }

  public Set<ACSLBlock> getBlocks() {
    return blocks;
  }

  public ACSLBlock getInnermostBlockOf(CFANode node) {
    ACSLBlock innermostBlock = null;
    for (ACSLBlock block : blocks) {
      if (block.getContainedNodes().contains(node)
          && (innermostBlock == null || innermostBlock.contains(block))) {
        innermostBlock = block;
      }
    }
    return innermostBlock;
  }

  public ACSLBlock getInnermostBlockOf(FileLocation location) {
    ACSLBlock innermostBlock = null;
    for (ACSLBlock block : blocks) {
      if (block.getStartOffset() < location.getNodeOffset()
          && location.getNodeOffset() < block.getEndOffset()
          && (innermostBlock == null || innermostBlock.contains(block))) {
        innermostBlock = block;
      }
    }
    return innermostBlock;
  }

  public Set<CFANode> getNodesInInnermostBlockOf(CFANode node) {
    return getInnermostBlockOf(node).getContainedNodes();
  }

  private boolean ignoreEdge(CFAEdge edge) {
    if (edge instanceof CFunctionCallEdge
        || edge.getFileLocation().equals(FileLocation.DUMMY)
        || edge.getDescription().contains("__CPAchecker_TMP")) {
      return true;
    }
    return false;
  }

  public Set<CFAEdge> getPrevEdges(ACSLBlock pBlock, FileLocation location) {
    Set<CFAEdge> edges = new HashSet<>();
    for (CFANode node : pBlock.getContainedNodes()) {
      CFAUtils.enteringEdges(node).copyInto(edges);
      CFAUtils.leavingEdges(node).copyInto(edges);
    }
    edges.removeIf(this::ignoreEdge);
    List<CFAEdge> sortedEdges = new ArrayList<>(edges);
    sortedEdges.sort(Comparator.comparingInt(pEdge -> pEdge.getFileLocation().getNodeOffset()));

    CFAEdge prev = null;
    for (CFAEdge edge : sortedEdges) {
      if (edge.getFileLocation().getNodeOffset() > location.getNodeOffset()) {
        break;
      }
      prev = edge;
    }

    if (prev == null) {
      // The specified location is directly at the beginning of the given block
      return ImmutableSet.of();
    }

    ACSLBlock innermostBlockOfPrevEdge = getInnermostBlockOf(prev.getSuccessor());
    if (innermostBlockOfPrevEdge.equals(pBlock)) {
      return CFAUtils.enteringEdges(prev.getSuccessor()).toSet();
    }

    // There is at least one block end directly before the specified location, so return all leaving
    // edges of the last of these blocks
    ACSLBlock lastBlock = null;
    for (ACSLBlock block : getBlocks()) {
      if (!block.getContainedNodes().contains(prev.getPredecessor())
          || block.getEndOffset() >= location.getNodeOffset()) {
        continue;
      }
      if (lastBlock == null || block.contains(lastBlock)) {
        lastBlock = block;
      }
    }
    assert lastBlock != null
        : "Previous edge is allegedly in another block, but no such block was found";
    return lastBlock.getLeavingEdges();
  }

  public CFAEdge getNextEdge(ACSLBlock block, FileLocation location) {
    Set<CFAEdge> edges = new HashSet<>();
    for (CFANode node : block.getContainedNodes()) {
      CFAUtils.enteringEdges(node).copyInto(edges);
      CFAUtils.leavingEdges(node).copyInto(edges);
    }
    edges.removeIf(this::ignoreEdge);
    List<CFAEdge> sortedEdges = new ArrayList<>(edges);
    sortedEdges.sort(Comparator.comparingInt(pEdge -> pEdge.getFileLocation().getNodeOffset()));

    for (CFAEdge edge : sortedEdges) {
      if (edge.getFileLocation().getNodeOffset() > location.getNodeOffset()) {
        return edge;
      }
    }
    return null;
  }
}
