/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.energy.forcefield;

import edu.duke.cs.osprey.structure.Atom;
import edu.duke.cs.osprey.structure.AtomNeighbors;
import edu.duke.cs.osprey.structure.Residue;
import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author mhall44
 */
public class ForcefieldEnergy implements Serializable {
	
	private static final long serialVersionUID = 944833567342112297L;

	//This can represent either the internal energy of a residue, or the interaction energy of two
    //some atoms from one or both residues can be excluded if desired (set up at constructor)
    boolean isInternal;//true for internal, false for interaction
    
    Residue res1, res2;//res1==res2 if internal energy of res1.  Else this is interaction of res1, res2
    AtomCache atomCache;
    double energyCache;
    
    ForcefieldParams params;
        
	// If the debug flag is set to true then additional debug statements are
	//  printed to standard out. I'm hoping that by making it a public static
	//  final variable that the compiler will be smart and not compile/include
	//  code that is unreachable.
	public static final boolean debug = false;
		
	boolean doSolvationE = false; //should solvation energies be computed
	
	double dielectric = 1.0;	
	boolean distDepDielect = true;
	final double constCoulomb = 332.0;

	
	//these terms will refer to atom numbers within res1 and res2
	//so the energy is a sum of the indicated res1-res2 interactions
	//(need not be symmetric if res1==res2)
	int numberNonBonded = 0;
	int numberHalfNonBonded = 0;
	int numberSolvated = 0;
	double nonBondedTerms[];
	double halfNonBondedTerms[];
	double solvationTerms[];
	
	double D2R = 0.01745329251994329576;
	double R2D = 57.29577951308232090712;
	double vdwMultiplier = 1.0f;
	double internalSolvEnergy = 0;
	
	
	//Solvation interactions for atoms more than 9.0A apart are already counted in dG(ref);
	//		Only count solvation interactions between atoms within 9.0A distance
	final double solvCutoff = 9.0;
	
	double solvScale = 1.0; //the scale factor for the solvation energies
	
	boolean useHydrogenEs;;
	boolean useHydrogenVdw;
	
	
	public ForcefieldEnergy(boolean intra, ArrayList<Atom> atoms1, ArrayList<Atom> atoms2,
                ForcefieldParams params){
            
            isInternal = intra;
            checkResComposition(intra,atoms1,atoms2);//if intra, then make sure all atoms from same res
            //and point res2 to res1, etc
            atomCache = new AtomCache(res1, res2);
            energyCache = Double.NaN;
            
            this.params = params;
            
            //copy over some things from the params for easier access
            distDepDielect = params.distDepDielect;
            dielectric = params.dielectric;
            vdwMultiplier = params.vdwMultiplier;
            doSolvationE = params.doSolvationE;
            solvScale = params.solvScale;
            useHydrogenEs = params.hElect;
            useHydrogenVdw = params.hVDW;
                
            //set up actual energies 
            //(interaction between atoms1 & atoms2, or internal of atoms1 if atoms2==null)
            initializeCalculation(atoms1,atoms2);
	}
	
        public ForcefieldEnergy(ArrayList<Atom[]> atomPairs, ForcefieldParams params){
            /* Sparse energy function that includes only the electrostatic and VDW interactions between the specified atom pairs
             * Used for SAPE
             */
            
            res1 = atomPairs.get(0)[0].res;
            res2 = atomPairs.get(0)[1].res;
            isInternal = (res1==res2);
            atomCache = new AtomCache(res1, res2);
            energyCache = Double.NaN;
            
            //check that all the atom pairs are at the same residue pair (assumed by structure of ForcefieldEnergy)
            for(Atom[] pair : atomPairs){
                if ( pair[0].res!=res1 || pair[1].res!=res2 ){
                    throw new RuntimeException("ERROR: Sparse forcefield energy given atom pairs with inconsistent residues");
                }
            }
            
            
            this.params = params;
            
            //copy over some things from the params for easier access
            distDepDielect = params.distDepDielect;
            dielectric = params.dielectric;
            vdwMultiplier = params.vdwMultiplier;
            solvScale = params.solvScale;
            
            useHydrogenEs = params.hElect;
            useHydrogenVdw = params.hVDW;
            
            doSolvationE = false;//not including solvation in these sparse energies
            
            //so initialize just the EV energies
            ArrayList<Atom[]> pairs14 = AtomNeighbors.getPairs14(atomPairs);
            ArrayList<Atom[]> pairsNonBonded = AtomNeighbors.getPairsNonBonded(atomPairs);
            
            initializeEVCalculation(pairs14, pairsNonBonded);
	}
        
        void checkResComposition(boolean intra, ArrayList<Atom> atoms1, ArrayList<Atom> atoms2){
            //set up res1 and res2 and make sure they are defined consistently
            
            if(intra){
                res1 = atoms1.get(0).res;
                res2 = res1;
                
                if(atoms1.size()!=atoms2.size())
                    throw new RuntimeException("ERROR: Atoms for intra-residue energy defined inconsistently");
                
                for(int atNum=0; atNum<atoms1.size(); atNum++){
                    if(atoms1.get(atNum)!=atoms2.get(atNum))
                        throw new RuntimeException("ERROR: Atoms for intra-residue energy defined inconsistently");
                    if(atoms1.get(atNum).res != res1)
                        throw new RuntimeException("ERROR: Can't compute intra-residue energy"
                                + " for list of atoms at different residues");
                }
            }
            else {
                res1 = atoms1.get(0).res;
                res2 = atoms2.get(0).res;
                
                if (res1==res2){
                    throw new RuntimeException("ERROR: Pairwise energy must be for "
                            + "two different residues");
                }
                
                for(Atom at : atoms1){
                    if(at.res != res1){
                        throw new RuntimeException("ERROR: Pairwise energy "
                                + "can't have more than one residue on one side");
                    }
                }
                
                for(Atom at : atoms2){
                    if(at.res != res2){
                        throw new RuntimeException("ERROR: Pairwise energy "
                                + "can't have more than one residue on one side");
                    }
                }
            }
            
            //also check that all bonds are marked in the residue(s)
            if( ! ( res1.interResBondsMarked && res1.intraResBondsMarked
                    && res2.interResBondsMarked && res2.intraResBondsMarked ) ){
                throw new RuntimeException("ERROR: Trying to set up force field energy"
                        + " for a residue whose bonds haven't been marked yet");
            }
        }

