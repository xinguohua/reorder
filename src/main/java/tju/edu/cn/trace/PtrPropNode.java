package tju.edu.cn.trace;

public class PtrPropNode extends AbstractNode {

  public final long src;
  public final long dest;
  public final long idx; //close idx

  public PtrPropNode(short tid, long src, long dest, long idx, long order) {
    super(tid, order);
    this.src = src;
    this.dest = dest;
    this.idx = idx;
  }

  @Override
  public String toString() {
    return "PtrPropNode{" +
        "src=" + src +
        ", dest=" + dest +
        ", idx=" + idx +
        '}';
  }
}
