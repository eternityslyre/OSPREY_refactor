package edu.duke.cs.osprey.sparse;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleMatrix1D;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.RC;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.TermECalculator;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.minimization.CCDMinimizer;
import edu.duke.cs.osprey.minimization.MoleculeModifierAndScorer;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBFileWriter;
import edu.duke.cs.osprey.structure.Residue;

public class ResidueInteractionGraph {
	
	Set<Integer> vertices = new HashSet<>();
	Map<Integer,Set<Integer>> adjacencyMatrix = new HashMap<>();
	double distanceCutoff = 7; // distance cutoff, in angstroms
	double energyCutoff = 0; // energy cutoff, in kcal/mol
	
	public ResidueInteractionGraph()
	{
		
	}
	
	public static ResidueInteractionGraph generateCompleteGraph(int numResidues)
	{
		ResidueInteractionGraph outputGraph = new ResidueInteractionGraph();
		for(int i = 0; i < numResidues; i++)
		{
			outputGraph.addVertex(i);
			for(int j = 0; j < i; j++)
			{
				outputGraph.addEdge(i,j);
			}
		}
		return outputGraph;
	}
	
	public static ResidueInteractionGraph generateGraph(
			Set<Integer> residues, Molecule m,
			double distanceCutoff, double energyCutoff)
	{
		ResidueInteractionGraph graph = new ResidueInteractionGraph();
		graph.setMutableResidues(residues);
		graph.applyDistanceCutoff(distanceCutoff);
		graph.applyEnergyCutoff(energyCutoff);
		//graph.computeGraph(m);
		
		return graph;
	}
	
	public void addVertex(int vertex)
	{
		vertices.add(vertex);
	}
	
	public void addEdge(int v1, int v2)
	{
		int min = Math.min(v1,v2);
		int max = Math.max(v1, v2);
		if(!adjacencyMatrix.containsKey(min))
			adjacencyMatrix.put(min, new HashSet<>());
		adjacencyMatrix.get(min).add(max);
	}
	
	public void pruneEdge(int v1, int v2)
	{
		int min = Math.min(v1,v2);
		int max = Math.max(v1, v2);
		if(!adjacencyMatrix.containsKey(min))
			adjacencyMatrix.put(min, new HashSet<>());
		adjacencyMatrix.get(min).remove(max);
	}
	
	public boolean connected(int source, int target)
	{
		int min = Math.min(source,target);
		int max = Math.max(source,target);
		return adjacencyMatrix.containsKey(min) 
				&& adjacencyMatrix.get(min).contains(max);
	}
	
	public void applyDistanceCutoff(double cutoff)
	{
		distanceCutoff = cutoff;
	}
	
	public void applyEnergyCutoff(double cutoff)
	{
		energyCutoff = 0;
	}
	
	public void computeGraph(SearchProblem problem, EnergyFunction termE)
	{
		ConfSpace conformations = problem.confSpace;
		
		for(int i =0; i < vertices.size(); i++)
		{
			Residue resi = conformations.posFlex.get(i).res;
			double maxEnergy = Double.NEGATIVE_INFINITY;
			double minEnergy = Double.POSITIVE_INFINITY;
			double minDistance = Double.POSITIVE_INFINITY;
			for(int j = 0; j < conformations.posFlex.get(i).RCs.size(); j++)
			{
				for(int k = i+1; k < vertices.size(); k++)
				{
					Residue resj = conformations.posFlex.get(k).res;
					for(int l = 0; l < conformations.posFlex.get(k).RCs.size(); l++)
					{
						RCTuple conformationTuple = new RCTuple(i,j,k,l);
						MoleculeModifierAndScorer mof = new MoleculeModifierAndScorer(termE,conformations,conformationTuple);

			            DoubleMatrix1D bestDOFVals;

			            if(mof.getNumDOFs()>0){//there are continuously flexible DOFs to minimize
			                CCDMinimizer ccdMin = new CCDMinimizer(mof,true);
			                bestDOFVals = ccdMin.minimize();
			            }
			            else//molecule is already in the right, rigid conformation
			                bestDOFVals = DoubleFactory1D.dense.make(0);


			            double pairwiseEnergy = mof.getValue(bestDOFVals);
			            double distance = resi.distanceTo(resj);
			            minDistance = Math.min(distance, minDistance);
			            maxEnergy = Math.max(pairwiseEnergy,maxEnergy);
			            minEnergy = Math.min(pairwiseEnergy, minEnergy);
			            //System.out.println("Energy of ("+i+"-"+j+","+k+"-"+l+"):"+pairwiseEnergy);
			            //System.out.println("Distance between ("+i+"-"+j+","+k+"-"+l+"):"+distance);
			            	
					}
					double energyBounds = maxEnergy - minEnergy;
					if(energyBounds < energyCutoff || minDistance > distanceCutoff)
					{
						System.out.println("Pruning edge ("+i+","+k+")");
						pruneEdge(i,k);
					}
				}
			}

		}
				
	}
	
	
	public void setMutableResidues(Set<Integer> residues)
	{
		vertices.clear();
		for(Integer i : residues)
			addVertex(i);
		createCompleteGraph();
	}
	
	private void createCompleteGraph()
	{
		for(Integer i : vertices)
		{
			for(Integer j : vertices)
			{
				if(i!=j)
					addEdge(i,j);
			}
		}
	}

	public void writeGraph (String outputFileName) {
		// TODO Auto-generated method stub
		try
		{
			FileOutputStream fileOutputStream = new FileOutputStream(outputFileName);
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
					fileOutputStream);
			PrintStream printStream = new PrintStream(bufferedOutputStream);


			for(Integer i : adjacencyMatrix.keySet())
			{
				for(Integer j : adjacencyMatrix.get(i))
				{
					printStream.println("("+i+","+j+")");
				}
			}
			printStream.close();
		}        
		catch (IOException e) {
			System.out.println("ERROR: An io exception occurred while writing file "+outputFileName);
			System.exit(0);
		}
		catch ( Exception e ){
			System.out.println(e.toString());
			System.out.println("ERROR: An exception occurred while writing file");
			System.exit(0);
		}
	}

}