	public int getNumTerms() {
		// just to get a sense of the size of the work being done
		int num = numberNonBonded + numberHalfNonBonded;
		if (doSolvationE) {
			num += numberSolvated;
		}
		return num;
	}
        
        
//////////////////////////////////////////////////////////////////////////////////////////////////
//	This section initializes the energy calculation
//////////////////////////////////////////////////////////////////////////////////////////////////
	// This function sets up the arrays for energy evaluation
	//  it does lookup calls to getStretchParameters and such
	// It prepares terms for bond, angle, and dihedral, vdw, and electrostatic
	// Terms involving residues with energyEval == false
	//  are not included
        //we're looking at the interaction between atoms1 and atoms2 (or internal energy of atom1
        //if atoms2 is null)
	public void initializeCalculation(ArrayList<Atom> atoms1, ArrayList<Atom> atoms2){
		
            //enumerate interacting pairs of atoms
            ArrayList<Atom[]> pairs14 = AtomNeighbors.getPairs14(atoms1, atoms2, (res1==res2));
            ArrayList<Atom[]> pairsNonBonded = AtomNeighbors.getPairsNonBonded(atoms1, atoms2, (res1==res2));
            //these pairs should exclude any SC or BB components we don't want
            
            initializeEVCalculation(pairs14,pairsNonBonded); //initialize the calculation of the electrostatic and vdW terms

            if (doSolvationE) //initialize solvation energy calculation
                    initializeSolvationCalculation(atoms1,atoms2);		
	}

