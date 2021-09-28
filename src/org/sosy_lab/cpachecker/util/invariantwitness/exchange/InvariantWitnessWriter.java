// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.invariantwitness.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.util.invariantwitness.InvariantWitness;
import org.sosy_lab.cpachecker.util.invariantwitness.exchange.model.InvariantStoreEntry;
import org.sosy_lab.cpachecker.util.invariantwitness.exchange.model.InvariantStoreEntryLocation;
import org.sosy_lab.cpachecker.util.invariantwitness.exchange.model.InvariantStoreEntryLoopInvariant;
import org.sosy_lab.cpachecker.util.invariantwitness.exchange.model.InvariantStoreEntryMetadata;

/**
 * Class to export invariants in the invariant-witness format.
 *
 * <p>The class exports invariants by calling {@link #exportInvariantWitness(InvariantWitness)}. The
 * invariants are exported in the invariant-witness format. The export requires IO. Consider calling
 * it in a separate thread.
 */
@Options(prefix = "invariantStore.export")
public final class InvariantWitnessWriter {
  private final ListMultimap<String, Integer> lineOffsetsByFile;
  private final LogManager logger;
  private final ObjectMapper mapper;

  @Option(secure = true, description = "The directory where the invariants are stored.")
  @FileOption(FileOption.Type.OUTPUT_DIRECTORY)
  private Path outDir = Paths.get("invariantWitnesses");

  private InvariantWitnessWriter(
      Configuration pConfig, LogManager pLogger, ListMultimap<String, Integer> pLineOffsetsByFile)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = Objects.requireNonNull(pLogger);
    lineOffsetsByFile = ArrayListMultimap.create(pLineOffsetsByFile);
    mapper =
        new ObjectMapper(
            YAMLFactory.builder()
                .disable(Feature.WRITE_DOC_START_MARKER, Feature.SPLIT_LINES)
                .build());
  }

  /**
   * Returns a new instance of this class. The instance is configured according to the given config.
   *
   * @param pConfig Configuration with which the instance shall be created
   * @param pCFA CFA representing the program of the invariants that the instance writes
   * @param pLogger Logger
   * @return Instance of this class
   * @throws InvalidConfigurationException if the configuration is (semantically) invalid
   * @throws IOException if the program files can not be accessed (access is required to translate
   *     the location mapping)
   */
  public static InvariantWitnessWriter getWriter(
      Configuration pConfig, CFA pCFA, LogManager pLogger)
      throws InvalidConfigurationException, IOException {
    return new InvariantWitnessWriter(
        pConfig, pLogger, InvariantStoreUtil.getLineOffsetsByFile(pCFA.getFileNames()));
  }

  /**
   * Exports the given invariant witness in the invariant-witness format through the configured
   * channel. The export is (in most cases) an IO operation and thus expensive and might be
   * blocking.
   *
   * @param invariantWitness Witness to export
   * @throws IOException If writing the witness is not possible
   * @throws IllegalArgumentException If the invariant witness is (semantically - according to the
   *     definition of the invariant-witness format) invalid.
   */
  public void exportInvariantWitness(InvariantWitness invariantWitness) throws IOException {
    UUID uuid = UUID.randomUUID();
    Path outFile = outDir.resolve(uuid + ".invariantwitness.yaml");

    logger.log(Level.INFO, "Exporting invariant", uuid);
    String entry = invariantWitnessToYamlEntry(invariantWitness);
    try (Writer writer = IO.openOutputFile(outFile, Charset.defaultCharset())) {
      writer.write(entry);
    }
  }

  private String invariantWitnessToYamlEntry(InvariantWitness invariantWitness) throws IOException {
    final InvariantStoreEntryMetadata metadata = new InvariantStoreEntryMetadata();

    final String fileName = invariantWitness.getLocation().getFileName();
    final int lineNumber = invariantWitness.getLocation().getStartingLineInOrigin();
    final int lineOffset = lineOffsetsByFile.get(fileName).get(lineNumber - 1);
    final int offsetInLine = invariantWitness.getLocation().getNodeOffset() - lineOffset;

    InvariantStoreEntryLocation location =
        new InvariantStoreEntryLocation(
            fileName,
            "file_hash",
            lineNumber,
            offsetInLine,
            invariantWitness.getNode().getFunctionName());

    InvariantStoreEntryLoopInvariant invariant =
        new InvariantStoreEntryLoopInvariant(
            invariantWitness.getFormula().toString(), "assertion", "C");

    InvariantStoreEntry entry =
        new InvariantStoreEntry("loop_invariant", metadata, location, invariant);

    return mapper.writeValueAsString(List.of(entry));
  }
}
