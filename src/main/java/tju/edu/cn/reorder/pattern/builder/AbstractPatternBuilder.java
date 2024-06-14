package tju.edu.cn.reorder.pattern.builder;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.reorder.SimpleSolver;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.misc.Result;
import tju.edu.cn.reorder.pattern.PatternType;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;

import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractPatternBuilder<T> implements PatternBuilder<T> {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractPatternBuilder.class);

    Set<T> data = new HashSet<>();

    public final SimpleSolver solver;

    static ExecutorService exe = Executors.newFixedThreadPool(10);


    protected AbstractPatternBuilder(SimpleSolver solver) {
        this.solver = solver;
    }


    public Set<T> getData() {
        return data;
    }

    public abstract PatternType getPatternType();

    @Override
    public abstract Set<T> loadData(boolean onlyDynamic);


    @Override
    public List<RawReorder> solveReorderConstr( Iterator<T> iter, int limit) {
        Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map = solver.getCurrentIndexer().getTSTid2sqeNodes();
        for (ArrayList<AbstractNode> nodes : map.values()) {
            nodes.sort(Comparator.comparingInt(AbstractNode::getGid));
        }

        CompletionService<RawReorder> cexe = new ExecutorCompletionService<>(exe);
        int task = 0;
        while (iter.hasNext() && limit > 0) {
            limit--;
            T e = iter.next();
            PatternType patternType = getPatternType();
            SearchContext searchContext = buildSearchContext(e, map, patternType);
            cexe.submit(() -> {
                Result result = solver.searchReorderSchedule(searchContext);
                if (result.schedule != null) return buildRawReorder(searchContext, result);
                else return null;

            });
            task++;
        }

        ArrayList<RawReorder> ls = new ArrayList<>(task);
        try {
            while (task-- > 0) {
                Future<RawReorder> f = cexe.take(); //blocks if none available
                RawReorder rawReorder = f.get();
                if (rawReorder != null) ls.add(rawReorder);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            exe.shutdownNow();
        }
        return ls;
    }

    protected boolean isPartOfPair(Pair<MemAccNode, MemAccNode> pair, AbstractNode node) {
        // Check if node matches either part of the pair
        return node != null && (nodeMatchesMemAccNode(node, pair.key) || nodeMatchesMemAccNode(node, pair.value));
    }

    private boolean nodeMatchesMemAccNode(AbstractNode node, AbstractNode node1) {
        return node.gid == node1.gid && node.tid == node1.tid;
    }

    public abstract RawReorder buildRawReorder(SearchContext searchContext, Result result);


    public abstract SearchContext buildSearchContext(T e, Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, PatternType patternType);
}