	// This function sets up the arrays for energy evaluation
	//  for electrostatics and vdW only (EV)
        //we initialize using lists of interacting pairs of atoms (1,4-bonded and non-bonded)
	private void initializeEVCalculation(ArrayList<Atom[]> pairs14, ArrayList<Atom[]> pairsNonBonded){

		int atom1, atom2, atom4;//, ix2, ix4, ix4b;
		int atomType1, atomType2, atomType4;
		boolean isHydrogen1, isHydrogen2;
		double equilibriumDistance[] = new double[1];
		double epsilon[] = new double[1];
		double smallerArray[];
		double Amult, Bmult;
		
		// calculate vdW multiplier
		// Note: Bmult = vdwMultiplier^6 and Amult = vdwMultiplier^12
		Bmult = vdwMultiplier * vdwMultiplier;
		Bmult = Bmult*Bmult*Bmult;
		Amult = Bmult*Bmult;
		
		if (debug)
			System.out.println("Starting initializeEVCalculation");

		//-------------------------------
		// half non bonded terms
		//-------------------------------
		// 5 terms: atom1, atom2, isHydrogen, Aij, Bij
		
		int number14Connections = pairs14.size();
		
		if (debug)
			System.out.println("Initial number of 1-4 pairs: " + number14Connections);
		
		halfNonBondedTerms = new double[number14Connections * 5];
		numberHalfNonBonded = 0;
		for(int i=0; i<number14Connections; i++){

			atom1 = pairs14.get(i)[0].indexInRes;//m.atom[m.connected14[ix4]].moleculeAtomNumber;
			atom4 = pairs14.get(i)[1].indexInRes;//m.atom[m.connected14[ix4 + 3]].moleculeAtomNumber;
			atomType1 = res1.atoms.get(atom1).type;
			atomType4 = res2.atoms.get(atom4).type;
			isHydrogen1 = res1.atoms.get(atom1).elementType.equalsIgnoreCase("H");
			isHydrogen2 = res2.atoms.get(atom4).elementType.equalsIgnoreCase("H");
			
			double epsilonProduct = 0, ri = 0, rj = 0;
			if (!(params.getNonBondedParameters(atomType1, equilibriumDistance, epsilon)))
				System.out.println("WARNING: Could not find nb parameters for " + atom1 + " type: " + res1.atoms.get(atom1).forceFieldType);
			else {
				if((params.forcefld == ForcefieldParams.FORCEFIELD.CHARMM19 
						|| params.forcefld == ForcefieldParams.FORCEFIELD.CHARMM19NEUTRAL )
						&& pairs14.get(i)[0].elementType.equalsIgnoreCase("C")){
					//KER: if charmm19 then reduce C radii for 1-4 interactions
					epsilonProduct = 0.1;
					ri = 1.9;
				}
				else{
					epsilonProduct = epsilon[0];
					ri = equilibriumDistance[0];
				}
				if (!(params.getNonBondedParameters(atomType4, equilibriumDistance, epsilon)))
					System.out.println("WARNING: Could not find nb parameters for " + atom4 + " type: " + res2.atoms.get(atom1).forceFieldType);
				else {
					if((params.forcefld == ForcefieldParams.FORCEFIELD.CHARMM19 
							|| params.forcefld == ForcefieldParams.FORCEFIELD.CHARMM19NEUTRAL )
							&& pairs14.get(i)[0].elementType.equalsIgnoreCase("C")){
						//KER: if charmm19 then reduce C radii for 1-4 interactions
						epsilonProduct *= 0.1;
						rj = 1.9;
					}
					else{
						epsilonProduct *= epsilon[0];
						rj = equilibriumDistance[0];
					}
					epsilonProduct = Math.sqrt(epsilonProduct);
					// This part is 1-4 interactions which are scaled by 1/2
					double Bij = ( ri + rj ) * ( ri + rj );
					Bij = Bij * Bij * Bij;
					double Aij = Bij * Bij;
					switch(params.forcefld){
						case AMBER:
							Aij *= epsilonProduct * 0.5;
							Bij *= epsilonProduct;
							break;
						case CHARMM19: 
						case CHARMM19NEUTRAL:
							Aij *= epsilonProduct;
							Bij *= epsilonProduct * 2.0;
							// Aij = (ri+rj)^12 * sqrt(ei*ej)
							// Bij = (ri+rj)^6 * sqrt(ei*ej) * 2.0
							break;
						default:
							throw new Error("Don't know how to handle FF " + params.forcefld);
					}
					// Aij = (ri+rj)^12 * sqrt(ei*ej) * 0.5
					// Bij = (ri+rj)^6 * sqrt(ei*ej)
					
					halfNonBondedTerms[5*i] = atom1;
					halfNonBondedTerms[5*i + 1] = atom4;
					halfNonBondedTerms[5*i + 2] = isHydrogen1 || isHydrogen2 ? 1 : 0;
					halfNonBondedTerms[5*i + 3] = Aij*Amult;
					halfNonBondedTerms[5*i + 4] = Bij*Bmult;
					numberHalfNonBonded++;
				}
			}
		}
		
		// Reduce the size of the halfNonBondedTerms to the size we actually used
		smallerArray = new double[numberHalfNonBonded * 5];
		System.arraycopy(halfNonBondedTerms, 0, smallerArray, 0, numberHalfNonBonded * 5);
		halfNonBondedTerms = smallerArray;
		
		if (debug)
			System.out.println("Final number of halfNonBondedTerms: " + numberHalfNonBonded);

		
		//-------------------------------
		// non bonded terms
		//-------------------------------
		// 5 terms: atom1, atom2, isHydrogen, Aij, Bij
		
		int numberNonBondedConnections = pairsNonBonded.size();
		
		if (debug)
			System.out.println("Initial number of full nonbonded pairs: " + numberNonBondedConnections);
		
		nonBondedTerms = new double[numberNonBondedConnections * 5];
		numberNonBonded = 0;
		for(int i=0; i<numberNonBondedConnections; i++) {
			
			atom1 = pairsNonBonded.get(i)[0].indexInRes;
			atom2 = pairsNonBonded.get(i)[1].indexInRes;
			atomType1 = res1.atoms.get(atom1).type;
			atomType2 = res2.atoms.get(atom2).type;
			isHydrogen1 = res1.atoms.get(atom1).elementType.equalsIgnoreCase("H");
			isHydrogen2 = res2.atoms.get(atom2).elementType.equalsIgnoreCase("H");
			
			if (!(params.getNonBondedParameters(atomType1, equilibriumDistance, epsilon)))
				System.out.println("WARNING: Could not find nb parameters for (at1) " + atom1 + " type: " + res1.atoms.get(atom1).forceFieldType);
			else {
				double epsilonProduct = epsilon[0];
				double ri = equilibriumDistance[0];
				if (!(params.getNonBondedParameters(atomType2, equilibriumDistance, epsilon)))
					System.out.println("WARNING: Could not find nb parameters for (at2) " + atom2 + " type: " + res2.atoms.get(atom2).forceFieldType);
				else {						
					epsilonProduct *= epsilon[0];
					double rj = equilibriumDistance[0];
					epsilonProduct = Math.sqrt(epsilonProduct);
					double Bij = ( ri + rj ) * ( ri + rj );
					Bij = Bij * Bij * Bij;
					double Aij = Bij * Bij * epsilonProduct;
					Bij *= epsilonProduct * 2.0;
					// Aij = (ri+rj)^12 * sqrt(ei*ej)
					// Bij = (ri+rj)^6 * sqrt(ei*ej) * 2
					nonBondedTerms[5*i] = atom1;
					nonBondedTerms[5*i + 1] = atom2;
					nonBondedTerms[5*i + 2] = isHydrogen1 || isHydrogen2 ? 1 : 0;
					nonBondedTerms[5*i + 3] = Aij*Amult;
					nonBondedTerms[5*i + 4] = Bij*Bmult;
					numberNonBonded++;
				}
			}
		}
		
		// Reduce the size of the nonBondedTerms to the size we actually used
		smallerArray = new double[numberNonBonded * 5];
		System.arraycopy(nonBondedTerms, 0, smallerArray, 0, numberNonBonded * 5);
		nonBondedTerms = smallerArray;
		
		if (debug)
			System.out.println("Final number of full nonbonded pairs: " + numberNonBonded);
	}
	
