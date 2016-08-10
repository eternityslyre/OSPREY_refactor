package edu.duke.cs.osprey.tests;

import cern.colt.matrix.DoubleMatrix1D;
import cern.jet.math.Functions;
import edu.duke.cs.osprey.minimization.CCDMinimizer;
import edu.duke.cs.osprey.minimization.MoleculeModifierAndScorer;
import edu.duke.cs.osprey.tools.ObjectIO;

public class DebugTestFromMain {
	public static void main (String[] args)
	{
		debuggingCommands(args);
	}
	
	// TODO: Move these into a test file, and just call it from the test.
		private static void debuggingCommands(String[] args){

			//MolecEObjFunction mof = (MolecEObjFunction)ObjectIO.readObject("OBJFCN1442697734046.dat", true);
			MoleculeModifierAndScorer mof = (MoleculeModifierAndScorer)ObjectIO.readObject("OBJFCN1442697735769.dat", true);

	        CCDMinimizer minim = new CCDMinimizer(mof, false);
	        DoubleMatrix1D bestVals = minim.minimize();
	        double E = mof.getValue(bestVals);

	        DoubleMatrix1D boxBottom = bestVals.copy();
	        DoubleMatrix1D boxTop = bestVals.copy();
	        for(int q=0; q<bestVals.size(); q++){
	            boxTop.set(q, Math.min(mof.getConstraints()[1].get(q), bestVals.get(q)+1));
	            boxBottom.set(q, Math.max(mof.getConstraints()[0].get(q), bestVals.get(q)-1));
	        }

	        for(int a=0; a<1000000; a++){

	            DoubleMatrix1D x2 = bestVals.like();
	            for(int q=0; q<bestVals.size(); q++)
	                x2.set( q, boxBottom.get(q)+Math.random()*(boxTop.get(q)-boxBottom.get(q)) );

	            double E2 = mof.getValue(x2);
	            if(E2 < E-0.1){
	                System.out.println("gg");
	                DoubleMatrix1D dx = x2.copy();
	                dx.assign( bestVals, Functions.minus );

	                for(double t=1; true; t*=1.5){
	                    dx.assign(Functions.mult(t));
	                    x2.assign(dx);
	                    x2.assign(bestVals, Functions.plus);

	                    boolean oor = false;
	                    for(int q=0; q<x2.size(); q++){
	                        if( x2.get(q) > mof.getConstraints()[1].get(q) )
	                            oor = true;
	                        else if( x2.get(q) < mof.getConstraints()[0].get(q) )
	                            oor = true;
	                    }

	                    if(oor)
	                        break;

	                    E2= mof.getValue(x2);
	                    int aaa = 1;
	                }
	            }
	        }

	        System.exit(0);
			 
			//anything we want to try as an alternate main function, for debugging purposes
			//likely will want to exit after doing this (specify here)
			//for normal operation, leave no uncommented code in this function

		}
}
