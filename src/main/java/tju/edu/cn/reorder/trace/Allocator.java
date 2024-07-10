package tju.edu.cn.reorder.trace;


import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.reorder.NewReachEngine;
import tju.edu.cn.reorder.Reorder;
import tju.edu.cn.trace.AllocNode;
import tju.edu.cn.trace.DeallocNode;
import tju.edu.cn.trace.MemAccNode;

import java.util.*;



/**
 * Created by cbw on 12/12/16.
 */
public class Allocator {

    private static final Logger LOG = LoggerFactory.getLogger(Allocator.class);

    public Short2ObjectOpenHashMap<TLAllocQ> getTid2AllocQ() {
        return tid2AllocQ;
    }

    private final Short2ObjectOpenHashMap<TLAllocQ> tid2AllocQ = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    private final HashSet<DeallocNode> unmatched = new HashSet<>(Reorder.INITSZ_S);
    HashMap<MemAccNode, HashSet<AllocaPair>> machtedAcc = new HashMap<MemAccNode, HashSet<AllocaPair>>(Reorder.INITSZ_S * 2);


    public void pushAlloc(AllocNode allocNode) {
        short tid = allocNode.tid;
        TLAllocQ q = tid2AllocQ.computeIfAbsent(tid, k -> new TLAllocQ());
        q.add(new AllocaPair(allocNode));
    }

    public void pushDealloc(DeallocNode deallocNode) {
        short tid = deallocNode.tid;
        TLAllocQ q = tid2AllocQ.get(tid);
        if (q != null) {
            ListIterator<AllocaPair> iter = q.allocQ.listIterator(q.allocQ.size());
            while (iter.hasPrevious()) {
                AllocaPair g = iter.previous();
                if (g.allocNode.addr == deallocNode.addr) {

                    if (g.deallocNode == null) {
                        deallocNode.length = g.allocNode.length;
                        g.deallocNode = deallocNode;
                    } else if (Indexer.CHECK_MEM_ERROR) {
                        LOG.error("possible double free at {}, first free {}, second free {}", g.allocNode, g.allocNode, deallocNode);
                        unmatched.add(deallocNode);
                    }
                    return;
                }
            }
        }
        //if we get here, probably the alloc is in a previous window or it is missed

        unmatched.add(deallocNode);
    }


    public boolean checkAcc(MemAccNode accNode) {
        HashSet<AllocaPair> matchedGrp = new HashSet<>(32);
        final long accAddr = accNode.getAddr();
        final short accTid = accNode.tid;
        for (Short2ObjectMap.Entry<TLAllocQ> e : tid2AllocQ.short2ObjectEntrySet()) {

            TLAllocQ q = e.getValue();
            ObjectCollection<ArrayList<AllocaPair>> lss = q.tree.tailMap(accAddr).values();
            for (ArrayList<AllocaPair> ls : lss)
                for (AllocaPair p : ls) {
                    DeallocNode dn = p.deallocNode;
                    if (dn == null
                        //|| dn.tid == accTid//JEFF
                    ) continue;
                    if (dn.addr <= accAddr && accAddr < dn.addr + dn.length && (dn.tid != accTid)//JEFF test  || accNode.idx>dn.idx
                            && !NewReachEngine.canReach(accNode, dn))

                        matchedGrp.add(p);

                }
            // tail map
        } // tid
        if (!matchedGrp.isEmpty()) {
            machtedAcc.put(accNode, matchedGrp);
            return true;
        } else return false;
    }
}



























