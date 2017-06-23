package edu.duke.cs.osprey.confspace;

import java.util.ArrayList;
import java.util.List;
import edu.duke.cs.osprey.dof.DegreeOfFreedom;

public class ConfSpaceConstrainer {
	public static ConfSpace constrainConfSpace(RCTuple initialConf, ConfSpace superSpace)
	{
		ConfSpace constrainedSpace = ConfSpace.copyConfSpace(superSpace);

		for(int RCIndex = 0; RCIndex < initialConf.size(); RCIndex++)
		{
			int resIndex = initialConf.pos.get(RCIndex);
			int resConf = initialConf.RCs.get(RCIndex);
			PositionConfSpace residueDOF = superSpace.posFlex.get(resIndex).copy();
			if(superSpace.posFlex.size() <= resIndex 
					|| superSpace.posFlex.get(resIndex).RCs.size() <= resConf)
			{
				int numPos = superSpace.posFlex.size();
				int numRCs = superSpace.posFlex.get(resIndex).RCs.size();
				System.out.println("Error error! "+numPos+"-"+numRCs+"!="+resIndex+"-"+resConf);
			}
			RC assignedRC = superSpace.posFlex.get(resIndex).RCs.get(resConf);
			ArrayList<RC> constrainedList = new ArrayList<>();
			constrainedList.add(assignedRC);
			residueDOF.RCs = constrainedList;
			constrainedSpace.posFlex.set(RCIndex, residueDOF);
		}
		
		return constrainedSpace;
	}
}
