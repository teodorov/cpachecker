// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.sosy_lab.cpachecker.cfa.ast.timedautomata.TaDeclaration;
import org.sosy_lab.cpachecker.cfa.model.timedautomata.TCFAEdge;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.extensions.EncodingExtension;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.BooleanVarFeatureEncoding;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.DiscreteFeatureEncoding;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.locationencodings.LocationEncoding;
import org.sosy_lab.cpachecker.util.predicates.pathformula.tatoformula.featureencodings.timeencodings.TimeEncoding;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class TATransitionUnrolling extends AutomatonEncoding {
  private final DiscreteFeatureEncoding<TCFAEdge> transitions;
  private final TCFAEdge delayEdge;
  private final Map<TaDeclaration, TCFAEdge> idleEdgesByAutomaton;

  public TATransitionUnrolling(
      FormulaManagerView pFmgr,
      TimedAutomatonView pAutomata,
      TimeEncoding pTime,
      LocationEncoding pLocations,
      Iterable<EncodingExtension> pExtensions) {
    super(pFmgr, pAutomata, pTime, pLocations, pExtensions);
    idleEdgesByAutomaton = new HashMap<>();

    delayEdge = TCFAEdge.createDummyEdge();
    var transitionEncoding = new BooleanVarFeatureEncoding<TCFAEdge>(pFmgr);
    for (var automaton : automata.getAllAutomata()) {
      transitionEncoding.addEntry(automaton, delayEdge, "delay_edge");

      var idleEdge = TCFAEdge.createDummyEdge();
      transitionEncoding.addEntry(automaton, idleEdge, "idle_" + automaton.getName());
      idleEdgesByAutomaton.put(automaton, idleEdge);

      for (var edge : automata.getEdgesByAutomaton(automaton)) {
        transitionEncoding.addEntry(automaton, edge, "edge_" + edge.hashCode());
      }
    }

    transitions = transitionEncoding;
  }

  @Override
  protected BooleanFormula makeAutomatonTransitionsFormula(
      TaDeclaration pAutomaton, int pLastReachedIndex) {
    var delayEdgeFormula = transitions.makeEqualsFormula(pAutomaton, pLastReachedIndex, delayEdge);
    var delayFormula =
        bFmgr.implication(delayEdgeFormula, makeDelayTransition(pAutomaton, pLastReachedIndex));

    var idleEdgeFormula =
        transitions.makeEqualsFormula(
            pAutomaton, pLastReachedIndex, idleEdgesByAutomaton.get(pAutomaton));
    var idleFormula =
        bFmgr.implication(idleEdgeFormula, makeIdleTransition(pAutomaton, pLastReachedIndex));

    var edgeFormulas = new HashSet<BooleanFormula>();
    var discreteSteps =
        from(automata.getEdgesByAutomaton(pAutomaton))
            .transform(
                edge -> {
                  var edgeFormula =
                      transitions.makeEqualsFormula(pAutomaton, pLastReachedIndex, edge);
                  edgeFormulas.add(edgeFormula);
                  return bFmgr.implication(
                      edgeFormula, makeDiscreteStep(pAutomaton, pLastReachedIndex, edge));
                });
    edgeFormulas.add(delayEdgeFormula);
    edgeFormulas.add(idleEdgeFormula);

    var transitionContraints =
        bFmgr.and(delayFormula, idleFormula, bFmgr.and(discreteSteps.toSet()));
    var atLeastOneEdge = bFmgr.or(delayEdgeFormula, idleEdgeFormula, bFmgr.or(edgeFormulas));
    var allEdgePairs =
        from(Sets.cartesianProduct(edgeFormulas, edgeFormulas))
            .filter(pair -> !pair.get(0).equals(pair.get(1)))
            .transform(pair -> bFmgr.and(pair.get(0), pair.get(1)));
    var onlyOneEdge = bFmgr.not(bFmgr.or(allEdgePairs.toSet()));

    return bFmgr.and(transitionContraints, atLeastOneEdge, onlyOneEdge);
  }
}
