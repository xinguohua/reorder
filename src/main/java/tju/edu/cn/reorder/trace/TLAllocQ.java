package tju.edu.cn.reorder.trace;

import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import tju.edu.cn.reorder.Reorder;

import java.util.ArrayList;

public class TLAllocQ {
    ArrayList<AllocaPair> allocQ = new ArrayList<>(Reorder.INITSZ_S);

    public Long2ObjectRBTreeMap<ArrayList<AllocaPair>> tree = new Long2ObjectRBTreeMap<>(
            // addr in this tree follows DESC order
            // to map acc with tail map
            (x, y) -> (y < x) ? -1 : ((x.longValue() == y.longValue()) ? 0 : 1));

    public void add(AllocaPair g) {
        allocQ.add(g);
        final long addr = g.allocNode.addr;
        ArrayList<AllocaPair> ls = tree.get(addr);
        if (ls == null) {
            ls = new ArrayList<>(30);
            tree.put(addr, ls);
        }
        ls.add(g);
    }

}