package tju.edu.cn.trace;

public class FuncEntryNode extends AbstractNode {

  public final long pc;

  public FuncEntryNode(short tid, long pc, long order) {
    super(tid, order);
    this.pc = pc;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    FuncEntryNode that = (FuncEntryNode) o;
    if (order != that.order) return false;
    return pc == that.pc;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (int) (pc ^ (pc >>> 32));
    result = 31 * result + (int) (order ^ (order >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "gid: "+gid + " #" + tid +"   FuncEntry " + "pc: 0x" + Long.toHexString(pc) + " order: " + order;
  }
}
