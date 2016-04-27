/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.astar.partfunc;

import edu.duke.cs.osprey.astar.AStarNode;
import edu.duke.cs.osprey.astar.AStarTree;
import edu.duke.cs.osprey.astar.ConfTree;
import edu.duke.cs.osprey.astar.Mplp;
import edu.duke.cs.osprey.astar.comets.UpdatedPruningMatrix;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.energy.PoissonBoltzmannEnergy;
import edu.duke.cs.osprey.partitionfunctionbounds.MapPerturbation;
import edu.duke.cs.osprey.partitionfunctionbounds.MarkovRandomField;
import edu.duke.cs.osprey.partitionfunctionbounds.ReparamMRF;
import edu.duke.cs.osprey.partitionfunctionbounds.SCMF_Clamp;
import edu.duke.cs.osprey.partitionfunctionbounds.TRBP_Refactor_2;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import edu.duke.cs.osprey.tools.ExpFunction;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;

/**
 *
 * @author hmn5
 */
public class PartFuncTree extends AStarTree {

    double expNormalizer = 0.0;

    BigDecimal lbZ;
    BigDecimal ubZ;

    BigDecimal runningSum;
    double runningSumExpNormalizer = 0.0;

    double epsilon = 0.1;

    int numPos;

    EnergyMatrix emat;
    PruningMatrix pruneMat;

    final double eCut = 0.0; //if we want to set an energy cutoff for MRFs

    boolean useMapPert = false;
    boolean useMPLP = false;

    int numSamples = 5; //number of sample averages to take the max from;

    ExpFunction ef = new ExpFunction();
    double constRT = PoissonBoltzmannEnergy.constRT;

    boolean useDynamicOrdering = true;
    boolean branchHalfSpace = true;
    boolean useTRBPWeightForOrder = true;
    Mplp mplp;

    boolean verbose = true;

    public int numConfsEnumerated = 0;

    //Should we use the edge probabilites from the parent node to "seed" the
    //edge probabilities of the child
    boolean useParentEdgeProbTRBP = false;

    public PartFuncTree(SearchProblem sp) {
        init(sp, sp.pruneMat, sp.useEPIC);
    }

    public PartFuncTree(SearchProblem sp, PruningMatrix aPruneMat, boolean useEPIC) {
        //Conf search over RC's in sp that are unpruned in pruneMat
        init(sp, aPruneMat, useEPIC);
    }

    //For rigid traditional
    public PartFuncTree(EnergyMatrix aEmat, PruningMatrix aPruneMat) {
        this.numPos = aEmat.numPos();
        this.emat = aEmat;
        this.pruneMat = aPruneMat;

        this.lbZ = new BigDecimal("0.0");
        this.ubZ = new BigDecimal("0.0");
        this.runningSum = new BigDecimal("0.0");
        mplp = new Mplp(numPos, aEmat, aPruneMat);
    }

    private void init(SearchProblem sp, PruningMatrix aPruneMat, boolean useEPIC) {
        numPos = sp.confSpace.numPos;
        this.lbZ = new BigDecimal("0.0");
        this.ubZ = new BigDecimal("0.0");
        this.runningSum = new BigDecimal("0.0");

        this.pruneMat = aPruneMat;
        //get the appropriate energy matrix to use in this A* search
        if (sp.useTupExpForSearch) {
            emat = sp.tupExpEMat;
            throw new RuntimeException("partFuncTree does not yet support tupExpEmat");
        } else {
            emat = sp.emat;

            if (useEPIC) {//include EPIC in the search
                throw new RuntimeException("partFuncTree does not yet support EPIC");
            }
        }
        mplp = new Mplp(numPos, emat, this.pruneMat);
    }

