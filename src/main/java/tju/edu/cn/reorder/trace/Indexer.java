

package tju.edu.cn.reorder.trace;


import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.config.Configuration;
import tju.edu.cn.reorder.NewReachEngine;
import tju.edu.cn.reorder.Reorder;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.trace.*;

import java.util.*;

import static tju.edu.cn.reorder.SimpleSolver.makeVariable;


/**
 * tid -> integer
 * address -> long
 * nLock taskId -> long
 * name starts with '_' -> temp
 */
public class Indexer {

    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

    protected static final boolean CHECK_MEM_ERROR = true;


    protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> _rawTid2seq = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> tid2CallSeq = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    public TraceMetaInfo metaInfo = new TraceMetaInfo();

    // for thread sync constraints
    protected Short2ObjectOpenHashMap<AbstractNode> tidFirstNode = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);
    protected Short2ObjectOpenHashMap<AbstractNode> tidLastNode = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    // key是switch value 依赖
    List<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> reorderCrossPairs = new ArrayList<>();


    private static class SharedAccIndexes {

        // z3 declare var, all shared acc and other nodes
        protected ArrayList<AbstractNode> allNodeSeq = new ArrayList<>(Reorder.INITSZ_L);

        protected Map<String, AbstractNode> allNodeMap = new HashMap<>();

        protected HashMap<MemAccNode, DeallocNode> acc2Dealloc = new HashMap<>(Reorder.INITSZ_L);

        protected Short2ObjectOpenHashMap<ArrayList<ReadNode>> tid2seqReads = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_L);

        protected Long2LongOpenHashMap initWrites = new Long2LongOpenHashMap(Reorder.INITSZ_L);

        protected Long2ObjectOpenHashMap<ArrayList<WriteNode>> addr2SeqWrite = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_L);

        // shared acc and all other (nLock dealloc ...)
        protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> tid2sqeNodes = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_L);


        public void addReadNode(ReadNode node) {

            ArrayList<ReadNode> tidNodes = tid2seqReads.get(node.tid);


            if (tidNodes == null) {
                tidNodes = new ArrayList<>(Reorder.INITSZ_L);
                tid2seqReads.put(node.tid, tidNodes);
            }
            tidNodes.add(node);

        }
    }

    public SharedAccIndexes shared = new SharedAccIndexes();


    public Indexer() {
        shared.initWrites.defaultReturnValue(-1);
    }

    public void addTidSeq(short tid, ArrayList<AbstractNode> seq) {
        _rawTid2seq.put(tid, seq);
        metaInfo.tidRawNodesCounts.put(tid, seq.size());
        metaInfo.rawNodeCount += seq.size();
        if (LOG.isTraceEnabled()) {
            for (AbstractNode n : seq)
                LOG.trace(n.toString());
        }
    }


    private final NewReachEngine reachEngine = new NewReachEngine();

    public void processNode() {
        // 1. first pass handles:
        // sync,
        // alloc & dealloc,
        // call seq
        // tid first node, last node
        handleSpeNode();

        // 2. second pass:
        Set<Pair<MemAccNode, MemAccNode>> racePairs = new HashSet<>();

        LongOpenHashSet sharedAddrSet = findSharedAcc(racePairs);

        // 3. third pass, handle shared mem acc (index: addr tid dealloc allnode)
        processReorderNodes(racePairs, sharedAddrSet);
    }

    public void processReorderNodes(Set<Pair<MemAccNode, MemAccNode>> racePairsSet, LongOpenHashSet sharedAddrSet) {
        Pair<MemAccNode, MemAccNode> switchPair = new Pair<>();
        Pair<MemAccNode, MemAccNode> dependentPair = new Pair<>();
        List<Pair<MemAccNode, MemAccNode>> racePairsList = new ArrayList<>(racePairsSet);

        for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> entry : _rawTid2seq.short2ObjectEntrySet()) {
            short tid = entry.getShortKey();
            ArrayList<AbstractNode> tidNodes = shared.tid2sqeNodes.computeIfAbsent(tid, k -> new ArrayList<>());

            for (AbstractNode node : entry.getValue()) {
                if (node instanceof MemAccNode) {
                    MemAccNode memNode = (MemAccNode) node;
                    if (sharedAddrSet.contains(memNode.getAddr())) {
                        addNodeToSharedAndTid(memNode, tidNodes);
                        handleTSMemAcc(tid, memNode);
                    }
                    if (Configuration.onlyDynamic) {
                        for (int i = 0; i < racePairsList.size(); i++) {
                            for (int j = i + 1; j < racePairsList.size(); j++) {
                                Pair<MemAccNode, MemAccNode> pair1 = racePairsList.get(i);
                                Pair<MemAccNode, MemAccNode> pair2 = racePairsList.get(j);
                                List<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> crossPatterns = isCrossPattern(pair1, pair2);
                                if (!crossPatterns.isEmpty()) {
                                    reorderCrossPairs.addAll(crossPatterns);
                                }
                            }
                        }
                    } else {
                        for (Pair<MemAccNode, MemAccNode> pair : racePairsList) {
                            if (pair.key.equals(memNode) || pair.value.equals(memNode)) {
                                if (memNode.order == 500) {
                                    switchPair.key = memNode;
                                }
                                if (memNode.order == 501) {
                                    switchPair.value = memNode;
                                }
                                if (memNode.order == 1048) {
                                    dependentPair.key = memNode;
                                }
                                if (memNode.order == 1051) {
                                    dependentPair.value = memNode;
                                }
                            }
                        }
                        reorderCrossPairs.add(new Pair<>(switchPair, dependentPair));
                    }
                } else if (!(node instanceof FuncEntryNode) && !(node instanceof FuncExitNode) && !(node instanceof AllocNode) && !(node instanceof DeallocNode)) {
                    addNodeToSharedAndTid(node, tidNodes);
                }
            }
        }
        metaInfo.countAllNodes = getAllNodeSeq().size();
    }

    private void addNodeToSharedAndTid(AbstractNode node, ArrayList<AbstractNode> tidNodes) {
        if (node != null && !shared.allNodeMap.containsKey(makeVariable(node))) {
            shared.allNodeSeq.add(node);
            shared.allNodeMap.put(makeVariable(node), node);
            tidNodes.add(node);
        }
    }

    /**
     * op1A                  op3B
     * op2B                  op4A
     *
     */
    private static List<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> isCrossPattern(Pair<MemAccNode, MemAccNode> pair1, Pair<MemAccNode, MemAccNode> pair2) {
        MemAccNode op1A = pair1.key;
        MemAccNode op4A = pair1.value;
        MemAccNode op2B = pair2.key;
        MemAccNode op3B = pair2.value;
        List<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> result = new ArrayList<>();

        if (op1A.tid == op2B.tid && op3B.tid == op4A.tid &&
                ((op1A.gid < op2B.gid && op3B.gid < op4A.gid) || (op1A.gid > op2B.gid && op3B.gid > op4A.gid))) {
            Pair<MemAccNode, MemAccNode> pair1Sorted = (op1A.gid < op2B.gid) ? new Pair<>(op1A, op2B) : new Pair<>(op2B, op1A);
            Pair<MemAccNode, MemAccNode> pair2Sorted = (op3B.gid < op4A.gid) ? new Pair<>(op3B, op4A) : new Pair<>(op4A, op3B);
            result.add(new Pair<>(pair1Sorted, pair2Sorted));
            result.add(new Pair<>(pair2Sorted, pair1Sorted));
        }

        if (op1A.tid == op3B.tid && op2B.tid == op4A.tid &&
                ((op1A.gid < op3B.gid && op2B.gid < op4A.gid) || (op1A.gid > op3B.gid && op2B.gid > op4A.gid))) {
            Pair<MemAccNode, MemAccNode> pair1Sorted = (op1A.gid < op3B.gid) ? new Pair<>(op1A, op3B) : new Pair<>(op3B, op1A);
            Pair<MemAccNode, MemAccNode> pair2Sorted = (op2B.gid < op4A.gid) ? new Pair<>(op2B, op4A) : new Pair<>(op4A, op2B);
            result.add(new Pair<>(pair1Sorted, pair2Sorted));
            result.add(new Pair<>(pair2Sorted, pair1Sorted));
        }

        return result;
    }

    private Allocator allocator = new Allocator();

    /**
     * sync,
     * alloc & dealloc,
     * call seq
     * tid first node, last node
     */
    protected void handleSpeNode() {
        for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> e : _rawTid2seq.short2ObjectEntrySet()) {
            final short curTid = e.getShortKey();
            ArrayList<AbstractNode> nodes = e.getValue();

            for (AbstractNode node : nodes) {
                if (node instanceof AllocNode) {
                    metaInfo.countAlloc++;
                    AllocNode an = (AllocNode) node;
                    allocator.push(an);

                } else if (node instanceof DeallocNode) {
                    // matching delloc with alloc, replacing alloc with dealloc
                    metaInfo.countDealloc++;
                    DeallocNode dnode = (DeallocNode) node;
                    allocator.insert(dnode);
                } else if (node instanceof FuncExitNode || node instanceof FuncEntryNode) {
                    ArrayList<AbstractNode> callseq = tid2CallSeq.get(curTid);
                    if (callseq == null) {
                        callseq = new ArrayList<>(Reorder.INITSZ_L);
                        tid2CallSeq.put(curTid, callseq);
                    }
                    callseq.add(node);
                    metaInfo.countFuncCall++;
                } else if (node instanceof ISyncNode) {
                    handleSync2(curTid, (ISyncNode) node);
                }

            } // for one tid
        } // for all tid
        finishSync();
    }


    /**
     * find shared acc {
     * shared heap access,
     * addr written by more than 2 threads
     * addr write / read diff threads
     */
    protected LongOpenHashSet findSharedAcc(Set<Pair<MemAccNode, MemAccNode>> racePairs) {
        Long2ObjectOpenHashMap<ShortOpenHashSet> addr2TidReads = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);
        Long2ObjectOpenHashMap<ShortOpenHashSet> addr2TidWrites = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);
        LongOpenHashSet sharedAddrSet = new LongOpenHashSet(Reorder.INITSZ_L * 2);
        Long2ObjectOpenHashMap<ArrayList<MemAccNode>> addr2Nodes = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);

        // 遍历每个线程的内存访问序列
        for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> entry : _rawTid2seq.short2ObjectEntrySet()) {
            final short tid = entry.getShortKey();
            for (AbstractNode node : entry.getValue()) {
                if (!(node instanceof MemAccNode)) continue;

                MemAccNode memNode = (MemAccNode) node;
                final long addr = memNode.getAddr();

                if (allocator.checkAcc(memNode)) sharedAddrSet.add(addr);

                if (node instanceof ReadNode || node instanceof RangeReadNode) {
                    addr2TidReads.computeIfAbsent(addr, k -> new ShortOpenHashSet(Reorder.INITSZ_S / 10)).add(tid);
                } else if (node instanceof WriteNode || node instanceof RangeWriteNode) {
                    addr2TidWrites.computeIfAbsent(addr, k -> new ShortOpenHashSet(Reorder.INITSZ_S / 10)).add(tid);
                }

                addr2Nodes.computeIfAbsent(addr, k -> new ArrayList<>()).add(memNode);
            }
        }

        LongOpenHashSet addrs = new LongOpenHashSet(Reorder.INITSZ_L * 2);
        addrs.addAll(addr2TidReads.keySet());
        addrs.addAll(addr2TidWrites.keySet());
        for (long addr : addrs) {
            ShortOpenHashSet wtids = addr2TidWrites.get(addr);
            if (wtids != null && !wtids.isEmpty()) {
                if (wtids.size() > 1) { // write thread > 1
                    sharedAddrSet.add(addr);
                } else { // only one write
                    short wtid = wtids.iterator().nextShort();
                    ShortOpenHashSet rtids = addr2TidReads.get(addr);
                    if (rtids != null) {
                        rtids.remove(wtid); // remove self
                        if (!rtids.isEmpty())// another read
                            sharedAddrSet.add(addr);
                    }
                }
            }

        } //for addr

        // 计算数据竞争的节点对
        for (long shareAddr : sharedAddrSet) {
            ArrayList<MemAccNode> nodes = addr2Nodes.get(shareAddr);
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    MemAccNode node1 = nodes.get(i);
                    MemAccNode node2 = nodes.get(j);
                    if ((node1 instanceof WriteNode || node1 instanceof RangeWriteNode) && (node2 instanceof WriteNode || node2 instanceof RangeWriteNode || node2 instanceof ReadNode || node2 instanceof RangeReadNode) && node1.tid != node2.tid && !NewReachEngine.canReach(node1, node2)) {
                        racePairs.add(new Pair<>(node1, node2));
                    }
                }
            }
        }

        return sharedAddrSet;
    }


    // called in the second pass
    // build tid addr dealloc index
    protected void handleTSMemAcc(int tid, MemAccNode node) {
        // index: addr -> acc
        long addr = node.getAddr();
        // index: seq read, seq write
        if (node instanceof RangeReadNode) {

        } else if (node instanceof ReadNode) {
            metaInfo.countRead++;


            shared.addReadNode((ReadNode) node);

        } else if (node instanceof RangeWriteNode) {

        } else if (node instanceof WriteNode) {
            metaInfo.countWrite++;
//        seqWrite.add((WriteNode) node);
            ArrayList<WriteNode> seqW = shared.addr2SeqWrite.get(addr);
            if (seqW == null) {
                seqW = new ArrayList<WriteNode>(Reorder.INITSZ_L);
                shared.addr2SeqWrite.put(addr, seqW);
            }
            seqW.add((WriteNode) node);

            if (!shared.initWrites.containsKey(addr)) {
                shared.initWrites.put(addr, ((WriteNode) node).value);
            }
        }
    }

    Long2ObjectOpenHashMap<ArrayList<LockPair>> addr2LockPairLs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(Reorder.INITSZ_S);

    protected void handleSync2(short tid, ISyncNode node) {

        long addr = node.getAddr();
        ArrayList<ISyncNode> syncNodes = _syncNodesMap.get(addr);
        if (syncNodes == null) {
            syncNodes = new ArrayList<ISyncNode>(Reorder.INITSZ_S);
            _syncNodesMap.put(addr, syncNodes);
        }
        syncNodes.add(node);

        if (node instanceof LockNode) {
            Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
            if (stack == null) {
                stack = new Stack<ISyncNode>();
                _tid2SyncStack.put(tid, stack);
            }
            stack.push(node);
            metaInfo.countLock++;

        } else if (node instanceof UnlockNode) {
            metaInfo.countUnlock++;
            Long2ObjectOpenHashMap<ArrayList<LockPair>> indexedLockpairs = _tid2LockPairs.get(tid);
            if (indexedLockpairs == null) {
                indexedLockpairs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(Reorder.INITSZ_S);
                _tid2LockPairs.put(tid, indexedLockpairs);
            }
            long lockId = ((UnlockNode) node).lockID;
            ArrayList<LockPair> lockpairLs = indexedLockpairs.get(lockId);
            if (lockpairLs == null) {
                lockpairLs = new ArrayList<LockPair>();
                indexedLockpairs.put(lockId, lockpairLs);
            }

            Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
            if (stack == null) {
                stack = new Stack<ISyncNode>();
                _tid2SyncStack.put(tid, stack);
            }
            //assert(stack.fsize()>0); //this is possible when segmented
            if (stack.isEmpty()) lockpairLs.add(new LockPair(null, node));
            else if (stack.size() == 1) lockpairLs.add(new LockPair(stack.pop(), node));
            else stack.pop();//handle reentrant nLock
        } // nUnlock

    }


    public void finishSync() {
        checkSyncStack();

        for (Long2ObjectOpenHashMap<ArrayList<LockPair>> tidAddr2LpLs : _tid2LockPairs.values()) {
            for (Long2ObjectMap.Entry<ArrayList<LockPair>> e : tidAddr2LpLs.long2ObjectEntrySet()) {
                long lockID = e.getLongKey();
                ArrayList<LockPair> addrLpLs = addr2LockPairLs.get(lockID);
                if (addrLpLs == null) {
                    addrLpLs = new ArrayList<LockPair>(Reorder.INITSZ_S * 5);
                    addr2LockPairLs.put(lockID, addrLpLs);
                }
                addrLpLs.addAll(e.getValue());
            }
        }
    }

    protected void checkSyncStack() {

        //check threadSyncStack - only to handle when segmented

        for (Short2ObjectMap.Entry<Stack<ISyncNode>> entry : _tid2SyncStack.short2ObjectEntrySet()) {
            final short tid = entry.getShortKey();
            Stack<ISyncNode> stack = entry.getValue();

            if (!stack.isEmpty()) {
                Long2ObjectOpenHashMap<ArrayList<LockPair>> indexedLockpairs = _tid2LockPairs.get(tid);
                if (indexedLockpairs == null) {
                    indexedLockpairs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(Reorder.INITSZ_S);
                    _tid2LockPairs.put(tid, indexedLockpairs);
                }

                while (!stack.isEmpty()) {
                    ISyncNode syncnode = stack.pop();//nLock or wait
                    long addr = syncnode.getAddr();
                    ArrayList<LockPair> lockpairs = indexedLockpairs.get(addr);
                    if (lockpairs == null) {
                        lockpairs = new ArrayList<LockPair>(Reorder.INITSZ_S);
                        indexedLockpairs.put(addr, lockpairs);
                    }
                    lockpairs.add(new LockPair(syncnode, null));
                }
            } // stack not empty
        } // for
    }


    public Long2ObjectOpenHashMap<ArrayList<LockPair>> getAddr2LockPairLs() {
        return addr2LockPairLs;
    }


    Long2ObjectOpenHashMap<ArrayList<ISyncNode>> _syncNodesMap = new Long2ObjectOpenHashMap<ArrayList<ISyncNode>>(Reorder.INITSZ_S);

    Short2ObjectOpenHashMap<Long2ObjectOpenHashMap<ArrayList<LockPair>>> _tid2LockPairs = new Short2ObjectOpenHashMap<Long2ObjectOpenHashMap<ArrayList<LockPair>>>(Reorder.INITSZ_S / 2);

    Short2ObjectOpenHashMap<Stack<ISyncNode>> _tid2SyncStack = new Short2ObjectOpenHashMap<Stack<ISyncNode>>(Reorder.INITSZ_S / 2);

    protected Long2ObjectOpenHashMap<AbstractNode> gid2node;

    public Long2ObjectOpenHashMap<AbstractNode> getGid2node() {
        return gid2node;
    }


    /**
     * most recent call in the back
     *
     * @param node
     * @return
     */
    public LongArrayList buildCallStack(AbstractNode node) {
        final short tid = node.tid;
        final long gid = node.gid;
        ArrayList<AbstractNode> callseq = tid2CallSeq.get(tid);
        if (callseq == null || callseq.size() < 1) return null;
        LongArrayList callStack = new LongArrayList(100);
        for (AbstractNode n : callseq) {
            if (n.gid > gid) break;
            if (n instanceof FuncEntryNode) {
                long pc = ((FuncEntryNode) n).pc;
                callStack.push(pc);

            } else if (n instanceof FuncExitNode) {
                if (!callStack.isEmpty()) callStack.popLong();

            } else throw new IllegalStateException("Unknown event in call seq " + n);
        }
        return callStack;
    }


    public void getReorderDependentRead(ArrayList<ReadNode> allReadNodes, MemAccNode node) {

        ArrayList<ReadNode> tidNodes = shared.tid2seqReads.get(node.tid);
        if (tidNodes == null || tidNodes.isEmpty()) return;

        int min = 0;
        int max = tidNodes.size() - 1;

        //find the latest read before this node
        int id = (min + max) / 2;

        while (true) {
            ReadNode tmp = tidNodes.get(id);
            if (tmp.gid < node.gid) {
                if (id + 1 > max || tidNodes.get(++id).gid > node.gid) break;
                min = id;
                id = (min + max) / 2;
            } else if (tmp.gid > node.gid) {
                if (id - 1 < min || tidNodes.get(--id).gid < node.gid) break;
                max = id;
                id = (min + max) / 2;
            } else {
                //exclude itself
                break;
            }
        }

        if (tidNodes.get(id).gid < node.gid && id < max) id++;//special case


        for (int i = 0; i <= id; i++)
            if (tidNodes.get(i).addr == node.addr) {
                allReadNodes.add(tidNodes.get(i));
            }
    }

    public HashMap<MemAccNode, HashSet<AllocaPair>> getMachtedAcc() {
        return allocator.machtedAcc;
    }

    public List<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> getReorderPairMap() {
        return reorderCrossPairs;
    }

    public HashMap<MemAccNode, DeallocNode> getTSAcc2Dealloc() {
//    throw new RuntimeException("Not implemented");
        return shared.acc2Dealloc;
    }

    public Long2ObjectOpenHashMap<ArrayList<WriteNode>> getTSAddr2SeqWrite() {
        return shared.addr2SeqWrite;
    }

    public ArrayList<AbstractNode> getAllNodeSeq() {
        return shared.allNodeSeq;
    }


    public Map<String, AbstractNode> getAllNodeMap() {
        return shared.allNodeMap;
    }

    public Long2ObjectOpenHashMap<ArrayList<ISyncNode>> get_syncNodesMap() {
        return _syncNodesMap;
    }

    public Short2ObjectOpenHashMap<ArrayList<AbstractNode>> getTSTid2sqeNodes() {
        return shared.tid2sqeNodes;
    }

    public Short2ObjectOpenHashMap<AbstractNode> getTidLastNode() {
        return tidLastNode;
    }

    public Short2ObjectOpenHashMap<AbstractNode> getTidFirstNode() {
        return tidFirstNode;
    }

    public Long2LongOpenHashMap getTSInitWrites() {
        return shared.initWrites;
    }

    public NewReachEngine getReachEngine() {
        return reachEngine;
    }


}
