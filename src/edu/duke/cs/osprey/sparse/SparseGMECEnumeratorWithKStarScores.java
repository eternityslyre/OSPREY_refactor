package edu.duke.cs.osprey.sparse;

public class SparseGMECEnumeratorWithKStarScores {

	private BranchDecomposedProblem sparseProblem;
	public SparseGMECEnumeratorWithKStarScores(BranchDecomposedProblem problem)
	{
		sparseProblem = problem;
	}
	
	public void enumerateSequences(int numSequences, boolean continuousRotamerBounds)
	{
		SparseKStarScoreEvaluator KSScoreEvaluator = new SparseKStarScoreEvaluator();
		SparseConformationEnumerator confEnumerator = new SparseConformationEnumerator();
		
	}
}