    @Override
    public ArrayList<AStarNode> getChildren(AStarNode curNode) {
        PartFuncNode node = (PartFuncNode) curNode;
        ArrayList<AStarNode> children = new ArrayList<>();

        int posMaxWeight = -1;
        double maxWeight = Double.NEGATIVE_INFINITY;
        for (int pos = 0; pos < node.nodeWeights.length; pos++) {
            double weight = node.nodeWeights[pos];
            if ((weight > maxWeight) && node.getNodeAssignments()[pos] < 0) {
                maxWeight = weight;
                posMaxWeight = pos;
            }
        }
        System.out.println("Pos With Best Weight: " + posMaxWeight);
//        subtractFromBounds(node);
        subtractLowerBound(node);
        int[] curAssignments = node.getNodeAssignments();

        int splitPos;
        if (useDynamicOrdering && useTRBPWeightForOrder) {
            splitPos = posMaxWeight;
        } else {
            splitPos = nextLevelToExpand(curAssignments);
        }
        System.out.println("Splitting at Level: " + splitPos);
        System.out.println("*****************************");
        for (int rot : this.pruneMat.unprunedRCsAtPos(splitPos)) {
            int[] childAssignments = curAssignments.clone();
            childAssignments[splitPos] = rot;

            PartFuncNode childNode = new PartFuncNode(childAssignments);
            if (!nodeIsDefined(childNode)) {
                childNode.setLowerBoundLogZ(computeLowerBound(childNode));
//                childNode.setUpperBoundLogZ(computeUpperBound(childNode));
//                childNode.setScore(scoreNode(childNode));
                updateLowerBound(childNode);

                children.add(childNode);
            } else {//child node is leaf so we compute exact score
                double confE = getConfE(childNode);
                updateRunningSumLB(confE);
                numConfsEnumerated++;
            }
        }
//        printEffectiveEpsilon();
//        System.out.println("Lower Bound: " + this.ef.log(lbZ.add(runningSum)).doubleValue());
        System.out.println("Upper Bound: " + this.ef.log(ubZ.add(runningSum)).doubleValue());
        if (!isFullyAssigned(curNode)) {
            subtractUpperBound(node);
            //Now update upper bounds
//            System.out.println("Computing Uppber Bound For " + children.size() + " children");
            int childNum = 1;
            for (AStarNode childNode : children) {
//                System.out.println();
//                System.out.println("TRBP For Child: " + childNum);
                PartFuncNode child = (PartFuncNode) childNode;
                child.setUpperBoundLogZ(computeUpperBound(child, node));
                updateUpperBound(child);
                child.setScore(scoreNode(child));
                childNum++;
            }
        }
        if (verbose) {
            System.out.println("LowerBound logZ: " + this.ef.log(this.lbZ.add(this.runningSum)).doubleValue());
            System.out.println("UpperBound logZ: " + this.ef.log(this.ubZ.add(this.runningSum)).doubleValue());
        }
        printEffectiveEpsilon();
        return children;
    }

    private int nextLevelToExpand(int[] partialConf) {
        if (useDynamicOrdering) {

            int bestLevel = -1;
            double bestLevelScore = Double.NEGATIVE_INFINITY;

            for (int level = 0; level < this.numPos; level++) {
                if (partialConf[level] < 0) {

                    double levelScore = scoreExpansionLevel(level, partialConf);

                    if (levelScore > bestLevelScore) {//higher score is better
                        bestLevelScore = levelScore;
                        bestLevel = level;
                    }
                }
            }

            if (bestLevel == -1) {
                throw new RuntimeException("ERROR: No next expansion level found for dynamic ordering");
            }

            return bestLevel;
        } else {//static ordering
            for (int level = 0; level < numPos; level++) {
                if (partialConf[level] < 0) {
                    return level;
                }
            }

            throw new RuntimeException("ERROR: Can't find next expansion level for fully defined conformation");
        }
    }

    private double scoreExpansionLevel(int level, int[] partialConf) {
        //We will score a level by new lower-bound
        //Thus the best level  is the level that most improves our lower bound

        int[] expandedConf = partialConf.clone();

        ArrayList<Integer> unprunedRCsAtLevel = this.pruneMat.unprunedRCsAtPos(level);
        ArrayList<Double> lowerBoundPerRC = new ArrayList<>();

        //Keep track of largest logZ lower bound for numerical accuracy (see below)
        double largestLowerBound = Double.NEGATIVE_INFINITY;

        for (int rc : unprunedRCsAtLevel) {
            expandedConf[level] = rc;
            double lowerBound = computeLowerBound(expandedConf);
            lowerBoundPerRC.add(lowerBound);
            largestLowerBound = Math.max(largestLowerBound, lowerBound);
        }

        //our new bound is sum e^bound for each RC
        //this is equivalent to e^largestBound * (sum e^(bound - largestBound)
        //which is helpful for numerical reasons (so we don't overflow)
        double score = 0.0;
        for (double bound : lowerBoundPerRC) {
            double normalizedBound = bound - largestLowerBound;
            score += Math.exp(normalizedBound);
        }
        score = Math.log(score) + largestLowerBound;

        return score;
    }

