package edu.duke.cs.osprey.restypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBFileReader;
import edu.duke.cs.osprey.structure.PDBRotamerReader;
import edu.duke.cs.osprey.structure.Residue;

/***
 * Rotamer library which specifies specific rotamers at 
 * each design position. Used for generating alternate
 * conformations when input structure is sufficiently high 
 * resolution.
 * @author JJ
 *
 */
public class PositionSpecificRotamerLibrary extends ResidueTemplateLibrary {

	private Map<Integer, Map<String, List<ResidueTemplate>>> positionSpecificRotamers = new HashMap<>();
	public static PositionSpecificRotamerLibrary generateLibraryFromPDB(String pdbFileName)
	{

		PositionSpecificRotamerLibrary library = new PositionSpecificRotamerLibrary();
		Molecule m = PDBFileReader.readPDBFile(pdbFileName);
		PDBRotamerReader.createTemplates(m, library, pdbFileName);


		for(Residue r: m.residues)
		{
			outputResidueTemplateInfo(r);
		}
		return library;
	}

	// This constructor left intentionally empty. Never create one in a non-static fashion.
	private void PositionSpecificRotamerLibrary(){}

	private static void outputResidueTemplateInfo(Residue r)
	{
	}
	
	@Override
    public ResidueTemplate getTemplateForMutation(String resTypeName, Residue res, boolean errorIfNone){
        //We want to mutate res to type resTypeName.  Get the appropriate template.
        //Currently only one template capable of being mutated to (i.e., having coordinates)
        //is available for each residue type.  If this changes update here!
		int index = res.getPDBIndex();
		List<ResidueTemplate> templates = getTemplatesForDesignIndex(index).get(resTypeName);
        for(ResidueTemplate template : templates){
            if(template.name.equalsIgnoreCase(resTypeName)){
                if(template.templateRes.coords!=null){
                    //we have coordinates for templateRes, so can mutate to it
                    return template;
                }
            }
        }
        
        if(errorIfNone){//actually trying to mutate...throw an error if can't get a mutation
            throw new RuntimeException("ERROR: Couldn't find a template for mutating "+res.fullName
                    +" to "+resTypeName);
        }
        else//just checking if template available for mutation...return null to indicate not possible
            return null;
    }


	@Override
	public int numRotForResType(int pos, String resType, double phi, double psi) {
		Map<String, List<ResidueTemplate>> templatesAtDesignIndex = getTemplatesForDesignIndex(pos);
		return templatesAtDesignIndex.get(resType).get(0).getNumRotamers();
	}

	@Override
	public double getDihedralForRotamer(int pos, String resType, double phi, double psi,
			int rotNum, int dihedralNum) {
		Map<String, List<ResidueTemplate>> templatesAtDesignIndex = getTemplatesForDesignIndex(pos);
		return templatesAtDesignIndex.get(resType).get(0).getRotamericDihedrals(0, 0, rotNum, dihedralNum);
	}

	private Map<String, List<ResidueTemplate>> getTemplatesForDesignIndex (int pos) {
		if(!positionSpecificRotamers.containsKey(pos))
			positionSpecificRotamers.put(pos, new HashMap<>());
		return positionSpecificRotamers.get(pos);
	}

	public void addResidueTemplate (int residueIndex, String resType, ResidueTemplate allowedConformations) {
		Map<String, List<ResidueTemplate>> templatesAtDesignIndex = getTemplatesForDesignIndex(residueIndex);
		if(!templatesAtDesignIndex.containsKey(resType))
			templatesAtDesignIndex.put(resType, new ArrayList<ResidueTemplate>());
		templatesAtDesignIndex.get(resType).add(allowedConformations);
		super.addResidueTemplate(allowedConformations);
	}
	
	public void addRotamer(int residueIndex, String resType, Residue alternateConformation)	{
		
	}
}
