package tju.edu.cn.reorder;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.trace.*;

import java.util.ArrayList;
import java.util.HashMap;

public class NewReachEngine {


    public static Short2ObjectOpenHashMap<AbstractNode> tidFirstNode = new Short2ObjectOpenHashMap<AbstractNode>(Reorder.INITSZ_S / 2);
    public static Short2ObjectOpenHashMap<AbstractNode> tidLastNode = new Short2ObjectOpenHashMap<AbstractNode>(Reorder.INITSZ_S / 2);

    public static ArrayList<ISyncNode> orderedSyncNodeList = new ArrayList<ISyncNode>(Reorder.INITSZ_SYNC);


    public static ArrayList<TStartNode> thrStartNodeList = new ArrayList<TStartNode>(Reorder.INITSZ_S * 5);
    public static ArrayList<TJoinNode> joinNodeList = new ArrayList<TJoinNode>(Reorder.INITSZ_S * 5);

    //make this global across phases
    public static Long2ObjectOpenHashMap<ArrayList<IWaitNotifyNode>> cond2WaitNotifyLs = new Long2ObjectOpenHashMap<ArrayList<IWaitNotifyNode>>(Reorder.INITSZ_SYNC);

    public static void saveToWaitNotifyList(IWaitNotifyNode node) {

        addToOrderedSyncList(node);
    }

    public static void saveToStartNodeList(TStartNode node) {
        addToOrderedSyncList(node);
        thrStartNodeList.add(node);
    }

    public static void saveToJoinNodeList(TJoinNode node) {
        addToOrderedSyncList(node);
        joinNodeList.add(node);
    }

    public static void saveToThreadFirstNode(short tid, TBeginNode node) {
        tidFirstNode.put(tid, node);
    }

    public static void saveToThreadLastNode(short tid, TEndNode node) {
        tidLastNode.put(tid, node);
    }

    public static void processSyncNode() {
        for (ISyncNode node : orderedSyncNodeList) {
            if (node instanceof TStartNode) {
                short tid = ((TStartNode) node).tidKid;
                AbstractNode fnode = tidFirstNode.get(tid);
                if (fnode != null) addEdge(node, fnode);
            } else if (node instanceof TJoinNode) {
                short tid = ((TJoinNode) node).tid_join;
                AbstractNode lnode = tidLastNode.get(tid);
                if (lnode != null) {
                    addEdge(lnode, node);
                }
            } else if (node instanceof WaitNode) {
                long cond = node.getAddr();
                ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
                if (waitNotifyLs == null) {
                    waitNotifyLs = new ArrayList<IWaitNotifyNode>();
                    cond2WaitNotifyLs.put(cond, waitNotifyLs);
                }
                waitNotifyLs.add((IWaitNotifyNode) node);

            } else if (node instanceof NotifyNode) {
                long cond = node.getAddr();
                ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
                if (waitNotifyLs != null && !waitNotifyLs.isEmpty()) {
                    ISyncNode wait = waitNotifyLs.remove(0);
                    addEdge(node, wait);//signal to wait
                }
            } else if (node instanceof NotifyAllNode) {
                long cond = node.getAddr();
                ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
                {
                    if (waitNotifyLs != null) while (!waitNotifyLs.isEmpty()) {
                        ISyncNode wait = waitNotifyLs.remove(0);
                        addEdge(node, wait);//signal to all wait
                    }
                }

            }

        }
    }

    private static HashMap<String, VectorClock> sync2VCs = new HashMap<String, VectorClock>();
    private static HashMap<Short, VectorClock> tidToVCs = new HashMap<Short, VectorClock>();

    private static Short2ObjectOpenHashMap<ArrayList<Long>> tid2GidsMap =
            new Short2ObjectOpenHashMap<ArrayList<Long>>(Reorder.INITSZ_S);

    private static HashMap<Short, Short> tid2IndexMap = new HashMap<Short, Short>();

    public static void setThreadIdsVectorClock(short[] shortArray) {

        VectorClock.CLOCK_LENGTH = (short) shortArray.length;
        for (short i = 0; i < shortArray.length; i++)
            tid2IndexMap.put(shortArray[i], i);
    }

