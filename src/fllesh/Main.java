package fllesh;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.dom.ICodeReaderFactory;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.CodeReader;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.ParserFactory;
import org.eclipse.core.runtime.CoreException;

import cmdline.stubs.CLanguage;
import cmdline.stubs.StubCodeReaderFactory;
import cmdline.stubs.StubScannerInfo;

import com.google.common.base.Joiner;

import cfa.CFABuilder;
import cfa.CFATopologicalSort;
import cfa.DOTBuilder;
import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFAFunctionDefinitionNode;
import cfa.objectmodel.CFANode;
import cfa.objectmodel.c.CallToReturnEdge;
import cfa.objectmodel.c.FunctionCallEdge;
import cfa.objectmodel.c.FunctionDefinitionNode;
import cfa.objectmodel.c.ReturnEdge;
import cfa.objectmodel.c.StatementEdge;

import common.configuration.Configuration;
import compositeCPA.CompositeCPA;

import cpa.art.ARTCPA;
import cpa.art.ARTStatistics;
import cpa.common.LogManager;
import cpa.common.ReachedElements;
import cpa.common.CPAcheckerResult.Result;
import cpa.common.algorithm.CEGARAlgorithm;
import cpa.common.algorithm.CPAAlgorithm;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.CPAFactory;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.Statistics;
import cpa.location.LocationCPA;
import cpa.observeranalysis.ObserverAutomatonCPA;
import cpa.symbpredabsCPA.SymbPredAbsCPA;
import exceptions.CPAException;
import fllesh.cpa.edgevisit.EdgeVisitCPA;
import fllesh.ecp.reduced.ObserverAutomatonCreator;
import fllesh.ecp.reduced.ObserverAutomatonTranslator;
import fllesh.ecp.reduced.Pattern;
import fllesh.fql.fllesh.util.CFATraversal;
import fllesh.fql.fllesh.util.CFAVisitor;
import fllesh.fql.fllesh.util.CPAchecker;
import fllesh.fql.fllesh.util.Cilly;
import fllesh.fql.frontend.ast.filter.FunctionCall;
import fllesh.fql.frontend.ast.filter.Identity;
import fllesh.fql.frontend.ast.filter.SetMinus;
import fllesh.fql2.ast.Edges;
import fllesh.fql2.ast.coveragespecification.CoverageSpecification;
import fllesh.fql2.ast.coveragespecification.Quotation;
import fllesh.fql2.ast.pathpattern.PathPattern;
import fllesh.fql2.ast.pathpattern.Repetition;

public class Main {

  private static Configuration createConfiguration(String pSourceFile, String pPropertiesFile) {
    return createConfiguration(Collections.singletonList(pSourceFile), pPropertiesFile);
  }
  