    private void subtractFromBounds(PartFuncNode node) {
        this.lbZ = this.lbZ.subtract(this.ef.exp(node.getLowerBoundLogZ()));
        this.ubZ = this.ubZ.subtract(this.ef.exp(node.getUpperBoundLogZ()));
    }

    private void subtractLowerBound(PartFuncNode node) {
        this.lbZ = this.lbZ.subtract(this.ef.exp(node.getLowerBoundLogZ()));
    }

    private void subtractUpperBound(PartFuncNode node) {
        this.ubZ = this.ubZ.subtract(this.ef.exp(node.getUpperBoundLogZ()));
    }

    private void updateBounds(PartFuncNode node) {
        this.lbZ = this.lbZ.add(this.ef.exp(node.getLowerBoundLogZ()));
        this.ubZ = this.ubZ.add(this.ef.exp(node.getUpperBoundLogZ()));
    }

    private void updateLowerBound(PartFuncNode node) {
        this.lbZ = this.lbZ.add(this.ef.exp(node.getLowerBoundLogZ()));
    }

    private void updateUpperBound(PartFuncNode node) {
        this.ubZ = this.ubZ.add(this.ef.exp(node.getUpperBoundLogZ()));
    }

    private void printEffectiveEpsilon() {
        double effectiveEpsilon = 1 - ((lbZ.add(this.runningSum)).divide((ubZ.add(this.runningSum)), this.ef.mc)).doubleValue();
        System.out.println("Effective Epsilon: " + effectiveEpsilon);
    }

    private void updateRunningSumLB(double confE) {
        //for decimal precision, we keep a normalizer, y, s.t. our sum is exp(y/constRT)*runningSum
        this.runningSum = this.runningSum.add(this.ef.exp(-confE / this.constRT));
    }

    private double getConfE(PartFuncNode node) {
        if (!(nodeIsDefined(node))) {
            throw new RuntimeException("Cannot Compute energy for partial node");
        }
        //TODO: ADD functionality for EPIC/LUTE/ContinuousFlexibility
        return this.emat.getInternalEnergy(new RCTuple(node.getNodeAssignments()));
    }