    private static VectorClock getThreadVectorClock(short tid) {
        VectorClock tidVC = tidToVCs.get(tid);
        if (tidVC == null) {
            Short idx = tid2IndexMap.get(tid);
            if (idx == null) {
                idx = 0;
//	    		 idx = (short) tid2IndexMap.size();
//	    		 tid2IndexMap.put(tid, idx);
            }
            tidVC = new VectorClock(idx);
            tidToVCs.put(tid, tidVC);
        }

        return tidVC;
    }

    private static void addSyncGidToTidList(short tid, long gid) {
        ArrayList<Long> gids = tid2GidsMap.get(tid);
        if (gids == null) {
            gids = new ArrayList<Long>();
            tid2GidsMap.put(tid, gids);

            gids.add(gid);
        } else {

            if (gid < gids.get(0))
                gids.add(0, gid);
            else
                for (int i = gids.size() - 1; i >= 0; i--) {
                    Long gid2 = gids.get(i);
                    if (gid > gid2) {
                        gids.add(i + 1, gid);//make sure this is correct
                        break;
                    }
                }
        }


    }

    public static void addEdge(AbstractNode from, AbstractNode to) {
        if (from.tid == to.tid)
            return;

        addSyncGidToTidList(from.tid, from.gid);
        addSyncGidToTidList(to.tid, to.gid);

        //get and tick the VC of the current thread
        VectorClock tid1VC = getThreadVectorClock(from.tid);
        VectorClock tid2VC = getThreadVectorClock(to.tid);


        tid1VC.tick();
        tid2VC.tick();
        //

        //create new VC for the sync points

        VectorClock vc1 = new VectorClock(tid1VC);
        VectorClock vc2 = new VectorClock(tid2VC);
        vc2.join(vc1);


//    long fromID = Bytes.longs.add(from.tid, from.gid);
//    long toID = Bytes.longs.add(to.tid, to.gid);

        //save vector clock
        sync2VCs.put(String.valueOf(from.tid + from.gid), vc1);
        sync2VCs.put(String.valueOf(to.tid + to.gid), vc2);
    }

    public static boolean canReach(AbstractNode n1, AbstractNode n2) {
        final short tid1 = n1.tid;
        final short tid2 = n2.tid;
        final long gid1 = n1.gid;
        final long gid2 = n2.gid;
        if (tid1 == tid2) {
            // gid grows within one thread
            return gid1 <= gid2;
        } else { // diff thread

            //find the nearest sync point for each node
            long gid1_down = findSyncGidDown(tid1, gid1);
            if (gid1_down == 0) //no sync point after gid1 from tid1
                return false; // can never reach

            long gid2_up = findSyncGidUp(tid2, gid2);
            if (gid2_up == 0)// no sync point before gid2 from tid2
                return false;

            //NO CACHE

            //Instead, maintain a vector clock for each sync point

//    	long key1 = Bytes.longs.add(tid1, gid1_down);
//    	long key2 = Bytes.longs.add(tid2, gid2_up);

            VectorClock vc1 = sync2VCs.get(String.valueOf(tid1 + gid1_down));
            VectorClock vc2 = sync2VCs.get(String.valueOf(tid2 + gid2_up));

            if (vc1.happensBefore(vc2))
                return true;
            else return false;

        } //diff threads
    }

    private static long findSyncGidUp(short tid2, long gid2) {
        ArrayList<Long> gids = tid2GidsMap.get(tid2);
        if (gids == null || gids.isEmpty())
            return 0;//
        for (int i = gids.size() - 1; i >= 0; i--) {
            Long gid = gids.get(i);
            if (gid2 > gid)
                return gid;
        }
        return 0;


    }

    private static long findSyncGidDown(short tid1, long gid1) {


        ArrayList<Long> gids = tid2GidsMap.get(tid1);
        if (gids == null || gids.isEmpty())
            return 0;//
        for (int i = 0; i < gids.size(); i++) {
            Long gid = gids.get(i);
            if (gid1 < gid)
                return gid;
        }

        return 0;

    }

    public static int cur_order_index;

    private static void addToOrderedSyncList(ISyncNode node) {

        long gindex = node.getIndex();

        for (; cur_order_index < orderedSyncNodeList.size(); cur_order_index++) {
            if (gindex <= orderedSyncNodeList.get(cur_order_index).getIndex())
                break;

        }
        orderedSyncNodeList.add(cur_order_index, node);
    }


}




