package tju.edu.cn.reorder.misc;

import java.util.ArrayList;

public class Result {
  public String logString;

  public ArrayList<String> schedule;

  public Result(String logString, ArrayList<String> schedule) {
    this.logString = logString;
    this.schedule = schedule;
  }

  public Result() {
  }
}
