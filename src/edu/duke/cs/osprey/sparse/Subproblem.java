package edu.duke.cs.osprey.sparse;

import java.util.PriorityQueue;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;

public class Subproblem {
	private Subproblem leftChild;
	private Subproblem rightChild;
	private PriorityQueue<Solution> solutions;
	
	public Solution nextBestSolution()
	{
		Solution nextBestCombination = solutions.poll();
		Solution output = nextBestCombination.copy();
		updateSolution(nextBestCombination);
		solutions.add(nextBestCombination);
		return output;
	}

	private void updateSolution (Solution nextBestCombination) {
		// TODO Auto-generated method stub
		
	}
	
	public static Subproblem createSubproblem(SearchProblem fullProblem, RCTuple initialSolution)
	{
		SearchProblem subSearchProblem = new SearchProblem(fullProblem);
		constrainConformationSpace(initialSolution,subSearchProblem.confSpace);
		return null;
	}

	private static void constrainConformationSpace (RCTuple initialSolution, ConfSpace confSpace) {
		// TODO Auto-generated method stub
		//SearchProblem constrainedSearchProblem = new SearchProblem();
		
	}

}
