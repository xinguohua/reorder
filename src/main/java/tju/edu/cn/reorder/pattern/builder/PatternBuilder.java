package tju.edu.cn.reorder.pattern.builder;


import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.trace.EventLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public interface PatternBuilder<T> {

    Set<T> loadData(boolean onlyDynamic);

    List<RawReorder> solveReorderConstr(Iterator<T> iter, int limit);

    void displayRawReorders(List<RawReorder> rawReorders, EventLoader traceLoader, String outputName);
}
