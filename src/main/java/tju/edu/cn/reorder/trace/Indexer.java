

package tju.edu.cn.reorder.trace;


import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


    public Short2ObjectOpenHashMap<ArrayList<AbstractNode>> getRawTid2seq() {
        return _rawTid2seq;
    }

    protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> _rawTid2seq = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> tid2CallSeq = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    public TraceMetaInfo metaInfo = new TraceMetaInfo();

    List<Pair<MemAccNode, MemAccNode>> racePairsList;



    private static class SharedAccIndexes {

        // z3 declare var, all shared acc and other nodes
        protected ArrayList<AbstractNode> allNodeSeq = new ArrayList<>(Reorder.INITSZ_L);

        protected Map<String, AbstractNode> allNodeMap = new HashMap<>();

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

    private final SharedAccIndexes shared = new SharedAccIndexes();


    public Indexer() {
        shared.initWrites.defaultReturnValue(-1);
    }

    public void addTidSeq(short tid, ArrayList<AbstractNode> seq) {
        ArrayList<AbstractNode> uniqueNodes = new ArrayList<>();
        if (!seq.isEmpty()) {
            for (int i = 0; i < seq.size(); i++) {
                AbstractNode previous3;
                AbstractNode previous2;
                AbstractNode previous1;

                AbstractNode current = seq.get(i);
                if (i-3>=0){
                    previous3 = seq.get(i-3);
                    previous2 = seq.get(i-2);
                    previous1= seq.get(i-1);
                    if (current.getAi() != null && current.getAi().equals(previous3.getAi()) && previous2 instanceof FuncExitNode && previous1 instanceof FuncEntryNode){
                        continue;
                    }
                }
                uniqueNodes.add(current);
            }
        }
        _rawTid2seq.put(tid, uniqueNodes);
        metaInfo.tidRawNodesCounts.put(tid, uniqueNodes.size());
        metaInfo.rawNodeCount += uniqueNodes.size();
        if (LOG.isTraceEnabled()) {
            for (AbstractNode n : uniqueNodes)
                LOG.trace(n.toString());
        }
    }



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
        racePairsList = new ArrayList<>(racePairs);

        // 3. third pass, handle shared mem acc (index: addr tid dealloc allnode)
        processReorderNodes(sharedAddrSet);
    }

    public void processReorderNodes(LongOpenHashSet sharedAddrSet) {
        for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> entry : _rawTid2seq.short2ObjectEntrySet()) {
            short tid = entry.getShortKey();
            ArrayList<AbstractNode> tidNodes = shared.tid2sqeNodes.computeIfAbsent(tid, k -> new ArrayList<>());

            for (AbstractNode node : entry.getValue()) {
                if (node instanceof MemAccNode) {
                    MemAccNode memNode = (MemAccNode) node;
                    if (sharedAddrSet.contains(memNode.getAddr())) {
                        addNodeToSharedAndTid(memNode, tidNodes);
                        handleTSMemAcc(memNode);
                    }
                } else if (!(node instanceof FuncEntryNode) && !(node instanceof FuncExitNode) && !(node instanceof AllocNode) && !(node instanceof DeallocNode)) {
                    addNodeToSharedAndTid(node, tidNodes);
                }
            }
        }
        metaInfo.countAllNodes = getAllNodeSeq().size();
    }

    public void addNodeToSharedAndTid(AbstractNode node, ArrayList<AbstractNode> tidNodes) {
        if (node != null && !shared.allNodeMap.containsKey(makeVariable(node))) {
            shared.allNodeSeq.add(node);
            shared.allNodeMap.put(makeVariable(node), node);
            tidNodes.add(node);
        }
    }


    public void addNodeToSharedAndTid(AbstractNode node) {
        if (node != null && !shared.allNodeMap.containsKey(makeVariable(node))) {
            shared.allNodeSeq.add(node);
            shared.allNodeMap.put(makeVariable(node), node);
            ArrayList<AbstractNode> tidNodes = shared.tid2sqeNodes.computeIfAbsent(node.tid, k -> new ArrayList<>());
            tidNodes.add(node);
        }
    }

    public Allocator getAllocator() {
        return allocator;
    }

    private final  Allocator allocator = new Allocator();

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
                    allocator.pushAlloc(an);

                } else if (node instanceof DeallocNode) {
                    // matching delloc with alloc, replacing alloc with dealloc
                    metaInfo.countDealloc++;
                    DeallocNode dnode = (DeallocNode) node;
                    allocator.pushDealloc(dnode);
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
                    if (node1.tid != node2.tid && !NewReachEngine.canReach(node1, node2)
                            && ((node1 instanceof WriteNode || node1 instanceof RangeWriteNode)
                            || (node2 instanceof WriteNode || node2 instanceof RangeWriteNode))) {
                        racePairs.add(new Pair<>(node1, node2));
                    }
                }
            }
        }
        racePairs.stream()
                .map(Object::toString)
                .forEach(LOG::info);
        return sharedAddrSet;
    }


    // called in the second pass
    // build tid addr dealloc index
    protected void handleTSMemAcc(MemAccNode node) {
        // index: addr -> acc
        long addr = node.getAddr();
        // index: seq read, seq write
        if (node instanceof RangeReadNode) {
            metaInfo.countRead++;
        } else if (node instanceof ReadNode) {
            metaInfo.countRead++;


            shared.addReadNode((ReadNode) node);

        } else if (node instanceof RangeWriteNode) {
            metaInfo.countRead++;

        } else if (node instanceof WriteNode) {
            metaInfo.countWrite++;
//        seqWrite.add((WriteNode) node);
            ArrayList<WriteNode> seqW = shared.addr2SeqWrite.get(addr);
            if (seqW == null) {
                seqW = new ArrayList<>(Reorder.INITSZ_L);
                shared.addr2SeqWrite.put(addr, seqW);
            }
            seqW.add((WriteNode) node);

            if (!shared.initWrites.containsKey(addr)) {
                shared.initWrites.put(addr, ((WriteNode) node).value);
            }
        }
    }

    Long2ObjectOpenHashMap<ArrayList<LockPair>> addr2LockPairLs = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);

    protected void handleSync2(short tid, ISyncNode node) {

        long addr = node.getAddr();
        ArrayList<ISyncNode> syncNodes = _syncNodesMap.get(addr);
        if (syncNodes == null) {
            syncNodes = new ArrayList<>(Reorder.INITSZ_S);
            _syncNodesMap.put(addr, syncNodes);
        }
        syncNodes.add(node);

        if (node instanceof LockNode) {
            Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
            if (stack == null) {
                stack = new Stack<>();
                _tid2SyncStack.put(tid, stack);
            }
            stack.push(node);
            metaInfo.countLock++;

        } else if (node instanceof UnlockNode) {
            metaInfo.countUnlock++;
            Long2ObjectOpenHashMap<ArrayList<LockPair>> indexedLockpairs = _tid2LockPairs.get(tid);
            if (indexedLockpairs == null) {
                indexedLockpairs = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);
                _tid2LockPairs.put(tid, indexedLockpairs);
            }
            long lockId = ((UnlockNode) node).lockID;
            ArrayList<LockPair> lockpairLs = indexedLockpairs.get(lockId);
            if (lockpairLs == null) {
                lockpairLs = new ArrayList<>();
                indexedLockpairs.put(lockId, lockpairLs);
            }

            Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
            if (stack == null) {
                stack = new Stack<>();
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
                    addrLpLs = new ArrayList<>(Reorder.INITSZ_S * 5);
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
                    indexedLockpairs = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);
                    _tid2LockPairs.put(tid, indexedLockpairs);
                }

                while (!stack.isEmpty()) {
                    ISyncNode syncnode = stack.pop();//nLock or wait
                    long addr = syncnode.getAddr();
                    ArrayList<LockPair> lockpairs = indexedLockpairs.get(addr);
                    if (lockpairs == null) {
                        lockpairs = new ArrayList<>(Reorder.INITSZ_S);
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


    Long2ObjectOpenHashMap<ArrayList<ISyncNode>> _syncNodesMap = new Long2ObjectOpenHashMap<>(Reorder.INITSZ_S);

    Short2ObjectOpenHashMap<Long2ObjectOpenHashMap<ArrayList<LockPair>>> _tid2LockPairs = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);

    Short2ObjectOpenHashMap<Stack<ISyncNode>> _tid2SyncStack = new Short2ObjectOpenHashMap<>(Reorder.INITSZ_S / 2);


    public void getReorderDependentRead1(ArrayList<ReadNode> allReadNodes, MemAccNode node) {

        ArrayList<ReadNode> tidNodes = shared.tid2seqReads.get(node.tid);
        if (tidNodes == null || tidNodes.isEmpty()) return;
        for (ReadNode tidNode : tidNodes) {
            if (tidNode.addr == node.addr) {
                allReadNodes.add(tidNode);
            }
        }
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

    public Short2ObjectOpenHashMap<ArrayList<AbstractNode>> getTSTid2sqeNodes() {
        return shared.tid2sqeNodes;
    }


    public List<Pair<MemAccNode, MemAccNode>> getRacePairsList(){
        return racePairsList;
    }

}
