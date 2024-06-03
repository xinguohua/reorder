/*******************************************************************************
 * Copyright (c) 2013 University of Illinois
 * <p/>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p/>
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p/>
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package tju.edu.cn.z3;

import tju.edu.cn.config.Configuration;
import tju.edu.cn.reorder.Reorder;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;



/**
 * Constraint solving with Z3 solver
 *
 * @author jeffhuang
 *
 */
public class Z3Run {
  //private static final String SOLVER_FULL_STRING = "/usr/local/bin/z3"; // just add z3 to path
protected static String Z3_SMT2 = ".z3smt2";
  protected static String Z3_OUT = ".z3out";
  protected static String Z3_ERR = ".z3err.";

  Configuration config;
  
  File smtFile, z3OutFile, z3ErrFile;
  protected String CMD;

  public Z3Model model;
  public ArrayList<String> schedule;

  public Z3Run(Configuration config, int id) {
    try {
      init(config, id);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }

  /**
   * initialize solver configuration
   * @param config
   * @param id
   * @throws IOException
   */
  protected void init(Configuration config, int id) throws IOException {

	 this.config = config;
    String fileNameBase = config.appname.replace(File.separatorChar, '.');

    //constraint file
    smtFile = Util.newOutFile(config.constraint_outdir, fileNameBase + "_" + id + Z3_SMT2);

    //solution file
    z3OutFile = Util.newOutFile(config.constraint_outdir, fileNameBase + "_" + id + Z3_OUT);

    //z3ErrFile = Util.newOutFile(Z3_ERR+taskId);//looks useless

    //command line to Z3 solver
    CMD = /*SOLVER_FULL_STRING+*/"z3 -T:" + config.solver_timeout + " -memory:" + config.solver_memory + " -smt2 ";
  }

  /**
   * solve constraint "msg"
   * @param msg
   */
  public ArrayList<String> buildSchedule(String msg) {
    PrintWriter smtWriter = null;
    try {
      smtWriter = Util.newWriter(smtFile, true);
      smtWriter.println(msg);
      smtWriter.close();

      //invoke the solver
      exec(z3OutFile, z3ErrFile, smtFile.getAbsolutePath());

      model = Z3ModelReader.read(z3OutFile);

      if (model != null) {
    	  	
    	  	//We can skip schedule construction if we don't need it
    	  	if(config.schedule)
    	  		schedule = computeSchedule2(model);
    	  	else
    	  		schedule =  new ArrayList<String>();
    	  		
      }
      //String z3OutFileName = z3OutFile.getAbsolutePath();
      //retrieveResult(z3OutFileName);
      if (z3ErrFile != null)
        z3ErrFile.delete();
      if (smtFile != null)
        smtFile.delete();
//
      z3OutFile.delete();
      z3OutFile.getParentFile().delete();
    } catch (IOException e) {
      System.err.println(e.getMessage());

    }
    return schedule;
  }

  public static Long varName2GID(String name) {
    return Long.parseLong(name.substring(1));
  }

  public ArrayList<String> computeSchedule2(Z3Model model) {
    // Extract the map from the model
    Map<String, Object> map = model.getMap();

    // Create a list from elements of the map
    List<Entry<String, Object>> list = new ArrayList<Entry<String, Object>>(map.entrySet());

    // Sort the list
    list.sort((o1, o2) -> {
        Comparable value1 = (Comparable) o1.getValue();
        Comparable value2 = (Comparable) o2.getValue();
        return value1.compareTo(value2);
    });

    // Create a list of sorted keys
    List<String> sortedKeys = new ArrayList<String>();
    for (Entry<String, Object> entry : list) {
      sortedKeys.add(entry.getKey());
    }

    // Convert the sorted list to an ArrayList and return
    return new ArrayList<String>(sortedKeys);
  }
  /**
   * Given the model of solution, return the corresponding schedule
   *
   *
   * @param model
   * @return
   */
  public ArrayList<String> computeSchedule(Z3Model model) {

    ArrayList<String> schedule = new ArrayList<String>(Reorder.INITSZ_S);

    for (Entry<String, Object> entryModel : model.getMap().entrySet()) {
      String op = entryModel.getKey();
      int order = ((Number) entryModel.getValue()).intValue();
      if (schedule.isEmpty())
        schedule.add(op);
      else
        for (int i = 0; i < schedule.size(); i++) {
          int ord = ((Number) model.getMap().get(schedule.get(i))).intValue();
          if (order < ord) {
            schedule.add(i, op);
            break;
          } else if (i == schedule.size() - 1) {
            schedule.add(op);
            break;
          }

        }
    }

    return schedule;
  }

  public void exec(File outFile, File errFile, String file) throws IOException {

    String cmds = CMD + file;

//		args2 += " 1>"+outFile;
//		args2 += " 2>"+errFile;
//
//		args2 = args2 + "\"";

    //cmds = "/usr/local/bin/z3 -version";

    Process process = Runtime.getRuntime().exec(cmds);
    InputStream inputStream = process.getInputStream();

    //do we need to wait for Z3 to finish?

    // write the inputStream to a FileOutputStream
    OutputStream out = new FileOutputStream(outFile);

    int read = 0;
    byte[] bytes = new byte[8192];

    while ((read = inputStream.read(bytes)) != -1) {
      out.write(bytes, 0, read);
    }

    inputStream.close();
    out.flush();
    out.close();
    //setError(errFile);
    //setOutput(outFile);

  }

}
