package tju.edu.cn.reorder.misc;

import tju.edu.cn.trace.MemAccNode;

import java.util.ArrayList;

public class RawReorder {
  public final Pair<MemAccNode, MemAccNode> switchPair;

  public final Pair<MemAccNode, MemAccNode> dependPair;

  public final ArrayList<String> schedule;

  public RawReorder(Pair<MemAccNode, MemAccNode> switchPair, Pair<MemAccNode, MemAccNode> dependPair, ArrayList<String> schedule) {
    this.switchPair = switchPair;
    this.dependPair = dependPair;
    this.schedule = schedule;
  }
}
