package edu.duke.cs.osprey.tests;

import org.junit.Test;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.restypes.PositionSpecificRotamerLibrary;
import edu.duke.cs.osprey.sparse.ResidueInteractionGraph;
import junit.framework.TestCase;

public class TestSparseGraphs extends TestCase {

    ConfigFileParser cfp;
    String PDBFileLocation = "test/4NPD/4NPD.pdb";
    protected void setUp () throws Exception {
        super.setUp();
        
        EnvironmentVars.assignTemplatesToStruct = true;

        
        String[] testArgs = new String[]{"-c", "test/4NPD/KStar.cfg", "Dummy command", "test/4NPD/DEEFull.cfg", "test/4NPD/SystemFull.cfg"};
        cfp = new ConfigFileParser(testArgs);//args 1, 3+ are configuration files
        cfp.loadData();
    }

    protected void tearDown () throws Exception {
        super.tearDown();
    }
    
    
    @Test
    public void testComputeSparseGraphs() throws Exception
    {
        String runName = cfp.getParams().getValue("runName");
    	SearchProblem problem = cfp.getSearchProblem();
    	EnergyFunction efunction = problem.fullConfE;
    	ConfSpace conformationSpace = problem.confSpace;
    	ResidueInteractionGraph graph = ResidueInteractionGraph.generateCompleteGraph(conformationSpace.numPos);
    	//graph.applyEnergyCutoff(0.2, problem, efunction);
    	graph.computeEdgeBounds(problem, efunction);
    	graph.printStatistics();
    	graph.applyEnergyCutoff(0.2, problem, efunction);
    	//graph.writeGraph(runName);
    }
    
    @Test
    public void testBranchDecomposition()
    {
        String runName = cfp.getParams().getValue("runName");
        String[] args = new String[]{runName, runName+"_bd"};
        long startBD = System.currentTimeMillis();
        BranchDecomposition.BranchDecomposition.main(args);
		long endBD = System.currentTimeMillis();
		long BDTime = endBD - startBD;

        System.out.println("Branch Decomposition generation time: "+BDTime);
        long start = System.currentTimeMillis();
        System.out.println("Branch Decomposition generated. Calculating GMEC...");
        
        long end = System.currentTimeMillis();
        long time = end - start;
        System.out.println("Total time enumeration taken in ms: "+time);
    }
    

}
