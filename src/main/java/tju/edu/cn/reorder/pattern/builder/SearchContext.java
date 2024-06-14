package tju.edu.cn.reorder.pattern.builder;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.pattern.PatternType;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;

import java.util.ArrayList;

public class SearchContext {
    private Pair<MemAccNode, MemAccNode> switchPair;

    public Indexer getCurrentIndexer() {
        return currentIndexer;
    }

    public void setCurrentIndexer(Indexer currentIndexer) {
        this.currentIndexer = currentIndexer;
    }

    private Pair<MemAccNode, MemAccNode> dependPair;

    private Indexer currentIndexer;

    private Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map;

    private PatternType patternType;

    public PatternType getPatternType() {
        return patternType;
    }

    public void setPatternType(PatternType patternType) {
        this.patternType = patternType;
    }

    public Pair<MemAccNode, MemAccNode> getSwitchPair() {
        return switchPair;
    }

    public void setSwitchPair(Pair<MemAccNode, MemAccNode> switchPair) {
        this.switchPair = switchPair;
    }

    public Pair<MemAccNode, MemAccNode> getDependPair() {
        return dependPair;
    }

    public void setDependPair(Pair<MemAccNode, MemAccNode> dependPair) {
        this.dependPair = dependPair;
    }

    public Short2ObjectOpenHashMap<ArrayList<AbstractNode>> getMap() {
        return map;
    }

    public void setMap(Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map) {
        this.map = map;
    }
}
