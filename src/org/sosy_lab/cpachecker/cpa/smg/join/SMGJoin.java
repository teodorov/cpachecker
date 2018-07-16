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
package org.sosy_lab.cpachecker.cpa.smg.join;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.smg.CLangStackFrame;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.UnmodifiableSMGState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.UnmodifiableCLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.util.PersistentStack;

/**
 * Joins two SMGs and provides a new merged SMG. Can use a surrounding SMGState to extract further
 * information.
 */
public final class SMGJoin {
  static public void performChecks(boolean pOn) {
    SMGJoinSubSMGs.performChecks(pOn);
  }

  private boolean defined = false;
  private SMGJoinStatus status = SMGJoinStatus.EQUAL;
  private final CLangSMG smg;
  final SMGLevelMapping levelMap = SMGLevelMapping.createDefaultLevelMap();

  /**
   * Algorithm 10 from FIT-TR-2012-04.
   *
   * @param opSMG1 left SMG for the join.
   * @param opSMG2 right SMG for the join.
   * @param pStateOfSmg1 state containing the left SMG, can be NULL for testing only.
   * @param pStateOfSmg2 state containing the right SMG, can be NULL for testing only.
   */
  public SMGJoin(
      UnmodifiableCLangSMG opSMG1,
      UnmodifiableCLangSMG opSMG2,
      UnmodifiableSMGState pStateOfSmg1,
      UnmodifiableSMGState pStateOfSmg2)
      throws SMGInconsistentException {

    smg = new CLangSMG(opSMG1.getMachineModel());

    // the mapping collects all visited nodes and is used for terminating the algorithms.
    SMGNodeMapping mapping1 = new SMGNodeMapping();
    SMGNodeMapping mapping2 = new SMGNodeMapping();

    Map<String, SMGRegion> globals_in_smg1 = opSMG1.getGlobalObjects();
    Map<String, SMGRegion> globals_in_smg2 = opSMG2.getGlobalObjects();

    // FIT-TR-2012-04, Alg 10, line 2
    SMGJoinStatus tmpStatus1 =
        joinGlobalVariables(mapping1, mapping2, globals_in_smg1, globals_in_smg2);
    status = status.updateWith(tmpStatus1);

    // FIT-TR-2012-04, Alg 10, line 2
    SMGJoinStatus tmpStatus2 =
        joinStackVariables(mapping1, mapping2, opSMG1.getStackFrames(), opSMG2.getStackFrames());
    status = status.updateWith(tmpStatus2);

    // FIT-TR-2012-04, Alg 10, line 3
    // join heap for globally pointed objects, global variable names are already joined
    for (Entry<String, SMGRegion> entry : smg.getGlobalObjects().entrySet()) {
      SMGObject globalInSMG1 = globals_in_smg1.get(entry.getKey());
      SMGObject globalInSMG2 = globals_in_smg2.get(entry.getKey());
      SMGObject destinationGlobal = mapping1.get(globalInSMG1);
      SMGJoinSubSMGs jss = new SMGJoinSubSMGs(status, opSMG1, opSMG2, smg, mapping1, mapping2, levelMap, globalInSMG1, globalInSMG2, destinationGlobal, 0,false, pStateOfSmg1, pStateOfSmg2);
      status = jss.getStatus();
      if (!jss.isDefined()) {
        return;
      }
    }

    // FIT-TR-2012-04, Alg 10, line 3
    // join heap for locally pointed objects, variable names per stackframe are already joined
    Iterator<CLangStackFrame> smg1stackIterator = opSMG1.getStackFrames().iterator();
    Iterator<CLangStackFrame> smg2stackIterator = opSMG2.getStackFrames().iterator();
    Iterator<CLangStackFrame> destSmgStackIterator = smg.getStackFrames().iterator();
    while (smg1stackIterator.hasNext() && smg2stackIterator.hasNext()) {
      CLangStackFrame frameInSMG1 = smg1stackIterator.next();
      CLangStackFrame frameInSMG2 = smg2stackIterator.next();
      CLangStackFrame destStackFrame = destSmgStackIterator.next();

      for (String localVar : destStackFrame.getVariables().keySet()) {
        SMGObject localInSMG1 = frameInSMG1.getVariable(localVar);
        SMGObject localInSMG2 = frameInSMG2.getVariable(localVar);
        SMGObject destinationLocal = mapping1.get(localInSMG1);
        SMGJoinSubSMGs jss = new SMGJoinSubSMGs(status, opSMG1, opSMG2, smg, mapping1, mapping2, levelMap, localInSMG1, localInSMG2, destinationLocal, 0, false, pStateOfSmg1, pStateOfSmg2);
        status = jss.getStatus();
        if (!jss.isDefined()) {
          return;
        }
      }

      /* Don't forget to join the return object */
      if (frameInSMG1.getReturnObject() != null) {
        SMGObject returnObjectInSmg1 = frameInSMG1.getReturnObject();
        SMGObject returnObjectInSmg2 = frameInSMG2.getReturnObject();
        SMGObject destinationLocal = destStackFrame.getReturnObject();
        mapping1.map(returnObjectInSmg1, destinationLocal);
        mapping2.map(returnObjectInSmg2, destinationLocal);
        SMGJoinSubSMGs jss =
            new SMGJoinSubSMGs(status, opSMG1, opSMG2, smg, mapping1, mapping2, levelMap, returnObjectInSmg1,
                returnObjectInSmg2, destinationLocal, 0, false, pStateOfSmg1, pStateOfSmg2);
        if (!jss.isDefined()) {
          return;
        }
        status = jss.getStatus();
      }
    }

    defined = true;
  }

