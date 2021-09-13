// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.arrayabstraction;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CCfaTransformer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CfaTransformer;
import org.sosy_lab.cpachecker.cfa.ast.c.AbstractTransformingCAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.TransformingCAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CCfaEdgeTransformer;
import org.sosy_lab.cpachecker.cfa.model.c.CCfaNodeTransformer;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.NoException;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Utility class for array abstraction by replacing all array reads with {@code __VERIFIER_nondet_X}
 * calls.
 */
public class ArrayAbstractionNondetRead {

  private ArrayAbstractionNondetRead() {}

  /**
   * Returns the transformed CFA with all array reads replaced by {@code __VERIFIER_nondet_X} calls.
   *
   * @param pConfiguration the configuration to use
   * @param pLogger the logger to use
   * @param pCfa the original array-containing CFA to transform
   * @return the transformed CFA with all array reads replaced by {@code __VERIFIER_nondet_X} calls
   * @throws NullPointerException if any of the parameters is {@code null}
   */
  public static CFA transformCfa(Configuration pConfiguration, LogManager pLogger, CFA pCfa) {

    Objects.requireNonNull(pConfiguration, "pConfiguration must not be null");
    Objects.requireNonNull(pLogger, "pLogger must not be null");
    Objects.requireNonNull(pCfa, "pCfa must not be null");

    ImmutableSet<TransformableArray> transformableArrays =
        TransformableArray.getTransformableArrays(pCfa);
    Map<MemoryLocation, TransformableArray> arrayMemoryLocationToTransformableArray =
        new HashMap<>();
    for (TransformableArray transformableArray : transformableArrays) {
      arrayMemoryLocationToTransformableArray.put(
          transformableArray.getMemoryLocation(), transformableArray);
    }

    CCfaTransformer cfaTransformer =
        CCfaTransformer.createTransformer(pConfiguration, pLogger, pCfa);
    ArrayOperationReplacementMap arrayOperationReplacementMap = new ArrayOperationReplacementMap();
    VariableGenerator variableGenerator = new VariableGenerator("__nondet_variable_");

    for (CFANode node : pCfa.getAllNodes()) {
      for (CFAEdge edge : CFAUtils.allLeavingEdges(node)) {
        ImmutableSet<TransformableArray.ArrayOperation> arrayOperations =
            TransformableArray.getArrayOperations(edge);
        for (TransformableArray.ArrayOperation arrayOperation : arrayOperations) {

          TransformableArray transformableArray =
              arrayMemoryLocationToTransformableArray.get(arrayOperation.getArrayMemoryLocation());
          assert transformableArray != null
              : "Missing TransformableArray for ArrayOperation: " + arrayOperation;

          String replacementVariableName = variableGenerator.createNewVariableName();
          MemoryLocation replacementVariable = MemoryLocation.forIdentifier(replacementVariableName);
          arrayOperationReplacementMap.insertReplacement(edge, arrayOperation, replacementVariable);

          if (arrayOperation.getType() == TransformableArray.ArrayOperationType.READ) {
            CfaTransformer.Node predecessorNode =
                cfaTransformer.get(edge.getPredecessor()).orElseThrow();

            CType replacementType = transformableArray.getArrayType().getType();
            Optional<String> functionName = Optional.of(node.getFunctionName());
            CFAEdge nondetVariableCfaEdge =
                VariableGenerator.createNondetVariableEdge(
                    replacementType, replacementVariableName, functionName);
            CfaTransformer.Edge nondetVariableEdge =
                CfaTransformer.Edge.forOriginal(nondetVariableCfaEdge);
            predecessorNode.insertPredecessor(
                nondetVariableEdge,
                CfaTransformer.Node.forOriginal(predecessorNode.getOriginalCfaNode()));
          }
        }
      }
    }

    CCfaEdgeTransformer edgeTransformer =
        CCfaEdgeTransformer.forAstTransformer(
            (originalCfaEdge, originalAstNode) ->
                arrayOperationReplacementMap
                    .getAstTransformer(originalCfaEdge)
                    .transform(originalAstNode));

    return cfaTransformer.createCfa(CCfaNodeTransformer.DEFAULT, edgeTransformer);
  }

  private static final class ArrayOperationReplacementMap {

    private final Map<CFAEdge, Map<TransformableArray.ArrayOperation, MemoryLocation>>
        replacementsPerEdge;

    private ArrayOperationReplacementMap() {
      replacementsPerEdge = new HashMap<>();
    }

    private void insertReplacement(
        CFAEdge pEdge,
        TransformableArray.ArrayOperation pArrayOperation,
        MemoryLocation pReplacementVariableMemoryLocation) {

      Map<TransformableArray.ArrayOperation, MemoryLocation> replacements =
          replacementsPerEdge.computeIfAbsent(pEdge, key -> new HashMap<>());
      replacements.put(pArrayOperation, pReplacementVariableMemoryLocation);
    }

    private TransformingCAstNodeVisitor<NoException> getAstTransformer(CFAEdge pEdge) {

      Map<TransformableArray.ArrayOperation, MemoryLocation> replacements =
          replacementsPerEdge.computeIfAbsent(pEdge, key -> ImmutableMap.of());
      return new AstTransformingVisitor(replacements);
    }
  }

  private static final class AstTransformingVisitor
      extends AbstractTransformingCAstNodeVisitor<NoException> {

    private final Map<TransformableArray.ArrayOperation, MemoryLocation> arrayOperationReplacements;

    private AstTransformingVisitor(
        Map<TransformableArray.ArrayOperation, MemoryLocation> pArrayOperationToNondetVariable) {
      arrayOperationReplacements = pArrayOperationToNondetVariable;
    }

    @Override
    public CAstNode visit(CArraySubscriptExpression pCArraySubscriptExpression) {

      if (!arrayOperationReplacements.isEmpty()) {

        ImmutableSet<TransformableArray.ArrayOperation> arrayOperations =
            TransformableArray.getArrayOperations(pCArraySubscriptExpression);

        if (arrayOperations.size() == 1) {

          TransformableArray.ArrayOperation arrayOperation =
              arrayOperations.stream().findAny().orElseThrow();
          MemoryLocation nondetVariableMemoryLocation =
              arrayOperationReplacements.get(arrayOperation);

          if (nondetVariableMemoryLocation == null) {
            // an isolated CArraySubscriptExpression is always seen as a read, so the array
            // operation is transformed into a write if the map doesn't contain the read operation
            TransformableArray.ArrayOperation writeArrayOperation =
                arrayOperation.toWriteOperation();
            nondetVariableMemoryLocation = arrayOperationReplacements.get(writeArrayOperation);
          }

          if (nondetVariableMemoryLocation != null) {
            CType type = pCArraySubscriptExpression.getExpressionType();
            return ArrayAbstractionUtils.createCIdExpression(type, nondetVariableMemoryLocation);
          }
        }
      }

      return super.visit(pCArraySubscriptExpression);
    }

    @Override
    public CAstNode visit(CVariableDeclaration pCVariableDeclaration) {

      if (pCVariableDeclaration.getType() instanceof CArrayType) {
        return ArrayAbstractionUtils.createNonArrayVariableDeclaration(pCVariableDeclaration);
      }

      return super.visit(pCVariableDeclaration);
    }
  }
}