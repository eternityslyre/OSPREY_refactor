package edu.duke.cs.osprey.tests;

import static org.junit.Assert.*;
import java.math.BigInteger;
import org.junit.Test;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.ConfSpaceConstrainer;
import edu.duke.cs.osprey.confspace.PositionConfSpace;
import edu.duke.cs.osprey.confspace.RC;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.sparse.BranchDecomposedProblem;
import edu.duke.cs.osprey.sparse.BranchTree;
import edu.duke.cs.osprey.sparse.ConformationProcessor;
import edu.duke.cs.osprey.sparse.ResidueInteractionGraph;
import edu.duke.cs.osprey.sparse.SparseKStarScoreEvaluator;
import edu.duke.cs.osprey.sparse.Subproblem;
import edu.duke.cs.osprey.sparse.TreeNode;
import edu.duke.cs.osprey.tools.branchdecomposition.BranchDecomposition;
import junit.framework.TestCase;

public class TestSparseAlgorithms  extends TestCase {

	ConfigFileParser cfp;
	String PDBFileLocation = "test/4NPD/4NPD.pdb";
	SearchProblem problem;
	protected void setUp () throws Exception {
		super.setUp();


		String[] testArgs = new String[]
				{"-c", "test/1CC8/KStar.cfg", "Dummy command", 
						"test/1CC8/DEESparse.cfg", "test/1CC8/SystemSparse.cfg"};
		cfp = new ConfigFileParser(testArgs);//args 1, 3+ are configuration files
		cfp.loadData();
		problem = cfp.getSearchProblem();
		String runName = cfp.getParams().getValue("runName");
		String graphFileName = "test/1CC8/"+runName;
		String bdFileName = "test/1CC8/"+runName+"_bd";
		
//		EnergyFunction efunction = problem.fullConfE;
//		ConfSpace conformationSpace = problem.confSpace;
//		ResidueInteractionGraph graph = ResidueInteractionGraph.generateCompleteGraph(problem);
//		//graph.applyEnergyCutoff(0.2, problem, efunction);
//		graph.computeEdgeBounds(problem, efunction);
//		graph.printStatistics();
//		graph.applyEnergyCutoff(0.2, problem, efunction);
//		graph.writeGraph(graphFileName);
//
//		String[] args = new String[]{graphFileName, bdFileName};
//		long startBD = System.currentTimeMillis();
//		BranchDecomposition.main(args);
//		long endBD = System.currentTimeMillis();
//		long BDTime = endBD - startBD;
//
//		System.out.println("Branch Decomposition generation time: "+BDTime);
//		long start = System.currentTimeMillis();
//		System.out.println("Branch Decomposition generated. Calculating GMEC...");



//		long end = System.currentTimeMillis();
//		long time = end - start;
//		System.out.println("Total time BD generation time taken in ms: "+time);
	}

	protected void tearDown () throws Exception {
		super.tearDown();
	}

	/***
	 * This test determines if a constrained conformation space is working correctly. It will fail the moment
	 * confSpaces have the wrong size.
	 */
	@Test
	public void testConstrainConfSpace()
	{
		RCTuple initialConf = new RCTuple();
		for(int i = 0; i < problem.confSpace.numPos/2; i++)
		{
			int RCAssigned = (int)(Math.random()*problem.confSpace.posFlex.get(i).RCs.size());
			initialConf = initialConf.addRC(i, RCAssigned);
		}
		ConfSpace localConfSpace = ConfSpaceConstrainer.constrainConfSpace(initialConf, problem.confSpace);
		if(localConfSpace.numPos != problem.confSpace.numPos)
		{
			System.err.println("Test not designed to handle conformation spaces"
					+" of different sizes. (It should. Fix this.)");
			System.exit(-1);
		}

		// Make sure the ConfSpace is properly constrained.
		for(int tupleIndex = 0; tupleIndex < initialConf.size(); tupleIndex++)
		{
			int RCPosition = initialConf.pos.get(tupleIndex);
			int RCConf = initialConf.RCs.get(tupleIndex);
			RC confSpaceRC = problem.confSpace.posFlex.get(tupleIndex).RCs.get(RCConf);

			PositionConfSpace constrainedConfSpace = localConfSpace.posFlex.get(tupleIndex);
			assert(constrainedConfSpace.RCs.size() != 1);
			RC constrainedRC = constrainedConfSpace.RCs.get(0);
			assert(constrainedRC.equals(confSpaceRC));
			if(!constrainedRC.equals(confSpaceRC))
			{
				System.err.println("ERROR: Residue "+RCPosition
						+" doesn't match the constrained space: "
						+constrainedConfSpace.RCs.get(0).AAType+"-"+constrainedConfSpace.RCs.get(0).rotNum
						+" isn't "+confSpaceRC.AAType+"-"+confSpaceRC.rotNum);
				System.exit(-1);
			}
			System.out.println("Residue "+RCPosition
					+" matches the constrained space: "
					+constrainedConfSpace.RCs.get(0).AAType+"-"+constrainedConfSpace.RCs.get(0).rotNum
					+" is "+confSpaceRC.AAType+"-"+confSpaceRC.rotNum);
		}
		System.out.println("Test complete.");
	}

