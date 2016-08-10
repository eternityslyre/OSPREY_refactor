/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * TODO: 
 * 1. Remove the top comment, starting with "To change this license header", since
 *  	it's useless to everyone who isn't using netbeans. 
 * 2. This is not the recommended way to deal with output categories. All output should
 * 		be piped through here, with the appropriate verbosity tag, and the LogHandler
 * 		class should be initialized with a user-specified verbosity level. All appropriate
 * 		output is then written and possibly duplicated across multiple output files when 
 * 		any output is generated anywhere in the code. We should replace all 
 * 		System.out.println calls with LogHandler.writeLog(String line, LogHandler.VERBOSITY_LEVEL verbosity)
 */
package edu.duke.cs.osprey.control;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 *
 * This writes some particular kind of warning to a special file
 * e.g., residues that are deleted because we can't find a template for them
 * 
 * @author mhall44
 */
public class SpecialWarningLog {
    
    private String fileName;
    private BufferedWriter fileHandle;
    
    
    public SpecialWarningLog(String fileName){
        this.fileName = fileName;
   
        try {
            fileHandle = new BufferedWriter(new FileWriter(fileName));
        }
        catch(IOException e){
            throw new RuntimeException("ERROR opening special warning log.  File name: "+fileName);
        }
    }
    
    
    public void write(String warning){
        try {
            fileHandle.write(warning);
        }
        catch(IOException e){
            throw new RuntimeException("ERROR writing to special warning log.  File name: "+fileName);
        }
    }
    
    public void close(){
        try {
            fileHandle.close();
        }
        catch(IOException e){
            throw new RuntimeException("ERROR closing special warning log.  File name: "+fileName);
        }
    }
            
    
}
