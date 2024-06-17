package tju.edu.cn.reorder.pattern.builder;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.reorder.SimpleSolver;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.misc.Result;
import tju.edu.cn.reorder.pattern.PatternType;
import tju.edu.cn.reorder.trace.EventLoader;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InitialPatternBuilder extends AbstractPatternBuilder<Pair<MemAccNode, MemAccNode>> {
    public InitialPatternBuilder(SimpleSolver solver) {
        super(solver);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.Initial;
    }


    @Override
    public Set<Pair<MemAccNode, MemAccNode>> loadData(boolean onlyDynamic) {
        return data;
    }

    @Override
    public void displayRawReorders(List<RawReorder> rawReorders, EventLoader traceLoader, String outputName) {
        // todo 识别new
    }

    @Override
    public RawReorder buildRawReorder(SearchContext searchContext, Result result) {
        return null;
    }

    @Override
    public SearchContext buildSearchContext(Pair<MemAccNode, MemAccNode> e, Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, PatternType patternType) {
        return null;
    }
}
