package edu.duke.cs.osprey.sparse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class TreeEdge {

	public TreeEdge (int name, int name2, Object m, int[] numUnprunedRot, int[] molResidueMap, int[] invResidueMap, int sysStrandNum, boolean b) {
		// TODO Auto-generated constructor stub
	}

	public void setP (TreeNode p) {
		// TODO Auto-generated method stub
		
	}

	public void setC (TreeNode c) {
		// TODO Auto-generated method stub
		
	}

	public int getNodeName1 () {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getNodeName2 () {
		// TODO Auto-generated method stub
		return 0;
	}

	public Object getM () {
		// TODO Auto-generated method stub
		return null;
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