	@Test
	public void testSubproblemExhaustiveEnumeration()
	{

		String runName = cfp.getParams().getValue("runName");
		SearchProblem problem = cfp.getSearchProblem();
		EnergyFunction efunction = problem.fullConfE;
		ConfSpace conformationSpace = problem.confSpace;

		String[] args = new String[]{"test/1CC8/"+runName, runName+"_bd"};
		long startBD = System.currentTimeMillis();
		BranchDecomposition.main(args);
		long endBD = System.currentTimeMillis();
		long BDTime = endBD - startBD;

		System.out.println("Branch Decomposition generation time: "+BDTime);
		long start = System.currentTimeMillis();
		System.out.println("Branch Decomposition generated. Calculating GMEC...");

		String bdFile = "test/1CC8/"+runName+"_bd";

		BranchTree tree = new BranchTree(bdFile, problem);
		TreeNode root = tree.getRoot();

		RCTuple initialConf = new RCTuple();
		for(int i = 0; i < problem.confSpace.numPos/2; i++)
		{
			int RCAssigned = (int)(Math.random()*problem.confSpace.posFlex.get(i).RCs.size());
			initialConf = initialConf.addRC(i, RCAssigned);
		}
		Subproblem sparseProblem = new Subproblem(conformationSpace, root, initialConf);
		ConformationCounter counter = new ConformationCounter();
		sparseProblem.addConformationProcessor(counter);
		sparseProblem.preprocess();
		BigInteger totalConfs = conformationSpace.getNumConformations();
		BigInteger subproblemConfs = sparseProblem.getTotalConformations();
		if(!counter.numConfs.equals(subproblemConfs))
		{
			System.err.println("Conformations not processed in subproblem: "+counter.numConfs+" != "+subproblemConfs);
		}
		System.out.println("Num confs processed: "+counter.numConfs);
		System.out.println("Num subproblem confs possible: "+subproblemConfs);
		System.out.println("Num confs possible: "+totalConfs);
	}
	
	@Test
	public void testFullTreeExhaustiveEnumeration()
	{

		String runName = cfp.getParams().getValue("runName");
		SearchProblem problem = cfp.getSearchProblem();
		EnergyFunction efunction = problem.fullConfE;
		ConfSpace conformationSpace = problem.confSpace;

		String[] args = new String[]{"test/1CC8/"+runName, runName+"_bd"};
		long startBD = System.currentTimeMillis();
		BranchDecomposition.main(args);
		long endBD = System.currentTimeMillis();
		long BDTime = endBD - startBD;

		System.out.println("Branch Decomposition generation time: "+BDTime);
		long start = System.currentTimeMillis();
		System.out.println("Branch Decomposition generated. Calculating GMEC...");

		String bdFile = "test/1CC8/"+runName+"_bd";

		BranchTree tree = new BranchTree(bdFile, problem);
		TreeNode root = tree.getRoot();

		RCTuple initialConf = new RCTuple();
		for(int i = 0; i < problem.confSpace.numPos/2; i++)
		{
			int RCAssigned = (int)(Math.random()*problem.confSpace.posFlex.get(i).RCs.size());
			initialConf = initialConf.addRC(i, RCAssigned);
		}
		Subproblem sparseProblem = new Subproblem(conformationSpace, root, initialConf);
		ConformationCounter counter = new ConformationCounter();
		sparseProblem.addConformationProcessor(counter);
		sparseProblem.preprocess();
		BigInteger totalConfs = conformationSpace.getNumConformations();
		BigInteger subproblemConfs = sparseProblem.getTotalConformations();
		if(!counter.numConfs.equals(subproblemConfs))
		{
			System.err.println("Conformations not processed in subproblem: "+counter.numConfs+" != "+subproblemConfs);
		}
		System.out.println("Num confs processed: "+counter.numConfs);
		System.out.println("Num subproblem confs possible: "+subproblemConfs);
		System.out.println("Num confs possible: "+totalConfs);
	}

	@Test
	public void testComputeKStarScore()
	{

	}


	@Test
	public void testEnumerateConformations() throws Exception
	{

	}

	@Test
	public void testEnumerateConformationsByKStarScore() throws Exception
	{

	}
	
	private class ConformationCounter implements ConformationProcessor
	{
		BigInteger numConfs = BigInteger.ZERO;
		@Override
		public void processConformation (RCTuple conformation) {
			numConfs = numConfs.add(BigInteger.ONE);
		}
		
	}
}
