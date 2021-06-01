// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.acsl.util;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.Pair;

public class BlockStructureBuilder {

  private final CFA cfa;

  public BlockStructureBuilder(CFA pCFA) {
    cfa = pCFA;
  }

  public BlockStructure build(String programPath) throws IOException {
    ArrayDeque<Integer> locationStack = new ArrayDeque<>();
    Set<Block> blocks = new HashSet<>();
    int i = 0;
    String fileContent = Files.asCharSource(new File(programPath), Charsets.UTF_8).read();
    while (!fileContent.isEmpty()) {
      // TODO: Detect if/for/while... even if there are no braces
      if (fileContent.charAt(0) == '{') {
        locationStack.push(i);
      } else if (fileContent.charAt(0) == '}') {
        blocks.add(new StatementBlock(locationStack.pop(), i));
      }
      fileContent = fileContent.substring(1);
      i++;
    }
    convertFunctionsToFunctionBlocks(blocks);
    convertLoopsToLoopBlocks(blocks);
    return new BlockStructure(cfa, blocks);
  }

  private void convertFunctionsToFunctionBlocks(Set<Block> blocks) {
    Map<FunctionEntryNode, Pair<Block, Integer>> functionsToBlocks = new HashMap<>();
    for (Block block : blocks) {
      if (block.isFunction() || block.isLoop()) {
        // block was converted already
        continue;
      }
      for (FunctionEntryNode functionHead : cfa.getAllFunctionHeads()) {
        int distance = block.getStartOffset() - functionHead.getFileLocation().getNodeOffset();
        if (distance > 0
            && (!functionsToBlocks.containsKey(functionHead)
                || distance < functionsToBlocks.get(functionHead).getSecond())) {
          functionsToBlocks.put(functionHead, Pair.of(block, distance));
        }
      }
    }

    for (Entry<FunctionEntryNode, Pair<Block, Integer>> entry : functionsToBlocks.entrySet()) {
      blocks.remove(entry.getValue().getFirst());
      blocks.add(new FunctionBlock(entry.getKey()));
    }
  }

  private void convertLoopsToLoopBlocks(Set<Block> blocks) {
    if (cfa.getLoopStructure().isEmpty()) {
      return;
    }

    Set<CFAEdge> loopStarts = new HashSet<>();
    for (CFANode node : cfa.getAllNodes()) {
      for (int i = 0; i < node.getNumLeavingEdges(); i++) {
        CFAEdge currentEdge = node.getLeavingEdge(i);
        String description = currentEdge.getDescription();
        if (description.equals("do") || description.equals("while") || description.equals("for")) {
          loopStarts.add(currentEdge);
        }
      }
    }

    Map<Block, CFAEdge> loops = new HashMap<>();
    for (CFAEdge loopStart : loopStarts) {
      Block loop = null;
      int minDist = -1;
      for (Block block : blocks) {
        if (block.isFunction() || block.isLoop()) {
          // block was converted already
          continue;
        }
        int distance = block.getStartOffset() - loopStart.getFileLocation().getNodeOffset();
        if (loop == null || (distance > 0 && distance < minDist)) {
          loop = block;
          minDist = distance;
        }
      }
      loops.put(loop, loopStart);
    }

    for (Loop loop : cfa.getLoopStructure().get().getAllLoops()) {
      Set<CFAEdge> incomingEdges = loop.getIncomingEdges();
      if (incomingEdges.size() != 1) {
        continue;
      }
      for (Entry<Block, CFAEdge> entry : loops.entrySet()) {
        if (incomingEdges.contains(entry.getValue())) {
          Block oldBlock = entry.getKey();
          blocks.remove(oldBlock);
          blocks.add(new LoopBlock(oldBlock.getStartOffset(), oldBlock.getEndOffset(), loop));
        }
      }
    }
  }
}
