/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.ematrix;

import java.util.ArrayList;

import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.HigherTupleFinder;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.TupleMatrixDouble;

/**
 *
 * @author mhall44
 */
public class EnergyMatrix extends TupleMatrixDouble {

	private static final long serialVersionUID = 6503270845014990929L;

	private double constTerm = 0;
    
    //we may want to have reference energies associated with this matrix
    ReferenceEnergies eRefMat = null;
    
    
    
    public EnergyMatrix(ConfSpace cSpace, double pruningInterval){
        //For a normal, precomputed energy matrix we expect infinite pruning interval
        //(matrix valid for all RCs),
        //but EnergyMatrix objects from tup-exp may only be valid for a finite pruning interval
        super(cSpace, pruningInterval, 0.);//default higher interaction for energies is 0
    }
    
    public EnergyMatrix(int numPos, int[] numRCsAtPos, double pruningInterval){
        super(numPos, numRCsAtPos, pruningInterval, 0.);
    }
    
    public double confE(int conf[]){
        //value of energy represented in energy matrix, for specified conformation
        //expressed as residue-specific RC indices (as in the storage matrices)
        return getInternalEnergy(new RCTuple(conf)) + constTerm;
    }
    
    
    public double getInternalEnergy(RCTuple tup){
        //internal energy of a tuple of residues when they're in the specified RCs
    	
		// OPTIMIZATION: don't even check higher terms if the energy matrix doesn't have any
		// this does wonders to CPU cache performance!
		boolean useHigherOrderTerms = hasHigherOrderTerms();
		
		ArrayList<Integer> tuppos = tup.pos;
		ArrayList<Integer> tupRCs = tup.RCs;
		
        // OPTIMIZATION: split oneBody and pairwise energies into separate loops
		// to improve CPU cache performance
		
        int numPosInTuple = tup.pos.size();
        double energy = 0;
        
        for(int indexInTuple=0; indexInTuple<numPosInTuple; indexInTuple++){
            int posNum = tuppos.get(indexInTuple);
            int RCNum = tupRCs.get(indexInTuple);
            
            energy += getOneBody(posNum,RCNum);
        }
        
        for(int indexInTuple=0; indexInTuple<numPosInTuple; indexInTuple++){
            int posNum = tuppos.get(indexInTuple);
            int RCNum = tupRCs.get(indexInTuple);
            
            for(int index2=0; index2<indexInTuple; index2++){
                int pos2 = tuppos.get(index2);
                int rc2 = tupRCs.get(index2);
                
                energy += getPairwise(posNum,RCNum,pos2,rc2);
                
                if (useHigherOrderTerms) {
					HigherTupleFinder<Double> htf = getHigherOrderTerms(posNum,RCNum,pos2,rc2);
					if(htf != null)
						energy += internalEHigherOrder(tup,index2,htf);
                }
            }
        }
        
        return energy;
    }
    
    public double getHigherOrderEnergy(RCTuple tup, int i1, int i2) {
    	int res1 = tup.pos.get(i1);
    	int rc1 = tup.pos.get(i1);
    	int res2 = tup.pos.get(i2);
    	int rc2 = tup.RCs.get(i2);
		HigherTupleFinder<Double> htf = getHigherOrderTerms(res1, rc1, res2, rc2);
		if (htf != null) {
			return internalEHigherOrder(tup, i2, htf);
		}
		return 0;
    }
    
    double internalEHigherOrder(RCTuple tup, int curIndex, HigherTupleFinder<Double> htf){
        //Computes the portion of the internal energy for tuple tup
        //that consists of interactions in htf (corresponds to some sub-tuple of tup)
        //with RCs whose indices in tup are < curIndex
        double E = 0;
        
        for(int ipos : htf.getInteractingPos()){
            
            //see if ipos is in tup with index < curIndex
            int iposIndex = -1;
            for(int ind=0; ind<curIndex; ind++){
                if(tup.pos.get(ind)==ipos){
                    iposIndex = ind;
                    break;
                }
            }

            if(iposIndex > -1){//ipos interactions need to be counted
                int iposRC = tup.RCs.get(iposIndex);
                E += htf.getInteraction(ipos, iposRC);
                
                //see if need to go up to highers order again...
                HigherTupleFinder<Double> htf2 = htf.getHigherInteractions(ipos,iposRC);
                if(htf2!=null){
                    E += internalEHigherOrder(tup,iposIndex,htf2);
                }
            }
        }
        
        return E;
    }
        
    /*
    public double getPairwiseE(int res1, int AA1, int rot1, int res2, int AA2, int rot2){
    //lookup by residue number, amino-acid index (for the residue's rotamer library),
    //and RC index for that residue and AA type (rotamer index for AA type if rigid backbone,
    //otherwise defined in the ConfSearchSpace)
    //RETURN ERROR IF RES1 AND RES2 ARE NOT SINGLE RESIDUES
    return getPairwise(res1, index1, res2, index2);
    }
     */
    //intra+shell similar...
    public double getConstTerm() {
        return constTerm;
    }

    public void setConstTerm(double constTerm) {
        this.constTerm = constTerm;
    }
    
    
    
    public double[][] topPairwiseInteractions(){
        //return the top absolute values of the pairwise interactions
        //between all pairs of positions
        int numPos = getNumPos();
        
        double strongestPairE[][] = new double[numPos][numPos];
        
        for(int pos=0; pos<numPos; pos++){
            for(int pos2=0; pos2<pos; pos2++){
                for(int rc=0; rc<getNumConfAtPos(pos); rc++){
                    for(int rc2=0; rc2<getNumConfAtPos(pos2); rc2++){
                        strongestPairE[pos][pos2] = Math.max( strongestPairE[pos][pos2], Math.abs(getPairwise(pos, rc, pos2, rc2)) );
                        strongestPairE[pos2][pos] = strongestPairE[pos][pos2];
                    }
                }
            }
        }
        
        return strongestPairE;
    }

    public ReferenceEnergies geteRefMat() {
        return eRefMat;
    }
}
