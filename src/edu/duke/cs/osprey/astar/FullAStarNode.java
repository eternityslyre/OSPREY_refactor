package edu.duke.cs.osprey.astar;

import java.util.Arrays;

public class FullAStarNode implements AStarNode {
	
	public static class Factory implements AStarNode.Factory<FullAStarNode> {
		
		private int numPos;
		
		public Factory(int numPos) {
			this.numPos = numPos;
		}
		
		@Override
		public FullAStarNode makeRoot() {
			int conf[] = new int[numPos];
			Arrays.fill(conf, -1);
			return new FullAStarNode(conf);
		}
		
		@Override
		public FullAStarNode make(FullAStarNode parent, int pos, int rc) {
			
			// explicitly instantiate the conformation
            int[] conf = parent.getNodeAssignments().clone();
            conf[pos] = rc;
            
			return new FullAStarNode(conf);
		}
	}
	
    private int nodeAssignments[];//assignments (e.g. partial conformation) for node
    
    private double score;//score (probably a lower bound on the energy)
    private double gscore;
    private double hscore;
    
    boolean scoreNeedsRefinement;

    
    //These are used in COMETS
    public double UB = Double.POSITIVE_INFINITY;//upper bound
    public int UBConf[] = null;//can have an upper bound on GMEC energy for this node's conf space
    //(and thus on the overall GMEC energy)
    //that is the energy of the conf denoted by UBConf
    
    
    
    //indicates the score needs to be refined (e.g. with EPIC continuous terms)
    //always false in simpler versions of A*
    public FullAStarNode(int[] nodeAssignments) {
        this.nodeAssignments = nodeAssignments;
        this.score = Double.NaN;
        this.gscore = Double.NaN;
        this.hscore = Double.NaN;
        this.scoreNeedsRefinement = false;
    }

    @Override
    public int compareTo(AStarNode other) {
        return Double.valueOf(score).compareTo(other.getScore());
    }

    @Override
    public int[] getNodeAssignments() {
        return nodeAssignments;
    }
    
    @Override
    public void setScore(double score) {
        this.score = score;
    }
    
    @Override
    public double getScore() {
        return score;
    }
    
    public double getGScore() {
    	return gscore;
    }
    public void setGScore(double val) {
    	gscore = val;
    }
    
    public double getHScore() {
    	return hscore;
    }
    public void setHScore(double val) {
    	hscore = val;
    }
    
    @Override
    public int getLevel() {
        int level = 0;
        for (int a : nodeAssignments) {
            if (a >= 0) {
                level++;
            }
        }
        return level;
    }
    
    @Override
    public boolean isFullyDefined() {
        //Assuming assignments greater than 0 denote fully defined positions,
        //determine if this node is fully defined or not
        for(int a : nodeAssignments){
            if(a<0)
                return false;
        }
        return true;
    }

	@Override
	public boolean scoreNeedsRefinement() {
		return scoreNeedsRefinement;
	}
	
	@Override
	public void setScoreNeedsRefinement(boolean val) {
		scoreNeedsRefinement = val;
	}
}