    private boolean nodeIsDefined(PartFuncNode node) {
        int[] nodeAssignments = node.getNodeAssignments();
        for (int rot : nodeAssignments) {
            if (rot == -1) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isFullyAssigned(AStarNode node) {
        double logLBZ = Math.log(this.lbZ.doubleValue() + this.runningSum.doubleValue());
        double logUBZ = Math.log(this.ubZ.doubleValue() + this.runningSum.doubleValue());
        return (logLBZ - logUBZ) >= Math.log(1 - this.epsilon);
    }

    @Override
    public AStarNode rootNode() {
        int[] conf = new int[this.numPos];
        Arrays.fill(conf, -1);//indicates the sequence is not assigned

        PartFuncNode root = new PartFuncNode(conf);
        root.isRoot = true;
        root.setUpperBoundLogZ(computeUpperBound(root, null));
        root.setLowerBoundLogZ(computeLowerBound(root));
        root.setScore(scoreNode(root));
        updateBounds(root);
        printEffectiveEpsilon();
        return root;
    }

    public double computeEpsilonApprox(double epsilon) {
        this.epsilon = epsilon;
        this.nextConf();
        return Math.log(this.lbZ.doubleValue() + this.runningSum.doubleValue());
    }

    private double scoreNode(PartFuncNode node) {
//        return -node.lbLogZ;
        return this.ef.exp(node.lbLogZ).subtract(this.ef.exp(node.ubLogZ)).doubleValue();
//        return mplp.optimizeMPLP(node.getNodeAssignments(), 1000);
    }

    private double computeLowerBound(PartFuncNode node) {
        ReparamMRF mrf = new ReparamMRF(this.emat, this.pruneMat, node.getNodeAssignments(), this.eCut);
        SCMF_Clamp scmf = new SCMF_Clamp(mrf);
        double lbLogZ = scmf.getLogZLB();
        return lbLogZ;
    }

    private double computeLowerBound(int[] partialConf) {
        ReparamMRF mrf = new ReparamMRF(this.emat, this.pruneMat, partialConf, this.eCut);
        SCMF_Clamp scmf = new SCMF_Clamp(mrf);
        double lbLogZ = scmf.getLogZLB();
        return lbLogZ;
    }

    private double computeUpperBound(PartFuncNode node, PartFuncNode parentNode) {
        if (useMapPert) {
            MapPerturbation mp = new MapPerturbation(this.emat, this.pruneMat);
            double ubLogZ = mp.calcUBLogZLPMax(node.getNodeAssignments(), this.numSamples);
            return ubLogZ;
        } else if (useMPLP) {
            return getUpperBoundMPLP(node);
        } else {
            /*            MarkovRandomField mrf = new MarkovRandomField(this.emat, this.pruneMat, node.getNodeAssignments(), this.eCut);
             TreeReweightedBeliefPropagation trbp = new TreeReweightedBeliefPropagation(mrf);
             double ubLogZ = trbp.getLogZ();
             */

//            double lbLogZ = computePartFunctionEstimate(emat, pruneMat, node.getNodeAssignments(), 10000);
//            ReparamMRF rMRF = new ReparamMRF(this.emat, this.pruneMat, node.getNodeAssignments(), this.eCut);
//            node.indexToPosNum = rMRF.getIndexToPosNumMap();
            MarkovRandomField mrf = new MarkovRandomField(this.emat, this.pruneMat, node.getNodeAssignments(), this.eCut);
            double ubLogZ;

//            TRBP_Refactor trbp = new TRBP_Refactor(rMRF);
            if (!node.isRoot) {
                TRBP_Refactor_2 trbp = new TRBP_Refactor_2(mrf, parentNode.ubLogZ);
                ubLogZ = trbp.getLogZ();
                node.nodeWeights = trbp.nodeWeights;
            } else {
                TRBP_Refactor_2 trbp = new TRBP_Refactor_2(mrf);
                ubLogZ = trbp.getLogZ();
                node.nodeWeights = trbp.nodeWeights;

            }

//            node.edgeProbabilities = trbp.edgeProbabilities;
            return ubLogZ;
        }
    }

    private double[][] getEdgeProbFromParent(ReparamMRF childMRF, PartFuncNode child, PartFuncNode parent) {
        //IMPORTANT THIS WILL ASSUME NO PRUNING IS DONE FROM PARENT TO NODE
        //Thus the only position that was clamped was the position used to expand the parent node
        if (parent.edgeProbabilities.length != childMRF.nodeList.size() + 1) {
            throw new RuntimeException("Cannot use parent edge probabilities because more than one variable was clamped");
        }
        int clampedPos = getClampedPos(child, parent);
        int indexClampedPos = ArrayUtils.indexOf(parent.indexToPosNum, clampedPos);
        if (indexClampedPos == -1) {
            throw new RuntimeException("Clamped Pos Num Error");
        }
        //Edge probabilities need to sum to |V|-1;
        int numNodes = childMRF.nodeList.size();
        double[][] edgeProb = getNewEdgeProb(parent.edgeProbabilities, indexClampedPos);
        double difference = numNodes - 1 - getSumEdgeProb(edgeProb);
        int numEdges = childMRF.getNumEdge();
        normalize(edgeProb, difference / (double) numEdges);
        if (Math.abs(numNodes - 1 - getSumEdgeProb(edgeProb)) > 1e-4) {
            throw new RuntimeException("edge probabilities are not normalized");
        }
        return edgeProb;
    }

    void normalize(double[][] edgeProb, double toAdd) {
        for (int i = 0; i < edgeProb.length; i++) {
            for (int j = 0; j < i; j++) {
                edgeProb[i][j] += toAdd;
                if (edgeProb[i][j] < 0 || edgeProb[i][j] > 1) {
                    throw new RuntimeException("Need to normalize better this has a bug");
                }
            }
        }
    }

    double getSumEdgeProb(double[][] edgeProb) {
        double sum = 0.;
        for (int i = 0; i < edgeProb.length; i++) {
            for (int j = 0; j < i; j++) {
                sum += edgeProb[i][j];
            }
        }
        return sum;
    }

    private double[][] getNewEdgeProb(double[][] parentEdgeProb, int indexClampedPos) {
        int numNodes = parentEdgeProb.length - 1;
        double[][] edgeProb = new double[numNodes][];
        for (int i = 0; i < numNodes + 1; i++) {
            if (i < indexClampedPos) {
                edgeProb[i] = new double[i];
                for (int j = 0; j < i; j++) {
                    edgeProb[i][j] = parentEdgeProb[i][j];
                }
            } else if (i > indexClampedPos) {
                edgeProb[i - 1] = new double[i - 1];
                for (int j = 0; j < i; j++) {
                    if (j < indexClampedPos) {
                        edgeProb[i - 1][j] = parentEdgeProb[i][j];
                    }
                    if (j > indexClampedPos) {
                        edgeProb[i - 1][j - 1] = parentEdgeProb[i][j];
                    }
                }
            }
        }
        return edgeProb;
    }

    private int getClampedPos(PartFuncNode child, PartFuncNode parent) {
        int[] childAssignments = child.getNodeAssignments();
        int[] parentAssignments = parent.getNodeAssignments();
        for (int pos = 0; pos < childAssignments.length; pos++) {
            if ((parentAssignments[pos] == -1) && (childAssignments[pos] >= 0)) {
                return pos;
            }
        }
        throw new RuntimeException("Clamped Position Could Not Be Found");
    }

    private double getUpperBoundMPLP(PartFuncNode node) {
        double lpGMEC = mplp.optimizeMPLP(node.getNodeAssignments(), 1000);
        return (-lpGMEC / constRT) + getLogNumConfsUnderNode(node.getNodeAssignments());
    }

    private double getLogNumConfsUnderNode(int[] partialConf) {
        double logNumConfs = 0;
        for (int pos = 0; pos < partialConf.length; pos++) {
            if (partialConf[pos] == -1) {
                int numRCs = this.pruneMat.unprunedRCsAtPos(pos).size();
                logNumConfs += Math.log(numRCs);
            }
        }
        return logNumConfs;
    }

    private BigInteger getNumConfsUnderNode(int[] partialConf) {
        BigInteger numConfs = new BigInteger("1");
        for (int pos = 0; pos < partialConf.length; pos++) {
            if (partialConf[pos] == -1) {
                int numRCs = this.pruneMat.unprunedRCsAtPos(pos).size();
                numConfs = numConfs.multiply(new BigInteger(Integer.toString(numRCs)));
            }
        }
        return numConfs;
    }

    private double computePartFunctionEstimate(EnergyMatrix emat, PruningMatrix pruneMat, int[] partialConf, int numIter) {
        UpdatedPruningMatrix upm = new UpdatedPruningMatrix(pruneMat);
        for (int pos = 0; pos < partialConf.length; pos++) {
            if (partialConf[pos] != -1) {
                for (int rc : pruneMat.unprunedRCsAtPos(pos)) {
                    if (rc != partialConf[pos]) {
                        upm.markAsPruned(new RCTuple(pos, rc));
                    }
                }
            }
        }

        double gmecE = 0.0;
        double partFunc = 0.0;
        ConfTree tree = new ConfTree(emat, upm);
        for (int i = 0; i < numIter; i++) {
            int[] conf = tree.nextConf();
            if (conf == null) {
                break;
            }
            double energy = emat.getInternalEnergy(new RCTuple(conf));
            if (i == 0) {
                gmecE = -energy / constRT;
                partFunc += 1;
            } else {
                double normalized = (-energy / constRT) - gmecE;
                partFunc += Math.exp(normalized);
            }
        }
        return gmecE + Math.log(partFunc);

    }

}