package tju.edu.cn.reorder.pattern.asser;

import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.MemAccNode;

public class AssertContext {
    private Indexer currentIndexer;
    private MemAccNode switchNode1;
    private MemAccNode switchNode2;
    private MemAccNode dependNode1;

    public Indexer getCurrentIndexer() {
        return currentIndexer;
    }

    public void setCurrentIndexer(Indexer currentIndexer) {
        this.currentIndexer = currentIndexer;
    }

    public MemAccNode getSwitchNode1() {
        return switchNode1;
    }

    public void setSwitchNode1(MemAccNode switchNode1) {
        this.switchNode1 = switchNode1;
    }

    public MemAccNode getSwitchNode2() {
        return switchNode2;
    }

    public void setSwitchNode2(MemAccNode switchNode2) {
        this.switchNode2 = switchNode2;
    }

    public MemAccNode getDependNode1() {
        return dependNode1;
    }

    public void setDependNode1(MemAccNode dependNode1) {
        this.dependNode1 = dependNode1;
    }

    public MemAccNode getDependNode2() {
        return dependNode2;
    }

    public void setDependNode2(MemAccNode dependNode2) {
        this.dependNode2 = dependNode2;
    }

    private MemAccNode dependNode2;
}