	// This function sets up the arrays for energy evaluation
	//  for solvation energies only
	// Terms involving residues with energyEval == false
	//  are not included
	// Since EEF1 handles only natural amino acids, we compute solvation 
	//	energies for proteins and ligands (natural amino acids) only, and
	//	not for cofactors. To determine if an atom belongs to a protein or a ligand,
	//	we use the isProtein flag of the Strand class. In KSParser, this flag is
	//	set to true for the protein and the ligand, but not for the cofactor
	private void initializeSolvationCalculation(ArrayList<Atom> atoms1, ArrayList<Atom> atoms2){
		
		if (debug)
			System.out.println("Starting initializeSolvationCalculation");
		
		final double solvCoeff = 2/(4*Math.PI*Math.sqrt(Math.PI));
		
		// Setup an array of 6 terms: 1=atom1(moleculeAtomNumber), 2=dG(ref), 3=dG(free), 4=volume, 5=lambda,
		//  6=vdW radius
		double[] solvationTerms1 = listSolvationTerms(atoms1);
		double[] solvationTerms2 = listSolvationTerms(atoms2);
		int num1 = solvationTerms1.length/6;
		int num2 = solvationTerms2.length/6;
		
		// allocate space for combined solvation terms
		// this is probably too much space, we'll resize later
		int numSolvationConnections = num1*num2;
		solvationTerms = new double[numSolvationConnections*8];
		
		//Determine which pairs of atoms can be excluded from the solvation energy computation
		// collapse the rest into a 1d array, like the non bonded terms
		numberSolvated = 0;
		for (int i=0; i<num1; i++) {
			
			int ix6 = i*6;
			int atomi = (int)solvationTerms1[ix6];
			Atom ati = res1.atoms.get(atomi);
			AtomNeighbors iNeighbors = new AtomNeighbors(ati);
			
			for (int j=0; j<num2; j++){
				
				if(isInternal){//for internal energies,
					//need to avoid double-counting, and don't include self-energies here
					if(j<=i){
						continue;
					}
				}

				int jx6 = j*6;
				int atomj = (int)solvationTerms2[jx6];
				Atom atj = res2.atoms.get(atomj);

				AtomNeighbors.NEIGHBORTYPE ijType = iNeighbors.classifyAtom(atj);

				if (ijType==AtomNeighbors.NEIGHBORTYPE.BONDED12 ||  
					ijType==AtomNeighbors.NEIGHBORTYPE.BONDED13) {
					//exclude solvation interactions for 1,2- or 1,3-bonded pairs
					continue;
				}
				
				// add the pair to the solvation terms
				int kx8 = numberSolvated*8;
				
				double dGiFree = solvationTerms1[ix6 + 2];
				double Vi = solvationTerms1[ix6 + 3];
				double Li = solvationTerms1[ix6 + 4];
				double vdWi = solvationTerms1[ix6 + 5];
				
				double dGjFree = solvationTerms2[jx6 + 2];
				double Vj = solvationTerms2[jx6 + 3];
				double Lj = solvationTerms2[jx6 + 4];
				double vdWj = solvationTerms2[jx6 + 5];
				
				double alpha_i = solvCoeff * dGiFree * Vj / Li;
				double alpha_j = solvCoeff * dGjFree * Vi / Lj;
				
				solvationTerms[kx8 + 0] = atomi;
				solvationTerms[kx8 + 1] = Li;
				solvationTerms[kx8 + 2] = vdWi;
				solvationTerms[kx8 + 3] = alpha_i;
				
				solvationTerms[kx8 + 4] = atomj;
				solvationTerms[kx8 + 5] = Lj;
				solvationTerms[kx8 + 6] = vdWj;
				solvationTerms[kx8 + 7] = alpha_j;
				
				numberSolvated++;
			}
		}
		
		// resize the solvation terms array to fit
		double[] smaller = new double[numberSolvated*8];
		System.arraycopy(solvationTerms, 0, smaller, 0, numberSolvated*8);
		solvationTerms = smaller;
		
		// compute the internal solvation energy term
		internalSolvEnergy = 0;
		for (int i=0; i<num1; i++) {
			internalSolvEnergy += solvationTerms1[i*6 + 1];
		}
	}
        
