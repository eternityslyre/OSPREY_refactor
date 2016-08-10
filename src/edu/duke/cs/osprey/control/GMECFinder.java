/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * TODO: 
 * 1. Remove the top comment, starting with "To change this template", since
 *  	it's useless to everyone who isn't using netbeans. 
 * 2. Encapsulate all energy-related terms and classes: useEPIC, useTupExp, 
 * 		fullEConfOnly, and so on should be consolidated into a class
 * 		that provides the correct energies when called.
 * 3. Consider simpler designs for lowestPairwiseBound, which reuses existing code
 * 		but also introduces cyclic dependencies in the design graph. It seems only 
 * 		to depend on the energy terms mentioned in 2.
 */
package edu.duke.cs.osprey.control;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import edu.duke.cs.osprey.astar.ConfTree;
import edu.duke.cs.osprey.astar.PairwiseConfTree;
import edu.duke.cs.osprey.confspace.ConfSearch;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.pruning.Pruner;
import edu.duke.cs.osprey.pruning.PruningControl;
import edu.duke.cs.osprey.pruning.PruningMatrix;

/**
 *
 * @author mhall44
 */
public class GMECFinder {
    
    //Many of the parameters that control the optimization process (like what DEE algs to use,
    //whether to use MPLP, etc. can be stored in this class
    //I propose that they be grouped into classes, similar to "EPICSettings," that handle settings for
    //particular aspects of functionality (energy function parameters, DEE/A* alg settings, etc.)
    //But we can also just store things as fields if people prefer
    
    //KStarCalculator will be set up similarly to this class
        
    private ConfigFileParser cfp;
    
    SearchProblem searchSpace;
    
    private double Ew;//energy window for enumerating conformations: 0 for just GMEC
    private double I0=0;//initial value of iMinDEE pruning interval
    private boolean doIMinDEE;//do iMinDEE
    
    
    private boolean useContFlex;
    //boolean enumByPairwiseLowerBound;//are we using a search method that 
    //enumerates by pairwise lower bound (minDEE-style)
    //or by (possibly approximated) true energy (rigid, EPIC, or tuple expander)?
    
    
    private boolean outputGMECStruct;//write the GMEC structure to a PDB file
    
    boolean useEPIC = false;
    boolean useTupExp = false;
    
    private boolean checkApproxE = true;//Use the actual energy function to evaluate
    //each enumerated conformation rather than just relying on the EPIC or tup-exp approximation
    
    private boolean useEllipses = false;
    
    
    boolean EFullConfOnly = false;//energy function only can be evaluated for full conf
    
    private double lowestBound;//lowest pairwise lower bound for a GMEC run
    //used to determine I-value needed
    
    
    private String confFileName;//file to which we write conformations
    
    public GMECFinder (ConfigFileParser cfgP){
        //fill in all the settings
        
        cfp = cfgP;
        
        //TODO: Arguably, this is the initialization, which should be its own function. The constructor may eventually
        // do other more interesting things and reading in member variables isn't that interesting. -JJ
        initializeParametersFromConfigFile(cfgP);
    }

    private void initializeParametersFromConfigFile (ConfigFileParser cfgP) {
        Ew = cfgP.params.getDouble("Ew");
        doIMinDEE = cfgP.params.getBool("imindee");
        if(doIMinDEE){
            I0 = cfgP.params.getDouble("Ival");
        }
        
        useContFlex = cfgP.params.getBool("doMinimize");
        useTupExp = cfgP.params.getBool("UseTupExp");
        useEPIC = cfgP.params.getBool("UseEPIC");
        
        checkApproxE = cfgP.params.getBool("CheckApproxE");
        
        if(doIMinDEE && !useContFlex)
            throw new RuntimeException("ERROR: iMinDEE requires continuous flexibility");
        
        outputGMECStruct = cfgP.params.getBool("OUTPUTGMECSTRUCT");
        
        useEllipses = cfgP.params.getBool("useEllipses");
        //for now only full-conf-only E-fcn supported is Poisson-Boltzmann
        EFullConfOnly = cfgP.params.getBool("UsePoissonBoltzmann");
        
        confFileName = cfgP.params.getRunSpecificFileName("CONFFILENAME", ".confs.txt");
    }
    
    
   
