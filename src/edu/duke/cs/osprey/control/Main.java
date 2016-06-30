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
        
        private static final String usageString = "Incorrect arguments. Command expects arguments "
                + "(e.g. -c KStar.cfg {findGMEC|fcalcKStar} System.cfg DEE.cfg";

	public static void main(String[] args){
		//args expected to be "-c KStar.cfg command config_file_1.cfg ..."

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


}