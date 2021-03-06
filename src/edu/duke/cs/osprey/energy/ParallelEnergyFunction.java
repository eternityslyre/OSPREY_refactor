package edu.duke.cs.osprey.energy;

import java.util.ArrayList;

import edu.duke.cs.osprey.astar.parallel.WorkCrew;
import edu.duke.cs.osprey.astar.parallel.Worker;

public class ParallelEnergyFunction implements EnergyFunction {
	
	private static class EnergyWorker extends Worker {
		
		private int startIndex;
		private int stopIndex;
		private double energy;

		public EnergyWorker(WorkCrew<EnergyWorker> crew) {
			super(crew);
		}

		@Override
		protected void workIt() {
			
			// copy some references/values to the stack for that little extra bit of speed
			ArrayList<EnergyFunction> terms = efunc.terms;
			ArrayList<Double> coeffs = efunc.coeffs;
			int startIndex = this.startIndex;
			int stopIndex = this.stopIndex;
			
			// do it!
			double energy = 0;
			for (int i=startIndex; i<=stopIndex; i++) {
				energy += terms.get(i).getEnergy()*coeffs.get(i);
			}
			
			// save results to 'this' memory
			this.energy = energy;
		}
	}

	private static WorkCrew<EnergyWorker> crew;
	private static ParallelEnergyFunction efunc;
	
	static {
		crew = null;
		efunc = null;
	}
	
	public static boolean isCrewStarted() {
		return crew != null;
	}
	
	public static void startCrew(int numThreads) {
		crew = new WorkCrew<>("Energy");
		for (int i=0; i<numThreads; i++) {
			new EnergyWorker(crew);
		}
		crew.start();
	}
	
	public static void stopCrew() {
		if (crew == null) {
			return;
		}
		crew.askToStop();
		crew = null;
	}
	
	public static void setEFunc(ParallelEnergyFunction val) {
		
		if (efunc == val) {
			return;
		}
		
		efunc = val;
		
		// set up partition
		// partition terms among workers
		int numTerms = efunc.terms.size();
		int numWorkers = crew.getWorkers().size();
		int width = (numTerms + numWorkers - 1)/numWorkers;
		int startIndex = 0;
		int stopIndex = startIndex + width - 1;
		for (EnergyWorker worker : crew.getWorkers()) {
			worker.startIndex = startIndex;
			worker.stopIndex = stopIndex;
			startIndex += width;
			stopIndex = Math.min(stopIndex + width, numTerms - 1);
		}
	}

	private static final long serialVersionUID = -2789380428939629566L;
	
	private ArrayList<EnergyFunction> terms;
	private ArrayList<Double> coeffs;
	
	public ParallelEnergyFunction(ArrayList<EnergyFunction> terms, ArrayList<Double> coeffs) {
		this.terms = terms;
		this.coeffs = coeffs;
	}
	
	public ArrayList<EnergyFunction> getTerms() {
		return terms;
	}

	public ArrayList<Double> getCoeffs() {
		return coeffs;
	}
	
	@Override
	public double getEnergy() {
		
		setEFunc(this);
		
		// start all the processors
		crew.sendWork();
		
		// wait for the work to finish
		try {
			boolean finished = crew.waitForResults(10000);
			if (!finished) {
				throw new Error("Timed our waiting 10 seconds for energy calculations to finish!"
					+ "\nEnergy calculation shouldn't take more than 10 seconds, right?");
			}
		} catch (InterruptedException ex) {
			// something wanted us to stop, so stop, then forward the exception
			throw new Error(ex);
		}
		
		// collect the results
		double energy = 0;
		for (EnergyWorker worker : crew) {
			energy += worker.energy;
		}
		return energy;
	}
}
