package edu.duke.cs.osprey.sparse;

import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;

public class Subproblem {
	private Subproblem leftChild;
	private Subproblem rightChild;
	private Iterator<RCTuple> solutionEnumerator;
	private Set<Integer> lambdaSet;
	private Set<Integer> L_Set;
	private Solution nextBestSolution;
	private List<Solution> solutions;
	
	public Solution nextBestSolution()
	{
		if(nextBestSolution == null)
		{
			nextBestSolution = new Solution(solutionEnumerator.next());
		}
		Solution output = nextBestSolution.copy();
		updateSolution(nextBestSolution);
		solutions.add(nextBestSolution);
		return output;
	}
	
	public boolean hasMoreSolutions()
	{
		return nextBestSolution != null || solutionEnumerator.hasNext();
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
