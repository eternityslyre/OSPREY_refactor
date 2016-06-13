/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.control;

import edu.duke.cs.osprey.energy.LigandResEnergies;
import java.util.HashMap;
import java.util.Map;
import edu.duke.cs.osprey.energy.MultiTermEnergyFunction;
import edu.duke.cs.osprey.tests.UnitTestSuite;

/**
 *
 * @author mhall44
 * Parse arguments and call high-level functions like DEE/A* and K*
   These will each be controlled by dedicated classes, unlike in the old KSParser
   to keep this class more concise, and to separate these functions for modularity purposes
 */

public class Main {

	public static Map<String, Runnable> commands;
        
        private static final String usageString = "Command expects arguments "
                + "(e.g. -c KStar.cfg {findGMEC|fcalcKStar} System.cfg DEE.cfg";

	public static void main(String[] args){
		//args expected to be "-c KStar.cfg command config_file_1.cfg ..."

		debuggingCommands(args);

		String command = "";
		try{
                    command = args[2];
		}
		catch(Exception e){
			System.out.println(usageString);
			System.exit(1);
		}



		long startTime = System.currentTimeMillis();

		ConfigFileParser cfp = new ConfigFileParser(args);//args 1, 3+ are configuration files

                EnvironmentVars.openSpecialWarningLogs(cfp);

		//load data files
		cfp.loadData();



		//DEBUG!!
		// set number of threads for energy function evaluation
		MultiTermEnergyFunction.setNumThreads( cfp.params.getInt("eEvalThreads") );

		initCommands(args, cfp);

		if(commands.containsKey(command))
			commands.get(command).run();
		else
			throw new RuntimeException("ERROR: OSPREY command unrecognized: "+command);
                
                EnvironmentVars.closeSpecialWarningLogs();
                
		long endTime = System.currentTimeMillis();
		System.out.println("Total OSPREY execution time: " + ((endTime-startTime)/60000) + " minutes.");
		System.out.println("OSPREY finished");
	}

	private static void initCommands(String[] args, ConfigFileParser cfp) {
		// TODO Auto-generated method stub
		commands = new HashMap<String, Runnable>();

		commands.put("findGMEC", new Runnable() {
			@Override
			public void run() {
				GMECFinder gf = new GMECFinder(cfp);
				gf.calcGMEC();
			}
		});

		commands.put("calcKStar", new Runnable() {
			@Override
			public void run() {
				System.err.println("Feature not implemented in this version.");
			}
		});

		commands.put("RunTests", new Runnable() {
			@Override
			public void run() {
				UnitTestSuite.runAllTests();
			}
		});

		commands.put("doCOMETS", new Runnable() {
			@Override
			public void run() {
				COMETSDoer cd = new COMETSDoer(args);
				cd.calcBestSequences();
			}
		});

		commands.put("calcLigResE", new Runnable() {
			@Override
			public void run() {
				LigandResEnergies lre = new LigandResEnergies(cfp.getParams());
				lre.printEnergies();
			}
		});

		commands.put("calcEnergy", new Runnable() {
			@Override
			public void run() {
				new EnergyCalculator().run(cfp);
			}
		});

		commands.put("ConfInfo", new Runnable() {
			@Override
			public void run() {
				ConfInfo ci = new ConfInfo(cfp);
				ci.outputConfInfo();
			}
		});
		
		commands.put("computeAlternates",
				new Runnable()
		{
			@Override
			public void run() {
				AlternateConformationEnumerator enumerator = new AlternateConformationEnumerator(cfp);
				enumerator.run();
			}

		});
		
		
	}

	// TODO: Move these into a test file, and just call it from the test.
	private static void debuggingCommands(String[] args){

		//MolecEObjFunction mof = (MolecEObjFunction)ObjectIO.readObject("OBJFCN1442697734046.dat", true);
		/*MolecEObjFunction mof = (MolecEObjFunction)ObjectIO.readObject("OBJFCN1442697735769.dat", true);

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
		 */
		//anything we want to try as an alternate main function, for debugging purposes
		//likely will want to exit after doing this (specify here)
		//for normal operation, leave no uncommented code in this function

	}


}