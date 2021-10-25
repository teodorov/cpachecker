// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.postprocessing.summaries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;

/*
 * The Information related to the Summaries.
 * This information will be used in order to change the transfer relation of the location CPA on the fly.
 * The information is encoded in the Nodes. This means that to know if you're entering a
 * strategy you need to check the successor of the edge with which you're entering it.
 *
 * This also contains the Informations needed in order to build the strategies.
 */
public class SummaryInformation {

  private Map<StrategiesEnum, CFANode> strategiesToNodes = new HashMap<>();
  private Map<CFANode, StrategiesEnum> nodesToStrategies = new HashMap<>();
  private Map<CFANode, GhostCFA> startNodeGhostCFAToGhostCFA = new HashMap<>();
  private Map<CFANode, GhostCFA> startNodeOriginalCFAToGhostCFA = new HashMap<>();
  private Map<CFANode, Map<String, CExpression>> variableDeclarations = new HashMap<>();
  private Map<CFANode, Loop> nodeToLoopStructure = new HashMap<>();
  private Set<StrategyInterface> strategies = new HashSet<>();
  private StrategyFactory factory;

  public SummaryInformation(CFA pCfa) {
    this.addCfaInformations(pCfa);
  }

  public void addNodeForStrategy(StrategiesEnum strategy, CFANode node) {
    strategiesToNodes.put(strategy, node);
    nodesToStrategies.put(node, strategy);
  }

  public void addGhostCFA(GhostCFA ghostCFA) {
    startNodeGhostCFAToGhostCFA.put(ghostCFA.getStartGhostCfaNode(), ghostCFA);
    startNodeOriginalCFAToGhostCFA.put(ghostCFA.getStartOriginalCfaNode(), ghostCFA);
    for (CFANode n : ghostCFA.getAllNodes()) {
      this.addNodeForStrategy(StrategiesEnum.Base, n);
    }

    addNodeForStrategy(ghostCFA.getStrategy(), ghostCFA.getStartGhostCfaNode());
    addNodeForStrategy(ghostCFA.getStrategy(), ghostCFA.getStopGhostCfaNode());
  }

  public StrategiesEnum getStrategyForEdge(CFAEdge edge) {
    if (nodesToStrategies.get(edge.getPredecessor()) == StrategiesEnum.Base) {
      return nodesToStrategies.get(edge.getSuccessor());
    } else {
      return nodesToStrategies.get(edge.getPredecessor());
    }
  }

  public StrategiesEnum getStrategyForNode(CFANode node) {
    return nodesToStrategies.get(node);
  }

  public Map<String, CExpression> getVariableDeclarationsForNode(CFANode node) {
    return variableDeclarations.get(node);
  }

  public void addStrategy(StrategyInterface strategy) {
    strategies.add(strategy);
  }

  public void addCfaInformations(CFA pCfa) {
    Optional<LoopStructure> optionalLoopStructure = pCfa.getLoopStructure();
    if (!optionalLoopStructure.isEmpty()) {
      for (Loop loop : optionalLoopStructure.get().getAllLoops()) {
        for (CFANode node : loop.getLoopHeads()) {
          nodeToLoopStructure.put(node, loop);
        }
      }
    }

    for (CFANode n : pCfa.getAllNodes()) {
      this.addNodeForStrategy(StrategiesEnum.Base, n);
    }
  }

  public Set<StrategyInterface> getStrategies() {
    return strategies;
  }

  public void setFactory(StrategyFactory pFactory) {
    factory = pFactory;
  }

  public StrategyFactory getFactory() {
    return factory;
  }

  public Optional<Loop> getLoop(CFANode node) {
    Loop loop = nodeToLoopStructure.get(node);
    if (Objects.isNull(loop)) {
      return Optional.empty();
    }
    return Optional.of(loop);
  }
}