	private double[] listSolvationTerms(ArrayList<Atom> atomList){
		//list the solvation terms (atom numbers within residue, and params) for the specified atoms
		//in the specified residue
		
		double termList[] = new double[atomList.size() * 6];

		int ix6 = -6;
		int numTerms = 0;
		for(int i=0; i<atomList.size(); i++){
				
			int atom1 = atomList.get(i).indexInRes;

			if (!atomList.get(i).elementType.equalsIgnoreCase("H")){//solvation terms do not include H

				ix6 += 6;

				double dGref[] = new double[1];
				double dGfree[] = new double[1];
				double atVolume[] = new double[1];
				double lambda[] = new double[1];
				double vdWradiusExt[] = new double[1]; //extended vdWradius (uses the EEF1 parameters)

				if (!(params.eef1parms.getSolvationParameters(atomList.get(i),dGref,
					dGfree,atVolume,lambda,vdWradiusExt))){
				
					throw new RuntimeException("WARNING: Could not find solvation parameters for atom: " 
						+ atom1 + " (" + atomList.get(i).name+") res: " + atomList.get(i).res.fullName);
				}
				else {

					termList[ix6] = atom1;
					termList[ix6 + 1] = dGref[0];
					termList[ix6 + 2] = dGfree[0];
					termList[ix6 + 3] = atVolume[0];
					termList[ix6 + 4] = lambda[0];
					termList[ix6 + 5] = vdWradiusExt[0];
					numTerms++;
				}
			}
		}

		// Shrink the dihedralAngleTerms array down
		double[] smallerArray = new double[numTerms * 6];
		System.arraycopy(termList,0,smallerArray,0,numTerms*6);
		return smallerArray;
	}
	

//////////////////////////////////////////////////////////////////////////////////////////////////
//	This section calculates the energy of the given system
//////////////////////////////////////////////////////////////////////////////////////////////////	
	//Evaluate this energy for the sets of interacting atoms
	//use the coordinates in res1 and res2
	//Depending on the flags, different types of energies are included/excluded
	public double calculateTotalEnergy() {
		
		int atomix4, atomjx4, atomi, atomj;
		int ix5, ix8;
		double rij, rij2, rij6, rij12;
		double rijx, rijy, rijz;
		double chargei, chargej, Aij, Bij;
		double coulombFactor, tmpCoulFact;
		boolean isHydrogen, isHeavy;
		
		
		//--------------------------------------------
		// check the cache, should we even compute energy?
		//--------------------------------------------
		
		// did the atoms change?
		boolean isChanged = atomCache.updateCoords();
		if (!isChanged) {
			return energyCache;
		}
		
		
		//--------------------------------------------
		// compute electrostatics and vdW energies
		//--------------------------------------------
		// OPTIMIZATION: this used to be a separate function, but inlining it helps us go faster
		
		// This function calculates the electrostatic and vdw (EV) energy of a system
		// Energy values are 'R'eturned in EenergyR and VenergyR
		// vdwMultiplier is the soft-potential multiplier, if equal to 1.0 it has no effect,
		//  values <1.0 allow for slight overpacking
		// Makes use of the halfNBeval and NBeval arrays
		//   0 - compute neither elect nor vdw terms
		//   1 - compute both elect and vdw terms
		//   2 - compute only elect term
		//   3 - compute only vdw term
		
		double esEnergy = 0;
		double vdwEnergy = 0;
		
		// OPTIMIZING: apparently copying these values/references to the stack
		//             gives a small but noticeable performance speedup
		int numberHalfNonBonded = this.numberHalfNonBonded;
		double[] halfNonBondedTerms = this.halfNonBondedTerms;
		int numberNonBonded = this.numberNonBonded;
		double[] nonBondedTerms = this.nonBondedTerms;
		double[] data = this.atomCache.data;
		int res1Start = this.atomCache.res1Start;
		int res2Start = this.atomCache.res2Start;
		boolean useHydrogenEs = this.useHydrogenEs;
		boolean useHydrogenVdw = this.useHydrogenVdw;
		boolean distDepDielect = this.distDepDielect;
		boolean isInternal = this.isInternal;
		double solvCutoff2 = this.solvCutoff*this.solvCutoff;
		int numberSolvated = this.numberSolvated;
		double[] solvationTerms = this.solvationTerms;
		
		// shortcuts
		boolean useHydrogenNeither = !useHydrogenEs && !useHydrogenVdw;
		
		// half non-bonded terms
		// 1-4 electrostatic terms are scaled by 1/1.2
		switch(params.forcefld){
			case AMBER:
				coulombFactor = (constCoulomb/1.2) / (dielectric);
				break;
			case CHARMM19:
			case CHARMM19NEUTRAL:
				coulombFactor = (constCoulomb * 0.4) / (dielectric);
				break;
			default:
				coulombFactor = 0;
				System.out.println("FORCEFIELD NOT RECOGNIZED!!!");
				System.exit(0);
				break;
		}
		
		ix5 = -5;
		for(int i=0; i<numberHalfNonBonded; i++) {
			ix5 += 5;
			
			isHydrogen = halfNonBondedTerms[ix5 + 2] == 1;
			if (isHydrogen && useHydrogenNeither) {
				continue;
			}
			isHeavy = !isHydrogen;
			
			// read table parts
			atomi = (int)halfNonBondedTerms[ix5];
			atomj = (int)halfNonBondedTerms[ix5 + 1];
			Aij = halfNonBondedTerms[ix5 + 3];
			Bij = halfNonBondedTerms[ix5 + 4];
			
			// read coords
			atomix4 = atomi * 4;
			atomjx4 = atomj * 4;
			rijx = data[res1Start + atomix4] - data[res2Start + atomjx4];
			rijy = data[res1Start + atomix4 + 1] - data[res2Start + atomjx4 + 1];
			rijz = data[res1Start + atomix4 + 2] - data[res2Start + atomjx4 + 2];
			chargei = data[res1Start + atomix4 + 3];
			chargej = data[res2Start + atomjx4 + 3];

			// shared math
			rij2 = rijx * rijx + rijy * rijy + rijz * rijz;

			if (isHeavy || useHydrogenEs) {
				
				// electrostatics only math
				// OPTIMIZATION: something like 35% of our work is spent evaluating sqrts
				rij = Math.sqrt(rij2);
				tmpCoulFact = coulombFactor;
				if (distDepDielect) //distance-dependent dielectric
					tmpCoulFact /= rij;
				
				esEnergy += (chargei * chargej * tmpCoulFact) / rij;
			}

			if (isHeavy || useHydrogenVdw) {
				
				// vdW only math
				rij6 = rij2 * rij2 * rij2;
				rij12 = rij6 * rij6;
				
				vdwEnergy += Aij / rij12 - Bij / rij6;
			}
		}

		// The full nonbonded electrostatic terms are NOT scaled down by 1/1.2
		coulombFactor = constCoulomb / (dielectric);
		
		// OPTIMIZATION: non-bonded terms usually far outnumber the other terms
		ix5 = -5;
		for(int i=0; i<numberNonBonded; i++) {
			ix5 += 5;
			
			isHydrogen = nonBondedTerms[ix5 + 2] == 1;
			if (isHydrogen && useHydrogenNeither) {
				continue;
			}
			isHeavy = !isHydrogen;
			
			// read table parts
			atomi = (int)nonBondedTerms[ix5];
			atomj = (int)nonBondedTerms[ix5 + 1];
			Aij = nonBondedTerms[ix5 + 3];
			Bij = nonBondedTerms[ix5 + 4];
			
			// read coords
			atomix4 = atomi * 4;
			atomjx4 = atomj * 4;
			rijx = data[res1Start + atomix4] - data[res2Start + atomjx4];
			rijy = data[res1Start + atomix4 + 1] - data[res2Start + atomjx4 + 1];
			rijz = data[res1Start + atomix4 + 2] - data[res2Start + atomjx4 + 2];
			chargei = data[res1Start + atomix4 + 3];
			chargej = data[res2Start + atomjx4 + 3];
			
			// shared math
			rij2 = rijx * rijx + rijy * rijy + rijz * rijz;
			
			if (isHeavy || useHydrogenEs) {
				
				// electrostatics only math
				// OPTIMIZATION: something like 35% of our work is spent evaluating sqrts
				rij = Math.sqrt( rij2 );
				tmpCoulFact = coulombFactor;
				if (distDepDielect) //distance-dependent dielectric
					tmpCoulFact /= rij;
				
				esEnergy += (chargei * chargej * tmpCoulFact) / rij;
			}

			if (isHeavy || useHydrogenVdw) {
				
				// vdw only math
				rij6 = rij2 * rij2 * rij2;
				rij12 = rij6 * rij6;
				
				vdwEnergy += Aij / rij12 - Bij / rij6;
			}
		}
		
		// not doing solvation? we're done
		if (!doSolvationE) {
			energyCache = esEnergy + vdwEnergy;
			checkEnergy(energyCache);
			return energyCache;
		}
		
		//--------------------------------------------
		// compute solvation energies
		//--------------------------------------------
		// OPTIMIZATION: this used to be a separate function, but inlining it helps us go faster
		
		double solvEnergy = 0;
		if (isInternal) {
			solvEnergy += internalSolvEnergy;
		}
		
		double lambda_i, vdWr_i, alpha_i;
		double lambda_j, vdWr_j, alpha_j;
		double Xij, Xji;
		
		ix8 = -8;
		for (int i=0; i<numberSolvated; i++) {
			ix8 += 8;
			
			// read the table parts
			atomi = (int)solvationTerms[ix8];
			atomj = (int)solvationTerms[ix8 + 4];
			
			// compute distance
			atomix4 = atomi * 4;
			atomjx4 = atomj * 4;
			rijx = data[res1Start + atomix4] - data[res2Start + atomjx4];
			rijy = data[res1Start + atomix4 + 1] - data[res2Start + atomjx4 + 1];
			rijz = data[res1Start + atomix4 + 2] - data[res2Start + atomjx4 + 2];
			rij2 = rijx * rijx + rijy * rijy + rijz * rijz;
			
			if (rij2 < solvCutoff2) {
				
				lambda_i = solvationTerms[ix8 + 1];
				vdWr_i = solvationTerms[ix8 + 2];
				alpha_i = solvationTerms[ix8 + 3];
				
				lambda_j = solvationTerms[ix8 + 5];
				vdWr_j = solvationTerms[ix8 + 6];
				alpha_j = solvationTerms[ix8 + 7];
				
				// OPTIMIZATION: something like 35% of our work is spent evaluating sqrts
				rij = Math.sqrt(rij2);
				Xij = (rij-vdWr_i)/lambda_i;
				Xji = (rij-vdWr_j)/lambda_j;
				
				solvEnergy -= (alpha_i*Math.exp(-Xij*Xij) + alpha_j*Math.exp(-Xji*Xji))/rij2;
			}
		}
		
		solvEnergy *= solvScale;
			
		// finally, we're done
		energyCache = esEnergy + vdwEnergy + solvEnergy;
		checkEnergy(energyCache);
		return energyCache;
	}
	
