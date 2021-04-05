// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0
package org.sosy_lab.cpachecker.util.loopAbstraction;

import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.util.loopInformation.LoopInformation;

/**
 * This class helps to abstract the loop code so that different cpa's or bounded model checker can
 * easily iterate over it. You have to enable the option, let it run with a cpa. You get a new file
 * with the changed code. After that you have to disable this function and let the cpa run with the
 * newly created file.
 */
@Options(prefix = "loopacc.loopabstractionheader")
public class LoopAbstractionHeader implements StatisticsProvider {

  @Option(
      secure = true,
      name = "shouldAbstract",
      description = "AbstractLoops to be able to process them")
  private AbstractionLevel shouldAbstract = AbstractionLevel.NONE;

  @Option(
      secure = true,
      name = "onlyAccelerableLoops",
      description = "Change this option only if you want all of the loops to be abstracted.")
  private boolean accLoops = true;

  private LoopAbstraction loopAbstraction;
  private AbstractionStatistic timeToAbstract;
  private final String abstractedSource;

  /**
   * Constructor that enables the CPAchecker to rewrite the loops in the programs, you can choose
   * between the loops that can't be analyzed by a BMC, all the loops or none
   *
   * @param loopI LoopInformation object that includes all the info needed to abstract a loop
   * @param automate boolean that shows if the files get automatically overwritten
   * @param config configuration object that enables switching between the 3 modes, default is that
   *     none of the data will be rewritten
   * @param logger logger that logs all exceptions
   * @throws InvalidConfigurationException throws an exception if the configuration doesn't match
   *     the supported options
   */
  public LoopAbstractionHeader(
      LoopInformation loopI, boolean automate, Configuration config, LogManager logger)
      throws InvalidConfigurationException {
    config.inject(this);

    loopAbstraction = new LoopAbstraction(config);
    if (!shouldAbstract.equals(AbstractionLevel.NONE)) {
      loopAbstraction.changeFileToAbstractFile(loopI, logger, shouldAbstract, automate, accLoops);
      abstractedSource = loopAbstraction.getAbstractedSource();
    } else {
      abstractedSource = null;
    }
    timeToAbstract = new AbstractionStatistic(loopAbstraction.getTimeToAbstract());
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(timeToAbstract);
  }

  public String getAbstractedSource() {
    return abstractedSource;
  }
}
