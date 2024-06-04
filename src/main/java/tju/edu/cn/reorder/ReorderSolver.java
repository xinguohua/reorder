package tju.edu.cn.reorder;


import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;
import tju.edu.cn.trace.ReadNode;

import java.util.ArrayList;

/**
 * Created by cbw on 11/15/16.
 */
public interface ReorderSolver {


  String rebuildIntraThrConstr(Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, Pair<MemAccNode, MemAccNode> reorderPair);

  String buildReorderConstrOpt(ArrayList<ReadNode> allReadNodes, boolean influence);

  //  disable nLock engine
  void buildSyncConstr(Indexer index);

  void declareVariables(ArrayList<AbstractNode> trace);

  boolean canReach(AbstractNode node1, AbstractNode node2);

  void reset();
}
