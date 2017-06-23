package edu.duke.cs.osprey.sparse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.ConfSpaceConstrainer;
import edu.duke.cs.osprey.confspace.RC;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;

public class Subproblem {
	private Set<Integer> LSet;
	private Set<Integer> lambdaSet;
	private Set<Integer> MSet;
	List<ConformationProcessor> processors = new ArrayList<>();
	public Subproblem leftSubproblem;
	public Subproblem rightSubproblem;
	private ConfSpace localConfSpace;
	
	public Subproblem (ConfSpace superSpace, TreeNode sparseTree, RCTuple initialConf) {
		localConfSpace = ConfSpaceConstrainer.constrainConfSpace(initialConf, superSpace);
	}


	
	public void addConformationProcessor(ConformationProcessor processor)
	{
		processors.add(processor);
	}

	public BigInteger getTotalConformations()
	{
		return localConfSpace.getNumConformations();
	}

	public void preprocess () {
		int[] currentConf = new int[localConfSpace.numPos];
		recursivelyProcessTuples(0,currentConf);
	}



	private void recursivelyProcessTuples (int position, int[] currentConf) {
		if(position >= localConfSpace.numPos)
		{
			//System.out.println("Process conformation:"+printConf(currentConf));
			RCTuple confTuple = new RCTuple(currentConf);
			for(ConformationProcessor proc : processors)
			{
				proc.processConformation(confTuple);
			}
			return;
		}
		ArrayList<RC> RCList = localConfSpace.posFlex.get(position).RCs;
		for(int i = 0; i < RCList.size(); i++)
		{
			currentConf[position] = i;
			recursivelyProcessTuples(position+1, currentConf);
		}
	}



	private String printConf (int[] currentConf) {
		String output = "(";
		for(int i = 0; i < currentConf.length-1; i++)
		{
			output+=i+":"+currentConf[i]+", ";
		}
		output = output+(currentConf.length-1)+":"+currentConf[currentConf.length-1]+")";
		return output;
	}

}