    public double calcGMEC(){
        //Calculate the GMEC
        
        double curInterval = I0;//For iMinDEE.  curInterval will need to be an upper bound
        //on GMEC-lowestBound
        //but we can start with an estimate I0 and raise if needed
        boolean needToRepeat;
        
        //GMEC is the lowest-energy conformation enumerated in this whole search
        int GMECConf[] = null;
        double bestESoFar = Double.POSITIVE_INFINITY;
        
        searchSpace = cfp.getSearchProblem();
        
        boolean printEPICEnergy = checkApproxE && useEPIC && useTupExp;
        ConfPrinter confPrinter = new ConfPrinter(searchSpace, confFileName, printEPICEnergy);
        
        do {
            needToRepeat = false;
            
            //initialize a search problem with current Ival
            if(useEPIC)
                checkEPICThresh2(curInterval);//Make sure EPIC thresh 2 matches current interval

            precomputeMatrices(Ew+curInterval);//precompute the energy, pruning, and maybe EPIC or tup-exp matrices
            //must be done separately for each round of iMinDEE
            
            //Finally, do A*, which will output the top conformations
            ConfSearch search = initSearch(searchSpace);//e.g. new AStarTree from searchSpace & params
            //can have options to instantiate other kinds of search here too...choose based on params
            
            double lowerBound;
            int conformationCount=0;
            
            System.out.println();
            System.out.println("BEGINNING CONFORMATION ENUMERATION");
            try {
            	System.out.println("\tsearching " + search.getNumConformations().floatValue() + " conformations...");
            } catch (UnsupportedOperationException ex) {
            	// conf tree doesn't support it, no big deal
            }
            System.out.println();
            
            long confSearchStartTime = System.currentTimeMillis();
            
            do {
                int conf[] = search.nextConf();
                
                if(conf==null){//no confs left in tree. Effectively, infinite lower bound on remaining confs
                    lowerBound = Double.POSITIVE_INFINITY;
                }
                else {//tree not empty
                                        
                    double confE = getConfEnergy(conf);//MINIMIZED, EPIC, OR MATRIX E AS APPROPRIATE
                    //this is the true energy; if !checkApproxE may be an approximation to it
                    
                    
                    lowerBound = confE;//the lower bound and confE are the same in rigid
                    //or non-checkApproxE EPIC and tup-exp calculations
                    //lowerBound is always what we enumerate in order of
                    if(useContFlex){
                        if(useTupExp||useEPIC){
                            if(checkApproxE)//confE is the "true" energy function
                                lowerBound = searchSpace.approxMinimizedEnergy(conf);
                        }
                        else
                            lowerBound = searchSpace.lowerBound(conf);
                    }
                    else if(EFullConfOnly && checkApproxE)//get tup-exp approx
                        lowerBound = searchSpace.approxMinimizedEnergy(conf);
                    
                    if(confE<bestESoFar){
                        bestESoFar = confE;
                        GMECConf = conf;
                        System.out.println("New best energy: "+confE);
                    }

                    lowestBound = Math.min(lowestBound,lowerBound);

                    confPrinter.printConf(conf,confE,lowerBound,bestESoFar,conformationCount);
                    conformationCount++;
                }
                
                
                
                                
                
                //DEBUG!!!!
                /*
                int conf2[] = new int[] {5,7,12,5,0,7,4};
                boolean b2 = searchSpace.pruneMat.isPruned(new RCTuple(conf2));
                double LB2 = searchSpace.lowerBound(conf2);
                double E2 = searchSpace.approxMinimizedEnergy(conf2);
                double EPIC2 = searchSpace.EPICMinimizedEnergy(conf2);
                int aaa = 0;
                */

                
                
                
                
                
                if(doIMinDEE){//if there are no conformations with minimized energy
                    //within curInterval of the lowestBound
                    //then we have to repeat with a higher curInterval
                    if( (lowerBound > lowestBound + curInterval)
                            && (bestESoFar > lowestBound + curInterval) ){
                        
                        curInterval = bestESoFar - lowestBound + 0.001;//give it a little buffer for numerical issues
                        System.out.println("Raising pruning interval to "+curInterval);
                        needToRepeat=true;
                        break;
                    }
                }
                
                if(bestESoFar==Double.POSITIVE_INFINITY){//no conformations found
                    System.out.println("A* returned no conformations.");
                    break;
                }
                
                // if we're finding a rigidGMEC with no energy window, don't wait for the second conformation
                if (!useContFlex && Ew == 0) {
                	break;
                }
                
            } while( bestESoFar+Ew >= lowerBound );//lower bound above GMEC + Ew...can stop enumerating
            
            double confSearchTimeMinutes = (System.currentTimeMillis()-confSearchStartTime)/60000.0;
            System.out.println("Conf search time (minutes): "+confSearchTimeMinutes);
            
        } while(needToRepeat);
        
        if(outputGMECStruct && GMECConf!=null)
            searchSpace.outputMinimizedStruct( GMECConf, searchSpace.name+".GMEC.pdb" );
        
        confPrinter.closeConfFile();
        System.out.println("GMEC calculation complete.  ");
        
        System.out.println("GMEC energy: "+bestESoFar);
        return bestESoFar;
    }
    
    
    void precomputeMatrices(double pruningInterval){
            //Precalculate TupleMatrices needed for GMEC computation.  Some of these may already be computed.  
            //All of these matrices except the basic pairwise energy matrix are pruning-dependent:
            //we can prune conformations whose energies are within pruningInterval
            //of the lowest pairwise lower bound
            
            if(EFullConfOnly){//Perform a tuple expansion that does not assume a pairwise
                //energy function, and thus must omit some of the usual pruning steps.
                fullConfOnlyTupExp();
                return;
            }
        
            //First calculate the pairwise energy matrix, if not already present
            searchSpace.loadEnergyMatrix();
            
            
            
            //DEBUG!!! Timing/profiling minimization for 40.cont...about 1/3 SAPE, small EPIC terms probably unimportant...
            /*searchSpace.pruneMat = new PruningMatrix(searchSpace.confSpace,0);
            searchSpace.loadEPICMatrix();
            long startTime = System.currentTimeMillis();
            int conf[] = new int[] { 0, 24, 1, 6, 4, 0, 9, 11, 4, 4, 4, 9, 3, 2, 22, 6, 
                4, 1, 4, 1, 4, 6, 13, 0, 3, 4, 1, 3, 1, 3, 2, 6, 21, 8, 5, 0, 26, 3, 1, 19 };

            //int conf[] = new int[] {0, 26, 1, 6, 2, 0, 7, 8, 6, 4, 4, 14, 4, 3, 26, 6, 3, 1, 2, 1, 
            //    5, 9, 13, 1, 2, 4, 1, 3, 2, 7, 2, 6, 17, 21, 4, 0, 15, 6, 1, 32 };
            
            
            System.out.println("EPIC MIN: "+searchSpace.EPICMinimizedEnergy(conf));
            long minDoneTime = System.currentTimeMillis();
            System.out.println("MIN TIME: "+(minDoneTime-startTime));
            System.exit(0);*/
            

            //Doing competitor pruning now
            //will limit us to a smaller, but effective, set of competitors in all future DEE
            if(searchSpace.competitorPruneMat == null){
                System.out.println("PRECOMPUTING COMPETITOR PRUNING MATRIX");
                PruningControl compPruning = cfp.setupPruning(searchSpace,0,false,false);
                compPruning.setOnlyGoldstein(true);
                compPruning.prune();
                searchSpace.competitorPruneMat = searchSpace.pruneMat;
                searchSpace.pruneMat = null;
                System.out.println("COMPETITOR PRUNING DONE");
            }
            
            
            //Next, do DEE, which will fill in the pruning matrix
            PruningControl pruning = cfp.setupPruning(searchSpace,pruningInterval,false,false);
            
            pruning.prune();//pass in DEE options, and run the specified types of DEE            
            
            
            //At this point, if we're using pairwise lower bounds, they're ready
            //Compute the bound now, before starting non-lower-bound-based pruning
            //(which could mess up the pairwise lowest bound calculation, though
            //it will speed up the GMEC calculation)
            lowestBound  = Double.POSITIVE_INFINITY;
            if( (useEPIC||useTupExp) && doIMinDEE)//lowest bound must be calculated without EPIC/tup exp, to ensure valid iMinDEE interval
                lowestBound = lowestPairwiseBound(searchSpace);
            
            
            //precomputing EPIC or tuple-expander matrices is much faster
            //if only done for unpruned RCs.  Less RCs to handle, and the fits are far simpler.  
            if(useEPIC){
                searchSpace.loadEPICMatrix();
                
                //we can prune more using the EPIC matrix
                System.out.println("Beginning post-EPIC pruning.");
                PruningControl postEPICPruning = cfp.setupPruning(searchSpace, pruningInterval,true,false);
                postEPICPruning.prune();
                System.out.println("Finished post-EPIC pruning.");
            }
            if(useTupExp){//preferably do this one EPIC loaded (much faster if can fit to EPIC)
                searchSpace.loadTupExpEMatrix();
                
                //we can prune even more with tup-exp!
                //we can prune more using the EPIC matrix
                //no iMinDEE interval needed here
                System.out.println("Beginning post-tup-exp pruning.");
                PruningControl postTEPruning = cfp.setupPruning(searchSpace, Ew,false,true);
                postTEPruning.prune();
                System.out.println("Finished post-tup-exp pruning.");
            }
    }
    
    
    private void fullConfOnlyTupExp(){
        //precompute the tuple expansion
        if(!useTupExp)
            throw new RuntimeException("ERROR: Need tuple expansion to handle full-conf-only E-function");
        if(useEPIC)//later consider using differencing scheme to do EPIC for these
            throw new RuntimeException("ERROR: EPIC for full-conf-only E-function not yet supported");
        if(doIMinDEE)//don't have concept of pairwise lower bound, so not doing iMinDEE 
            //(can just do rigid pruning on tup-exp matrix, even if using cont flex)
            throw new RuntimeException("ERROR: iMinDEE + full-conf-only E-function not supported");
        
        
        //Let's compute a matrix from the pairwise terms (no P-B), to use in selecting triples
        searchSpace.loadEnergyMatrix();
        
        //initialize pruning matrix.  Nothing pruned yet because don't have pairwise energies
        searchSpace.pruneMat = new PruningMatrix(searchSpace.confSpace,Ew);//not iMinDEE
        
        //We can only do steric pruning
        //May want to set a lower thresh than the default (30 perhaps)
        double stericThresh = cfp.params.getDouble("STERICTHRESH");
        Pruner pruner = new Pruner(searchSpace, false, 0, 0, false, false);
        pruner.pruneSteric(stericThresh);
                
        searchSpace.loadTupExpEMatrix();
    }
    
    
    double getConfEnergy(int[] conf){
        //MINIMIZED, EPIC, OR MATRIX E AS APPROPRIATE
        //whatever is being used as the "true" energy while enumerating
        if(useContFlex || EFullConfOnly){
            if( (useEPIC||useTupExp) && (!checkApproxE) )
                return searchSpace.approxMinimizedEnergy(conf);
            else
                return searchSpace.minimizedEnergy(conf);
        }
        else
            return searchSpace.lowerBound(conf);//for rigid calc w/ pairwise E-mtx, can just calc from mtx
    }
        