  /**
   * searches for common global variables and copies them over into a new SMG.
   *
   * <p>Note that one SMG can have less variables than the other one, e.g., if refinement allows to
   * ignore some variables.
   */
  private SMGJoinStatus joinGlobalVariables(
      SMGNodeMapping mapping1,
      SMGNodeMapping mapping2,
      Map<String, SMGRegion> globals_in_smg1,
      Map<String, SMGRegion> globals_in_smg2) {
    Set<String> globals1 = globals_in_smg1.keySet();
    Set<String> globals2 = globals_in_smg2.keySet();
    for (String globalVar : Sets.intersection(globals1, globals2)) {
      SMGRegion globalInSMG1 = globals_in_smg1.get(globalVar);
      SMGRegion globalInSMG2 = globals_in_smg2.get(globalVar);
      smg.addGlobalObject(globalInSMG1);
      mapping1.map(globalInSMG1, globalInSMG1);
      mapping2.map(globalInSMG2, globalInSMG1);
    }
    return getFlag(globals1.containsAll(globals2), globals2.containsAll(globals1));
  }

  // crosswise check all combinations
  private static SMGJoinStatus getFlag(boolean oneInTwo, boolean twoInOne) {
    if (oneInTwo) {
      return twoInOne ? SMGJoinStatus.EQUAL : SMGJoinStatus.LEFT_ENTAIL;
    } else {
      return twoInOne ? SMGJoinStatus.RIGHT_ENTAIL : SMGJoinStatus.INCOMPARABLE;
    }
  }

  /**
   * merges common stack variables and copies them over into a new SMG.
   *
   * <p>We assume identical stack sizes (incl. function names), otherwise we return INCOMPARABLE.
   *
   * <p>Note that one SMG can have less variables than the other one, e.g., if refinement allows to
   * ignore some variables.
   */
  private SMGJoinStatus joinStackVariables(
      SMGNodeMapping mapping1,
      SMGNodeMapping mapping2,
      PersistentStack<CLangStackFrame> stack1,
      PersistentStack<CLangStackFrame> stack2) {
    Iterator<CLangStackFrame> smg1stackIterator = stack1.iterator();
    Iterator<CLangStackFrame> smg2stackIterator = stack2.iterator();
    SMGJoinStatus result = SMGJoinStatus.EQUAL;
    while (smg1stackIterator.hasNext() && smg2stackIterator.hasNext()) {
      CLangStackFrame frameInSMG1 = smg1stackIterator.next();
      CLangStackFrame frameInSMG2 = smg2stackIterator.next();
      if (!frameInSMG1.getFunctionDeclaration().equals(frameInSMG2.getFunctionDeclaration())) {
        return SMGJoinStatus.INCOMPARABLE;
      }
      smg.addStackFrame(frameInSMG1.getFunctionDeclaration());
      Set<String> locals1 = frameInSMG1.getVariables().keySet();
      Set<String> locals2 = frameInSMG2.getVariables().keySet();
      for (String localVar : Sets.intersection(locals1, locals2)) {
        SMGRegion localInSMG1 = frameInSMG1.getVariable(localVar);
        SMGRegion localInSMG2 = frameInSMG2.getVariable(localVar);
        smg.addStackObject(localInSMG1);
        mapping1.map(localInSMG1, localInSMG1);
        mapping2.map(localInSMG2, localInSMG1);
      }
      result =
          status.updateWith(getFlag(locals1.containsAll(locals2), locals2.containsAll(locals1)));
    }
    return result;
  }

  public boolean isDefined() {
    if (!defined) {
      Preconditions.checkState(
          status == SMGJoinStatus.INCOMPARABLE || status == SMGJoinStatus.INCOMPLETE,
          "Join of SMGs not defined, but status is " + status);
    }
    return defined;
  }

  public SMGJoinStatus getStatus() {
    return status;
  }

  public CLangSMG getJointSMG() {
    return smg;
  }
}
