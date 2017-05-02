package experiments;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.sparse.ResidueInteractionGraph;

public class TestGraphDrawAPI {
	
	public static void main(String[] args)
	{
        EnvironmentVars.assignTemplatesToStruct = true;
        ConfigFileParser cfp;
        String PDBFileLocation = "test/4NPD/4NPD.pdb";
        
        String[] testArgs = new String[]{"-c", "test/4NPD/KStar.cfg", "Dummy command", "test/4NPD/DEEFull.cfg", "test/4NPD/SystemFull.cfg"};
        cfp = new ConfigFileParser(testArgs);//args 1, 3+ are configuration files
        cfp.loadData();
        String runName = cfp.getParams().getValue("runName");
    	SearchProblem problem = cfp.getSearchProblem();
    	EnergyFunction efunction = problem.fullConfE;
    	ConfSpace conformationSpace = problem.confSpace;
    	ResidueInteractionGraph graph = ResidueInteractionGraph.generateGraph(problem.confSpace.m.residues, 
    			problem.confSpace.m, problem, efunction);
    	graph.computeEdgeBounds(problem, efunction);
    	graph.printStatistics();
    	graph.applyEnergyCutoff(0.5, problem, efunction);
    	graph.applyDistanceCutoff(12, problem, efunction);
    	SparseGraphVisualizer visualizer = new SparseGraphVisualizer(graph);
	}

}
