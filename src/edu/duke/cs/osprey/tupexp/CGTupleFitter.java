/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.tupexp;

/**
 *
 * @author mhall44
 */

import cern.colt.matrix.DoubleMatrix1D;
import java.util.ArrayList;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.ConjugateGradient;
import org.apache.commons.math3.linear.RealLinearOperator;
import org.apache.commons.math3.linear.RealVector;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author mhall44
 */
public class CGTupleFitter {
    //conjugate gradient fit for the tuple expander
    //if there are problems can also try lsqr or other iterative methods
    //for now doing basic least squares--this might be enough (pruning might take the place
    //of modified lsq in this discrete-fit setting)
    
    
    RealLinearOperator AtA;
    RealVector Atb;
    
    //fit A * params = b
    //So solve A^T A params = A^T b 
    //(A is matrix defined by samp, b is true energies)
    
    int numSamp, numTup;
    ArrayList<ArrayList<Integer>> sampTuples;
    
    public CGTupleFitter(ArrayList<ArrayList<Integer>> sampleTuples, int numTuples, double[] trueVals){
        //sampleTuples.get(s) lists tuple numbers for sample s
        
        numSamp = sampleTuples.size();
        numTup = numTuples;
        sampTuples = sampleTuples;
        
        
        AtA = new RealLinearOperator(){

            @Override
            public int getRowDimension() {
                return numTup;
            }

            @Override
            public int getColumnDimension() {
                return numTup;
            }

            @Override
            public RealVector operate(RealVector rv) throws DimensionMismatchException {
                //first apply A
                double Arv[] = new double[numSamp];
                for(int s=0; s<numSamp; s++){
                    ArrayList<Integer> sampTup = sampTuples.get(s);
                    for(int t : sampTup)
                        Arv[s] += rv.getEntry(t);
                }
                
                //then apply A^T to Arv
                double ans[] = new double[numTup];
                for(int s=0; s<numSamp; s++){
                    ArrayList<Integer> sampTup = sampTuples.get(s);
                    for(int t : sampTup)
                        ans[t] += Arv[s];
                }
                
                return new ArrayRealVector(ans,false);//make RealVector without copying ans
            }
            
        };
        
        
        double atb[] = new double[numTup];
        //apply A^T to true vals
        for(int s=0; s<numSamp; s++){
            ArrayList<Integer> sampTup = sampTuples.get(s);
            for(int t : sampTup)
                atb[t] += trueVals[s];
        }
        
        Atb = new ArrayRealVector(atb);
    }
    
    
    double[] doFit(){
        //return fit tuple coefficients
        
        ConjugateGradient cg = new ConjugateGradient(100000,1e-6,false);//max_iter; delta; whether to check pos def
        //delta is target ratio of residual norm to true vals norm
        
        long startTime = System.currentTimeMillis();
        RealVector ans = cg.solve(AtA, Atb);
        
        System.out.println( "Conjugate gradient fitting time (ms): " + (System.currentTimeMillis()-startTime) );
        
        return ans.toArray();
    }
}

