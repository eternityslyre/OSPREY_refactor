package edu.duke.cs.osprey.sparse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class TreeEdge {
	
    private int nodeName1 = -1; //the name of the first incident node
    private int nodeName2 = -1; //the name of the second incident node

    @SuppressWarnings("unused")
    private TreeNode p = null; //the parent node incident to the edge in the rooted tree
    private TreeNode c = null; //the child node incident to the edge in the rooted tree

    private boolean isLambdaEdge = false; //determines if this is a lambda edge

    private LinkedHashSet<Integer> M = null; //the M set (vertices have molecule index-relative numbering)
    private LinkedHashSet<Integer> L = null; //the L set (vertices have molecule index-relative numbering)
    private LinkedHashSet<Integer> lambda = null; //the lambda set (vertices have molecule index-relative numbering)
    private LinkedHashSet<Integer> leftL = null;
    private LinkedHashSet<Integer> rightL = null;

    private LinkedHashSet<Integer> leftOnlyL = null;

 
    private boolean isRootEdge = false; //determines if this is the root edge

    /* Enumeration objects */

    static boolean printHeap = false;
    static boolean EnumerateEnsembles = false;
    HashMap<String, Integer> confsEnumerated = new HashMap<String, Integer>();
    Set<String> stringSet = new HashSet<String>();
    private static Map<String, Integer> heapHashCodes = new HashMap<String, Integer>();

    TreeNode leftChild;
    TreeNode rightChild;
    TreeEdge parent;
    private float shellShellEnergy;

    public TreeEdge(int eNodeName1, int eNodeName2, LinkedHashSet<Integer> teM, boolean rootEdge){


        nodeName1 = eNodeName1;
        nodeName2 = eNodeName2;

        M = new LinkedHashSet<Integer>(teM);
        
        isRootEdge = rootEdge;
    }

    public void compLlambda(){
		compLlambda(true);
	}
    //Computes the L and lambda sets for this edge; must be called only after p and c have been assigned for all edges
    public void compLlambda(boolean initMatrices){

        TreeNode clc = c.getlc();
        if(clc==null)//child is leaf tree node, so the L set is the difference between the M set and the two graph vertices for the child
        {
            L = new LinkedHashSet<Integer>();
            L.add(c.getv1());
            L.add(c.getv2());
            L.removeAll(M);
            leftL = L;
            leftOnlyL = leftL;
            rightL = L;
            lambda = new LinkedHashSet<Integer>(L); // as the lambda set and L set for a leaf node would be the same
        }

        else
        {
            LinkedHashSet<Integer> uMc = new LinkedHashSet<Integer>(clc.getCofEdge().getM());
            uMc.addAll(c.getrc().getCofEdge().getM()); //the union of the M sets for the two incident edges with the two children
            lambda = new LinkedHashSet<Integer>(uMc);
            lambda.removeAll(M); //the difference between the M set for this edge and the uMc set is equal to the lambda set

            L = new LinkedHashSet<Integer>(lambda);
            leftL = new LinkedHashSet<Integer>();
            leftOnlyL = new LinkedHashSet<Integer>(lambda);
            rightL = new LinkedHashSet<Integer>();
            leftL.addAll(clc.getCofEdge().getleftOnlyL());
            rightL.addAll(c.getrc().getCofEdge().getL());
            L.addAll(clc.getCofEdge().getL()); //add the L set of the left edge
            L.addAll(c.getrc().getCofEdge().getL()); //add the L set of the right edge
        }

        
        if(!lambda.isEmpty() || rightChild != null)
        {
            isLambdaEdge=!lambda.isEmpty();  // initialising the matrices and calculating the Fset since it is a lambda edge
            
        }
        else
            isLambdaEdge=false;
    }



    public LinkedHashSet<Integer> getL(){
        return L;
    }

    public LinkedHashSet<Integer> getleftOnlyL(){
        return leftOnlyL;
    }

    public LinkedHashSet<Integer> getM(){
        return M;
    }

    public boolean getIsLambdaEdge(){
        return isLambdaEdge;
    }

    public LinkedHashSet<Integer> getLambda(){
        return lambda;
    }


    public TreeNode getc(){
        return c;
    }
    

	public TreeEdge (int name, int name2, Object m, int[] numUnprunedRot, int[] molResidueMap, int[] invResidueMap, int sysStrandNum, boolean b) {
		// TODO Auto-generated constructor stub
	}

	public void setP (TreeEdge p) {
		parent = p;		
	}

	public void setC (TreeNode node) {
		c = node;
	}



    //Returns a deep copy of this TreeEdge (modified from http://javatechniques.com/blog/faster-deep-copies-of-java-objects/, accessed 10/30/2008)
    public TreeEdge deepCopy(){
        TreeEdge c = null;
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ObjectOutputStream fout = new ObjectOutputStream(b);
            fout.writeObject(this);
            fout.flush();
            fout.close();

            ObjectInputStream fin = new ObjectInputStream(new ByteArrayInputStream(b.toByteArray()));
            c = (TreeEdge)fin.readObject();
        }
        catch (Exception e){
            System.out.println("ERROR: "+e);
            System.exit(1);
        }
        return c;
    }
    

	
}
