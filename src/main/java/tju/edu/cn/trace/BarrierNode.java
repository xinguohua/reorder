package tju.edu.cn.trace;

import java.util.Arrays;
import java.util.Objects;

public class BarrierNode extends AbstractNode {
    private long pc;
    private int orderType;

    public long getPc() {
        return pc;
    }

    public void setPc(long pc) {
        this.pc = pc;
    }

    public int getOrderType() {
        return orderType;
    }

    public void setOrderType(int orderType) {
        this.orderType = orderType;
    }

    @Override
    public long getOrder() {
        return order;
    }

    public void setOrder(long order) {
        this.order = order;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BarrierNode that = (BarrierNode) o;
        return pc == that.pc && orderType == that.orderType && order == that.order && line == that.line && Arrays.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), pc, orderType, order, line);
        result = 31 * result + Arrays.hashCode(file);
        return result;
    }

    public char[] getFile() {
        return file;
    }

    public void setFile(char[] file) {
        this.file = file;
    }

    private long order;
    private int line;
    private char[] file;

    public BarrierNode(short tid, long pc, int orderType, long order, int line, char[] file) {
        super(tid, order);
        this.pc = pc;
        this.orderType = orderType;
        this.order = order;
        this.line = line;
        this.file = file;
    }
}
