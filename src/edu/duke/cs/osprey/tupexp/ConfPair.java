/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.tupexp;

/**
 *
 * @author mhall44
 */
/*
	This file is part of OSPREY.

	OSPREY Protein Redesign Software Version 2.1 beta
	Copyright (C) 2001-2012 Bruce Donald Lab, Duke University
	
	OSPREY is free software: you can redistribute it and/or modify
	it under the terms of the GNU Lesser General Public License as 
	published by the Free Software Foundation, either version 3 of 
	the License, or (at your option) any later version.
	
	OSPREY is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU Lesser General Public License for more details.
	
	You should have received a copy of the GNU Lesser General Public
	License along with this library; if not, see:
	      <http://www.gnu.org/licenses/>.
		
	There are additional restrictions imposed on the use and distribution
	of this open-source code, including: (A) this header must be included
	in any modification or extension of the code; (B) you are required to
	cite our papers in any publications that use this code. The citation
	for the various different modules of our software, together with a
	complete list of requirements and restrictions are found in the
	document license.pdf enclosed with this distribution.
	
	Contact Info:
			Bruce Donald
			Duke University
			Department of Computer Science
			Levine Science Research Center (LSRC)
			Durham
			NC 27708-0129 
			USA
			e-mail:   www.cs.duke.edu/brd/
	
	<signature of Bruce Donald>, Mar 1, 2012
	Bruce Donald, Professor of Computer Science
*/
///////////////////////////////////////////////////////////////////////////////////////////////
//	ConfPair.java
//
//	Version:           2.1 beta
//
//
//	  authors:
// 	  initials    name                 organization                email
//	---------   -----------------    ------------------------    ----------------------------
//     KER        Kyle E. Roberts       Duke University         ker17@duke.edu


// This class is used to store conformations.  It is used in OSPREY to store the top conformations.  
public class ConfPair implements Comparable{
	int[] conf;
	//minE: 0 unMinE: 1
	double[] energy;
	public ConfPair(int[] conformation, double[] e){
		conf = new int[conformation.length];
		for(int i=0; i<conformation.length;i++)
			conf[i] = conformation[i];
		energy = new double[e.length];
		for(int i=0; i<e.length;i++)
			energy[i] = e[i];
		
	}
	
	@Override
	public int compareTo(Object o) throws ClassCastException {
		// TODO Auto-generated method stub
		if(!(o instanceof ConfPair))
			throw new ClassCastException("Another confPair was expected.");
		double otherE = ((ConfPair) o).energy[0];
		if(otherE >= energy[0])
			return 1;
		else
			return -1;
		
	}

}

