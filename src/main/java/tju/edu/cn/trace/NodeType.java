package tju.edu.cn.trace;

public enum  NodeType {
    INIT,
    READ, WRITE, LOCK, UNLOCK, WAIT,
    NOTIFY, NOTIFYALL, START, JOIN, BRANCH, BB,
    PROPERTY, ALLOC, DEALLOC, RANGE_W, RANGE_R,
    BEGIN, END, FUNC_IN, FUNC_OUT
}
