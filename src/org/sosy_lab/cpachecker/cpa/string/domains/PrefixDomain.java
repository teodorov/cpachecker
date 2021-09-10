// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.string.domains;

import org.sosy_lab.cpachecker.cpa.string.StringOptions;
import org.sosy_lab.cpachecker.cpa.string.utils.Aspect;

//@Options(prefix = "string.cpa")
public class PrefixDomain implements AbstractStringDomain {

  // @Option(
  // secure = true,
  // name = "prefixlength",
  // description = "which prefixlength shall be tracked")
  private int prefixLength;
  private static final DomainType TYPE = DomainType.PREFFIX;
  // private final StringOptions options;

  private PrefixDomain(StringOptions pOptions) {
    // options = pOptions;
    prefixLength = pOptions.getPrefixLength();
  }

  @Override
  public Aspect toAdd(String pVariable) {
    int temp = prefixLength;
    if (prefixLength > pVariable.length()) {
      temp = pVariable.length();
    }
    return new Aspect(TYPE, pVariable.substring(0, temp));
  }

  @Override
  public DomainType getType() {
    return TYPE;
  }


  @Override
  public AbstractStringDomain createInstance(StringOptions pOptions) {
    return new PrefixDomain(pOptions);
  }
}