	private void checkEnergy(double energy) {
		if(Double.isNaN(energy)){
			
			for(double a : res1.coords){
				if(Double.isNaN(a))
					throw new RuntimeException("ERROR: NaN coordinates provided to ForcefieldEnergy");
				if(Double.isInfinite(a))
					throw new RuntimeException("ERROR: Infinite coordinates provided to ForcefieldEnergy");
			}
			
			for(double a : res2.coords){
				if(Double.isNaN(a))
					throw new RuntimeException("ERROR: NaN coordinates provided to ForcefieldEnergy");
				if(Double.isInfinite(a))
					throw new RuntimeException("ERROR: Infinite coordinates provided to ForcefieldEnergy");
			}
			
			throw new RuntimeException("ERROR: NaN returned by ForcefieldEnergy.  No NaN or infinite coordinates.");
		}
	}

        //probably want to handle gradient at some point...below is from Amber96ext
        //but we'll probably want to be returning the gradient from a particular term
        //which will be converted to derivatives with respect to minimization degrees of freedom
	/*
	//Computes the gradient of the different energy terms;
	//The computed gradient is in the molecule's gradient member variable
	//The parameter curIndex specifies the row in the partial arrays
	//		(the corresponding flexible residue);
	//If (curIndex==-1), then the full gradient is computed
	public void calculateGradient(int curIndex){
		
		// clear the gradient		
		m.gradient = new double[m.numberOfAtoms * 3];
		for(int i=0; i<m.numberOfAtoms*3;i ++){
			m.gradient[i]=0;
		}
		
		calculateEVGradient(curIndex); //compute electrostatic and vdW energies
		
		if (doSolvationE) //compute solvation energies
			calculateSolvationGradient(curIndex);
	}
	
	// This code computes the gradient of the electrostatic and vdw energy terms
	// The computed gradient is in the molecule's gradient member variable
	private void calculateEVGradient(int curIndex){
		
		int ix4;
		double coulombFactor;
		int atomi, atomj, atomix3, atomjx3;
		double Aij, Bij, rij6, rij7, rij8, rij14;
		double chargei, chargej, coulombTerm;
		double rijx, rijy, rijz, rij2, rij, rij3;
		double term1, term2, term3;
		double forceix, forceiy, forceiz, forcejx, forcejy, forcejz;
		
		int numHalfNBterms = 0; int numNBterms = 0;
		double halfNBterms[] = null; double nbTerms[] = null;
		
		if (curIndex==-1){ //full gradient is computed
			numHalfNBterms = numHalfNonBondedTerms;
			halfNBterms = halfNonBondedTerms;
			numNBterms = numberNonBonded;
			nbTerms = nonBondedTerms;
		}
		else { //partial gradient is computed, based on flexible residue curIndex
			numHalfNBterms = numPartHalfNonBonded[curIndex];
			halfNBterms = partHalfNonBonded[curIndex];
			numNBterms = numPartNonBonded[curIndex];
			nbTerms = partNonBonded[curIndex];
		}

		// Note: Bmult = vdwMultiplier^6 and Amult = vdwMultiplier^12
		double Bmult; double Amult;
		Bmult = vdwMultiplier * vdwMultiplier;
		Bmult = Bmult*Bmult*Bmult;
		Amult = Bmult*Bmult;
		
		// compute gradient for 1/2 non-bonded terms
		ix4 = -4;
		//coulombFactor = (constCoulomb/2.0) / (dielectric);
		//KER: Made change to 1.2
		switch(EnvironmentVars.forcefld){
			case AMBER: 
				coulombFactor = (constCoulomb/1.2) / dielectric;
				break;
			case CHARMM19:
			case CHARMM19NEUTRAL:
				coulombFactor = (constCoulomb*0.4) / dielectric;
				break;
			default:
				coulombFactor = 0;
				System.out.println("FORCEFIELD NOT RECOGNIZED!!!");
				System.exit(0);
				break;
		}
		double tmpCoulFact;
		for (int i=0; i<numHalfNBterms; i++){
			ix4 += 4;
			atomi = (int)halfNBterms[ix4];
			atomj = (int)halfNBterms[ix4 + 1];
			Aij = halfNBterms[ix4 + 2]*Amult;
			Bij = halfNBterms[ix4 + 3]*Bmult;
			chargei = m.atom[atomi].charge;
			chargej = m.atom[atomj].charge;
			atomix3 = atomi * 3;
			atomjx3 = atomj * 3;
			rijx = m.actualCoordinates[atomix3] - 
				m.actualCoordinates[atomjx3];
			rijy = m.actualCoordinates[atomix3 + 1] - 
				m.actualCoordinates[atomjx3 + 1];
			rijz = m.actualCoordinates[atomix3 + 2] - 
				m.actualCoordinates[atomjx3 + 2];
			rij2 = rijx * rijx + rijy * rijy + rijz * rijz;
			if (rij2 < 1.0e-2)
				rij2 = 1.0e-2;	
			rij = Math.sqrt(rij2);
			rij3 = rij2 * rij;
			rij6 = rij3 * rij3;
			rij7 = rij6 * rij;
			rij8 = rij7 * rij;
			rij14 = rij7 * rij7;
			
			tmpCoulFact = coulombFactor;
			if (distDepDielect) //distance-dependent dielectric
				tmpCoulFact = (tmpCoulFact * 2) / rij;
			
			coulombTerm = (chargei * chargej * tmpCoulFact) / rij3;
			term1 = 12 * Aij / rij14;
			term2 = 6 * Bij / rij8;
			term3 = term1 - term2 + coulombTerm;
			forceix = term3 * rijx;
			forceiy = term3 * rijy;
			forceiz = term3 * rijz;
			forcejx = -forceix;
			forcejy = -forceiy;
			forcejz = -forceiz;
			m.gradient[atomix3] -= forceix;
			m.gradient[atomix3 + 1] -= forceiy;
			m.gradient[atomix3 + 2] -= forceiz;
			m.gradient[atomjx3] -= forcejx;
			m.gradient[atomjx3 + 1] -= forcejy;
			m.gradient[atomjx3 + 2] -= forcejz;
		}

		// Full non bonded terms
		ix4 = -4;
		coulombFactor = constCoulomb / (dielectric);
		for(int i=0; i<numNBterms; i++){
			ix4 += 4;
			atomi = (int)nbTerms[ix4];
			atomj = (int)nbTerms[ix4 + 1];
			Aij = nbTerms[ix4 + 2]*Amult;
			Bij = nbTerms[ix4 + 3]*Bmult;
			chargei = m.atom[atomi].charge;
			chargej = m.atom[atomj].charge;
			atomix3 = atomi * 3;
			atomjx3 = atomj * 3;
			rijx = m.actualCoordinates[atomix3] - 
				m.actualCoordinates[atomjx3];
			rijy = m.actualCoordinates[atomix3 + 1] - 
				m.actualCoordinates[atomjx3 + 1];
			rijz = m.actualCoordinates[atomix3 + 2] - 
				m.actualCoordinates[atomjx3 + 2];
			rij2 = rijx * rijx + rijy * rijy + rijz * rijz;
			if (rij2 < 1.0e-2)
				rij2 = 1.0e-2;	
			rij = Math.sqrt(rij2);
			rij3 = rij2 * rij;
			rij6 = rij3 * rij3;
			rij7 = rij6 * rij;
			rij8 = rij7 * rij;
			rij14 = rij7 * rij7;
			
			tmpCoulFact = coulombFactor;
			if (distDepDielect) //distance-dependent dielectric
				tmpCoulFact = (tmpCoulFact * 2) / rij;
			
			coulombTerm = (chargei * chargej * coulombFactor) / rij3;
			term1 = 12 * Aij / rij14;
			term2 = 6 * Bij / rij8;
			term3 = term1 - term2 + coulombTerm;
			forceix = term3 * rijx;
			forceiy = term3 * rijy;
			forceiz = term3 * rijz;
			forcejx = -forceix;
			forcejy = -forceiy;
			forcejz = -forceiz;
			m.gradient[atomix3] -= forceix;
			m.gradient[atomix3 + 1] -= forceiy;
			m.gradient[atomix3 + 2] -= forceiz;
			m.gradient[atomjx3] -= forcejx;
			m.gradient[atomjx3 + 1] -= forcejy;
			m.gradient[atomjx3 + 2] -= forcejz;
		}  
	}
	
	//Computes the gradient for the solvation energy term;
	//The computed gradient is in the molecules gradient member variable
	private void calculateSolvationGradient(int curIndex) {
		
		double forceix, forceiy, forceiz;
		int atomix3, atomjx3, atomi, atomj;
		double rij, rij2, rij3;
		double rijx, rijy, rijz;
		double tempTerm_i;
		int indMult = 0;
		
		int numSolvTerms = 0;
		double solvTerms[] = null;
		
		if (curIndex==-1){ //full energy is computed
			numSolvTerms = numSolvationTerms;
			solvTerms = solvationTerms;
			indMult = 6;
		}
		else { //partial energy is computed, based on flexible residue curIndex
			numSolvTerms = numPartSolv[curIndex];
			solvTerms = partSolv[curIndex];
			indMult = 7;
		}
		
		for ( int i = 0; i < numSolvTerms; i++ ){

			atomi = (int)solvTerms[ i*indMult ];
			atomix3 = atomi * 3;
			
			double dGi_free = solvTerms[i*indMult+2]; //dGi(free)
			double V_i = solvTerms[i*indMult+3]; //Vi
			double lambda_i = solvTerms[i*indMult+4]; //lambdai
			double vdWr_i = solvTerms[i*indMult+5]; //vdWri
			
			int startInd = i;
			if (curIndex!=-1)
				startInd = (int)solvTerms[i*indMult+6];
			
			forceix = 0.0;forceiy = 0.0; forceiz = 0.0;
			for (int j=0; j<numSolvationTerms; j++){ //the pairwise solvation energies
				
				if (j!=startInd){
				
					atomj = (int)solvTerms[j*6];
					atomjx3 = atomj*3;
					
					//atoms 1 or 2 bonds apart are excluded from each other's calculation of solvation free energy
					if (!solvExcludePairs[startInd][j]) {
						
						rijx = m.actualCoordinates[ atomix3 ] - m.actualCoordinates[ atomjx3 ];
						rijy = m.actualCoordinates[ atomix3 + 1 ] - m.actualCoordinates[ atomjx3 + 1 ];
						rijz = m.actualCoordinates[ atomix3 + 2 ] - m.actualCoordinates[ atomjx3 + 2 ];
						rij2 = rijx * rijx + rijy * rijy + rijz * rijz;
						rij = Math.sqrt( rij2 ); //distance between the two atoms
						rij3 = rij2 * rij;
						
						if (rij < solvCutoff){
							
							double dGj_free = solvationTerms[j*6+2]; //dGj(free)
							double V_j = solvationTerms[j*6+3]; //Vj
							double lambda_j = solvationTerms[j*6+4]; //lambdaj
							double vdWr_j = solvationTerms[j*6+5]; //vdWrj
						
							double coeff = 1/(Math.PI*Math.sqrt(Math.PI));
							
							double Xij = (rij-vdWr_i)/lambda_i;
							double Xji = (rij-vdWr_j)/lambda_j;
							
							double Vj_coeff = Xij/lambda_i + 1/rij;
							double Vi_coeff = Xji/lambda_j + 1/rij;
							
							tempTerm_i = ( (coeff * dGi_free * Math.exp(-Xij*Xij) * Vj_coeff * V_j) / (lambda_i * rij3)
										+ (coeff * dGj_free * Math.exp(-Xji*Xji) * Vi_coeff * V_i) / (lambda_j * rij3) ) ;
							
							forceix += tempTerm_i * rijx;
							forceiy += tempTerm_i * rijy;
							forceiz += tempTerm_i * rijz;
						}
					}
				}
			}		
			
			m.gradient[ atomix3 ] += solvScale*forceix;
			m.gradient[ atomix3 + 1 ] += solvScale*forceiy;
			m.gradient[ atomix3 + 2 ] += solvScale*forceiz;
		}
	}
//////////////////////////////////////////////////////////////////////////////////////////////////
	*/


    
}
