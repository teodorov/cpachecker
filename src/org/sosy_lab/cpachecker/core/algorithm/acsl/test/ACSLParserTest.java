/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.acsl.test;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.CFAWithACSLAnnotationLocations;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.test.TestDataTools;

@RunWith(Parameterized.class)
public class ACSLParserTest {

  private static String TEST_DIR = "test/programs/acsl/";

  private String programName;
  private CFACreator cfaCreator;

  public ACSLParserTest(String pProgramName) throws InvalidConfigurationException {
    programName = pProgramName;
    Configuration config =
        TestDataTools.configurationForTest()
            .loadFromResource(ACSLParserTest.class, "acslToWitness.properties")
            .build();
    cfaCreator =
        new CFACreator(config, LogManager.createTestLogManager(), ShutdownNotifier.createDummy());
  }

  @Parameters(name = "{0}")
  public static Object[] data() {
    ImmutableList.Builder<Object> b = ImmutableList.builder();
    b.add("abs.c");
    b.add("even.c");
    b.add("simple.c");
    return b.build().toArray();
  }

  @Test
  public void test()
      throws InterruptedException, ParserException, InvalidConfigurationException, IOException {
    List<String> files = ImmutableList.of(Paths.get(TEST_DIR, programName).toString());
    CFAWithACSLAnnotationLocations cfaWithLocs =
        (CFAWithACSLAnnotationLocations) cfaCreator.parseFileAndCreateCFA(files);
    assertThat(cfaWithLocs.getCommentPositions().size()).isGreaterThan(0);
    assertThat(cfaWithLocs.getEdgesToAnnotations().keySet().size())
        .isAtLeast(cfaWithLocs.getCommentPositions().size());
  }
}