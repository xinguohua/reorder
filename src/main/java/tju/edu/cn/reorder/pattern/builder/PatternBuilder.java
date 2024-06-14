package tju.edu.cn.reorder.pattern.builder;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.trace.EventLoader;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public interface PatternBuilder<T> {

    Set<T> loadData(List<Pair<MemAccNode, MemAccNode>> racePairsList, boolean onlyDynamic);

    List<RawReorder> solveReorderConstr(final Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, Iterator<T> iter, int limit);

    void displayRawReorders(List<RawReorder> rawReorders, Indexer indexer, EventLoader traceLoader, String outputName);
}
