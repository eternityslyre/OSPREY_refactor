package edu.duke.cs.osprey.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import edu.duke.cs.osprey.astar.PairwiseConfTree;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.dof.deeper.DEEPerSettings;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.EnergyMatrixCalculator;
import edu.duke.cs.osprey.ematrix.epic.EPICSettings;
import edu.duke.cs.osprey.energy.MultiTermEnergyFunction;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import edu.duke.cs.osprey.tools.ObjectIO;
import edu.duke.cs.osprey.tools.Stopwatch;

public class ConfTreeProfiling {
	
	public static void main(String[] args)
	throws Exception {
		
		// check the cwd
		String path = new File("").getAbsolutePath();
		if (!path.endsWith("test/DAGK")) {
			throw new Error("This profiler was designed to run in the test/DAGK folder\n\tcwd: " + path);
		}

		// load configuration
		ConfigFileParser cfp = new ConfigFileParser(new String[] {"-c", "KStar.cfg"});
		cfp.loadData();
		
		// multi-thread the energy function
		MultiTermEnergyFunction.setNumThreads(4);
		
		// init a conf space with lots of flexible residues, but no mutations
		//final int NumFlexible = 27;
		//final int NumFlexible = 34;
		final int NumFlexible = 55;
		ArrayList<String> flexRes = new ArrayList<>();
		ArrayList<ArrayList<String>> allowedAAs = new ArrayList<>();
		for (int i=0; i<NumFlexible; i++) {
			flexRes.add(Integer.toString(i + 1));
			allowedAAs.add(new ArrayList<String>());
		}
		boolean addWt = true;
		boolean doMinimize = false;
		boolean useEpic = false;
		boolean useTupleExpansion = false;
		boolean useEllipses = false;
		boolean useERef = false;
		boolean addResEntropy = false;
		boolean addWtRots = true;
		boolean usePDBAlternatesAsRotamers = false;
		ArrayList<String[]> moveableStrands = new ArrayList<String[]>();
		ArrayList<String[]> freeBBZones = new ArrayList<String[]>();
		SearchProblem search = new SearchProblem(
			"energyMatrixProfiling",
			"2KDC.P.forOsprey.pdb", 
			flexRes, allowedAAs, addWt, doMinimize, useEpic, new EPICSettings(), useTupleExpansion,
			new DEEPerSettings(), moveableStrands, freeBBZones, useEllipses, useERef, addResEntropy, addWtRots,
			usePDBAlternatesAsRotamers
		);
		
		// compute the energy matrix
		File ematFile = new File(String.format("emat.%d.dat", NumFlexible));
		if (ematFile.exists()) {
			System.out.println("\nReading energy matrix...");
			search.emat = (EnergyMatrix)ObjectIO.readObject(ematFile.getAbsolutePath(), true);
		}
		if (search.emat == null) {
			System.out.println("\nComputing energy matrix...");
			EnergyMatrixCalculator emCalc = new EnergyMatrixCalculator(search.confSpace, search.shellResidues, useERef, addResEntropy);
			emCalc.calcPEM();
			search.emat = emCalc.getEMatrix();
			ObjectIO.writeObject(search.emat, ematFile.getAbsolutePath());
		}
		
		// don't bother with pruning, set all to unpruned
		search.pruneMat = new PruningMatrix(search.confSpace, search.emat.getPruningInterval());
		
		// init the conformation search
		//ConfTree<?> tree = ConfTree.makeFull(search);
		PairwiseConfTree tree = new PairwiseConfTree(search);
		tree.initProgress(search.confSpace.numPos);
		
		// notation below (trialN values in milliseconds):
		// numFlexPos: [trial1, trial2, trial2]
		
		// 2016-05-03
		// BEFORE OPTIMIZATIONS
		// 27: [36503, 37969, 36664]
		
		// after roughly 2x energy matrix read speedup
		// 27: [25663, 25565, 25646] => 1.45x speedup over benchmark
		
		// optimize ConfTree a bit
		// 27: [18446, 18387, 18470] => 2.01x speedup over benchmark
		
		// implement lazy instantiation of higher-order terms
		// 27: [12963, 13017, 12885] => 2.86x speedup over benchmark
		
		// implement parallel expansion of nodes (2 threads)
		// 27x2: [14847, 15117, 15544] => rather disappointing, really
		// this workload must be memory-bound, so parallel computations hurt more than help
		
		// NB: turning off dynamic A* bumped the run time to at least 5 minutes
		// I stopped waiting after that
		// dynamic A* makes a HUGE difference!!
		
		// 2016-05-04
		// another day, another something... dunno what it is. re-doing benchmarks
		
		// after 2x energy matrix speedup
		// 27: [31356, 31728, 31215]
		
		// Bah. I'm lazy and don't want to re-benchmark the before-optimization state
		// let's just assume this weird daily thing is linear and say we're 1.23x slower today
		// so: pre-opt should be:
		// 27: [44777, 46575, 44975]
		// which means the energy matrix improvements are still about a 1.45x speedup
		// so that checks out
		
		// yesterday's ConfTree optimizations:
		// 27: [15723, 15778, 15897] => 2.88x speedup over benchmark
		
		// today's ConfTree optimizations:
		// 27: [2619, 2665, 2663] => 17.15x speedup over benchmark!! =D
		
		// NOTE: addWTRots bug fix on 2016-05-05 changed energy matrix values!
		// so all benchmarks after that are incomparable to benchmarks before it
		// also, the newer progress reporting does impose a small performance penalty
		
		// sooo..... let's start some new benchmarks
		// 2016-05-13
		// current state of code:
		// 34:   [22759, 22687, 22572]
		
		// after minor optimizations, haven't dropped the big guns just yet... =P
		// 34:   [18846, 18921, 18962] => 1.20x speedup over benchmark
		
		// 2016-05-14 (didn't bother re-benchmarking today)
		// after differential score calculations
		// 34:   [1325, 1326, 1337] => 17.06x speedup over benchmark!! =D
		
		// this test run is too short now... need something longer
		// 55:   [24873, 24501, 25076]
		
		// 2016-05-15 (didn't bother re-benchmarking again)
		// after more minor optimizations, fixed memory usage
		// 55:   [19785, 20181, 20118] => 1.24x speedup over benchmark
		
		// optimize for memory usage at the nodes, hopefully this won't slow down too much
		// 55:   [20082, 20240, 20227] => about the same, not bad at all! =)
		
		System.out.println("\nFinding GMEC among " + tree.getNumConformations().doubleValue() + " conformations ...");
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
		int[] conf = tree.nextConf();
		stopwatch.stop();
		System.out.println("finished in " + stopwatch.getTime(TimeUnit.MILLISECONDS));
		System.out.println("conf:     " + Arrays.toString(conf));
		
		// TODO: check for accuracy, energy should be:
		// 27:   -260.91555715297517
		// 34:   -346.32024675046176
		// 55:   -514.1055956242977

		// make sure we still have the right answer!
		Map<Integer,int[]> expectedConfs = new TreeMap<>();
		expectedConfs.put(27, new int[] { 0, 6, 7, 0, 16, 0, 0, 6, 25, 6, 0, 0, 0, 0, 0, 0, 16, 2, 12, 1, 0, 15, 0, 1, 0, 0, 0 });
		expectedConfs.put(34, new int[] { 0, 6, 7, 0, 16, 0, 0, 6, 25, 6, 0, 0, 0, 0, 0, 0, 16, 2, 12, 1, 0, 15, 0, 1, 0, 0, 0, 0, 0, 0, 0, 29, 0, 0 });
		expectedConfs.put(55, new int[] { 0, 6, 7, 0, 16, 0, 0, 6, 25, 6, 0, 0, 0, 0, 0, 0, 16, 2, 12, 1, 0, 15, 0, 1, 0, 0, 0, 0, 0, 0, 0, 29, 0, 0, 1, 2, 1, 0, 0, 3, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
		checkConf(expectedConfs.get(NumFlexible), conf);
	}
	
	private static void checkConf(int[] expected, int[] observed) {
		if (!Arrays.equals(expected, observed)) {
			System.out.println("expected: " + Arrays.toString(expected));
			throw new Error("GMEC changed! Undo that \"optimization!\"");
		}
	}
}