  private static Configuration createConfiguration(List<String> pSourceFiles, String pPropertiesFile) {
    Map<String, String> lCommandLineOptions = new HashMap<String, String>();    

    lCommandLineOptions.put("analysis.programNames", Joiner.on(", ").join(pSourceFiles));
    //lCommandLineOptions.put("output.path", "test/output");
    
    Configuration lConfiguration = null;
    try {
      lConfiguration = new Configuration(pPropertiesFile, lCommandLineOptions);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return lConfiguration;
  }
  
  private static File createPropertiesFile() {
    File lPropertiesFile = null;
    
    try {
      
      lPropertiesFile = File.createTempFile("fllesh.", ".properties");
      lPropertiesFile.deleteOnExit();
      
      PrintWriter lWriter = new PrintWriter(new FileOutputStream(lPropertiesFile));
      // we do not use a fixed error location (error label) therefore
      // we do not want to remove parts of the CFA
      lWriter.println("cfa.removeIrrelevantForErrorLocations = false");
      
      lWriter.println("log.consoleLevel = ALL");
      
      lWriter.println("analysis.traversal = topsort");
      
      // we want to use CEGAR algorithm
      lWriter.println("analysis.useRefinement = true");    
      lWriter.println("cegar.refiner = " + cpa.symbpredabsCPA.SymbPredAbsRefiner.class.getCanonicalName());

      lWriter.close();
      
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return lPropertiesFile;
  }

  private static File createPropertiesFile(File pObserverAutomatonFile) {
    File lPropertiesFile = Main.createPropertiesFile();
    
    // append configuration for observer automaton
    PrintWriter lWriter;
    try {
      
      lWriter = new PrintWriter(new FileOutputStream(lPropertiesFile, true));
      
      lWriter.println("observerAnalysis.inputFile = " + pObserverAutomatonFile.getAbsolutePath());
      lWriter.println("observerAnalysis.dotExportFile = test/output/observerAutomatonExport.dot");
      lWriter.close();
      
      return lPropertiesFile;
      
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return null;
  }
  
  /**
   * @param pArguments
   * @throws Exception 
   */
  public static void main(String[] pArguments) throws Exception {
    assert(pArguments != null);
    assert(pArguments.length > 1);
    
    // check cilly invariance of source file, i.e., is it changed when preprocessed by cilly?
    Cilly lCilly = new Cilly();
    
    String lSourceFileName = pArguments[1];
    
    if (!lCilly.isCillyInvariant(lSourceFileName)) {
      File lCillyProcessedFile = lCilly.cillyfy(pArguments[1]);
      
      lSourceFileName = lCillyProcessedFile.getAbsolutePath();
      
      System.err.println("WARNING: Given source file is not CIL invariant ... did preprocessing!");
    }
    
    File lPropertiesFile = Main.createPropertiesFile();
    Configuration lConfiguration = Main.createConfiguration(lSourceFileName, lPropertiesFile.getAbsolutePath());

    LogManager lLogManager = new LogManager(lConfiguration);
      
    CPAchecker lCPAchecker = new CPAchecker(lConfiguration, lLogManager);
    
    
    CFAFunctionDefinitionNode lMainFunction = lCPAchecker.getMainFunction();
    
    

    
    
    
    final fllesh.fql2.ast.coveragespecification.Translator lCoverageSpecificationTranslator = new fllesh.fql2.ast.coveragespecification.Translator(lMainFunction);
    
    FunctionCall lFunctionCallFilter = new FunctionCall("f");
    SetMinus lSetMinus = new SetMinus(Identity.getInstance(), lFunctionCallFilter);
    //PathPattern lPrefixPattern = new Repetition(new Edges(Identity.getInstance()));
    PathPattern lPrefixPattern = new Repetition(new Edges(lSetMinus));
    Quotation lQuotation = new Quotation(lPrefixPattern);
    CoverageSpecification lTarget = new Edges(lFunctionCallFilter);
    
    CoverageSpecification lIdRepetition = new Quotation(new Repetition(new Edges(Identity.getInstance())));
    
    CoverageSpecification lSpecification = new fllesh.fql2.ast.coveragespecification.Concatenation(lQuotation, new fllesh.fql2.ast.coveragespecification.Concatenation(lTarget, lIdRepetition));
    
    Set<Pattern> lTestGoals = lCoverageSpecificationTranslator.translate(lSpecification);
    
    System.out.println(lTestGoals);

    Pattern lTestGoal = null; 
    
    for (Pattern lGoal : lTestGoals) {
      lTestGoal = lGoal;
      break;
    }
    
    System.out.println(lTestGoal);
    
    
    /** Generating a wrapper start up method */
    Wrapper lWrapper = new Wrapper((FunctionDefinitionNode)lMainFunction, lCPAchecker.getCFAMap(), lCoverageSpecificationTranslator.getAnnotations(),lLogManager);
    
    String lAlphaId = lCoverageSpecificationTranslator.getAnnotations().getId(lWrapper.getAlphaEdge());
    String lOmegaId = lCoverageSpecificationTranslator.getAnnotations().getId(lWrapper.getOmegaEdge());
    
    // TODO: for every test goal (i.e., pattern) create an automaton and check reachability
    
    
    
    File lAutomatonFile = File.createTempFile("fllesh.", ".oa");
    lAutomatonFile.deleteOnExit();
    
    PrintStream lObserverAutomaton = new PrintStream(new FileOutputStream(lAutomatonFile));
    lObserverAutomaton.println(ObserverAutomatonTranslator.translate(lTestGoal, "Goal", lAlphaId, lOmegaId));
    lObserverAutomaton.close();
    
    
    
    
    File lExtendedPropertiesFile = Main.createPropertiesFile(lAutomatonFile);
    Configuration lExtendedConfiguration = Main.createConfiguration(lSourceFileName, lExtendedPropertiesFile.getAbsolutePath());

    EdgeVisitCPA.Factory lFactory = new EdgeVisitCPA.Factory(lCoverageSpecificationTranslator.getAnnotations());
    lFactory.setConfiguration(lExtendedConfiguration);
    lFactory.setLogger(lLogManager);
    ConfigurableProgramAnalysis lEdgeVisitCPA = lFactory.createInstance();
    
    CPAFactory lAutomatonFactory = ObserverAutomatonCPA.factory();
    lAutomatonFactory.setConfiguration(lExtendedConfiguration);
    lAutomatonFactory.setLogger(lLogManager);
    ConfigurableProgramAnalysis lObserverCPA = lAutomatonFactory.createInstance();

    CPAFactory lLocationCPAFactory = LocationCPA.factory();
    ConfigurableProgramAnalysis lLocationCPA = lLocationCPAFactory.createInstance();
    
    CPAFactory lSymbPredAbsCPAFactory = SymbPredAbsCPA.factory();
    lSymbPredAbsCPAFactory.setConfiguration(lConfiguration);
    lSymbPredAbsCPAFactory.setLogger(lLogManager);
    ConfigurableProgramAnalysis lSymbPredAbsCPA = lSymbPredAbsCPAFactory.createInstance();

    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<ConfigurableProgramAnalysis>();    
    lComponentAnalyses.add(lLocationCPA);
    lComponentAnalyses.add(lEdgeVisitCPA);
    lComponentAnalyses.add(lSymbPredAbsCPA);
    lComponentAnalyses.add(lObserverCPA);

    // create composite CPA
    CPAFactory lCPAFactory = CompositeCPA.factory();  
    lCPAFactory.setChildren(lComponentAnalyses);
    lCPAFactory.setConfiguration(lConfiguration);
    lCPAFactory.setLogger(lLogManager);
    ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

    // create ART CPA
    CPAFactory lARTCPAFactory = ARTCPA.factory();    
    lARTCPAFactory.setChild(lCPA);
    lARTCPAFactory.setConfiguration(lConfiguration);
    lARTCPAFactory.setLogger(lLogManager);
    ConfigurableProgramAnalysis lARTCPA = lARTCPAFactory.createInstance();
    
    
    
    CEGARAlgorithm lAlgorithm = new CEGARAlgorithm(new CPAAlgorithm(lARTCPA, lLogManager), lConfiguration, lLogManager);
    
    Statistics lARTStatistics = new ARTStatistics(lConfiguration, lLogManager);
    Set<Statistics> lStatistics = new HashSet<Statistics>();
    lStatistics.add(lARTStatistics);
    lAlgorithm.collectStatistics(lStatistics);
    
    AbstractElement initialElement = lARTCPA.getInitialElement(lWrapper.getEntry());
    Precision initialPrecision = lARTCPA.getInitialPrecision(lWrapper.getEntry());
          
    ReachedElements lReachedElements = new ReachedElements(ReachedElements.TraversalMethod.TOPSORT, true);
    lReachedElements.add(initialElement, initialPrecision);
    
    try {
      lAlgorithm.run(lReachedElements, true);      
    } catch (CPAException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
        
    for (AbstractElement reachedElement : lReachedElements) {
      // TODO determine whether ERROR element was reached
      
      System.out.println(reachedElement);
    }
    
    PrintWriter lStatisticsWriter = new PrintWriter(System.out);

    lARTStatistics.printStatistics(lStatisticsWriter, Result.SAFE, lReachedElements);
  }

}