    ConfSearch initSearch(SearchProblem searchSpace){
        //initialize some kind of combinatorial search, like A*
        //FOR NOW just using A*; may also want BWM*, WCSP, or something according to settings
    	ConfTree<?> tree;
    	if (searchSpace.searchNeedsHigherOrderTerms()) {
    		tree = ConfTree.makeFull(searchSpace);
    	} else {
    		tree = new PairwiseConfTree(searchSpace);
    	}
    	tree.initProgress(searchSpace.confSpace.numPos);
    	return tree;
    }
    
    
    double lowestPairwiseBound(SearchProblem prob){
        //In an EPIC calculation, our enumeration will probably include much less conformations,
        //but for iMinDEE purposes we still need to know what our lowest bound would have been
        //if we enumerated w/o EPIC (i.e., what is the minimum energy calculated using the lower-bound energy matrix)
        
        System.out.println();
        System.out.println("Calculating no-EPIC lowest energy bound");
        
        SearchProblem probNoEPIC = new SearchProblem(prob);//shallow copy
        probNoEPIC.useEPIC = false;
        probNoEPIC.useTupExpForSearch = false;
        
        ConfSearch searchNoEPIC = initSearch(probNoEPIC);
        int LBConf[] = searchNoEPIC.nextConf();//lowest conf for this search
        
        double LB = Double.POSITIVE_INFINITY;
        if(LBConf != null)
            LB = probNoEPIC.lowerBound(LBConf);
        
        System.out.println("No-EPIC lowest energy bound: "+LB);
        System.out.println();
        
        return LB;
    }

    
    private void checkEPICThresh2(double curInterval){
        if(curInterval+Ew>searchSpace.epicSettings.EPICThresh2){//need to raise EPICThresh2 
            //to the point that we can guarantee no continuous component of the GMEC
            //or desired ensemble will need to reach it
            System.out.println("Raising EPICThresh2 to "+(curInterval+Ew)+" based on "
                    + "iMinDEE interval and energy window");
            searchSpace.epicSettings.EPICThresh2 = curInterval+Ew;
        }
    }
   
}