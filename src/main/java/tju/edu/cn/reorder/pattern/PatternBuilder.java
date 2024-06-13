package tju.edu.cn.reorder.pattern;

import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.MemAccNode;

public interface PatternBuilder {

    String buildReorderAssert(Indexer currentIndexer,  MemAccNode switchNode1,  MemAccNode switchNode2, MemAccNode dependNode1, MemAccNode dependNode2);
}
