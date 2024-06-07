package tju.edu.cn.reorder;


import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.config.Configuration;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.Result;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.*;
import tju.edu.cn.z3.Z3Run;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class SimpleSolver implements ReorderSolver {

    public int ct_vals = 0;
    public final IntArrayList ct_constr = new IntArrayList(200);

    protected AtomicInteger taskId = new AtomicInteger(0);// constraint taskId

    protected Configuration config;

    protected NewReachEngine reachEngine;

    // constraints below
    protected String constrDeclare;
    protected String constrSync;
    protected String constrCasual = "";

    public static final String CONS_SETLOGIC = "(set-logic QF_IDL)\n";// use integer difference logic
    public static final String CONS_CHECK_GETMODEL = "(check-sat)\n(get-model)\n(exit)";

    public SimpleSolver(Configuration config) {
        this.config = config;
    }

    @Override
    public void declareVariables(ArrayList<AbstractNode> trace) {
        StringBuilder sb = new StringBuilder(trace.size() * 30);
        List<String> variables = new ArrayList<>();

        for (AbstractNode node : trace) {
            String var = makeVariable(node);
            sb.append("(declare-const ").append(var).append(" Int)\n");
            variables.add(var);
            ct_vals++;
        }
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                sb.append("(assert (not (= ").append(variables.get(i)).append(" ").append(variables.get(j)).append(")))\n");
            }
        }

        constrDeclare = sb.toString();
    }



    public String rebuildIntraThrConstr(Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, Pair<MemAccNode, MemAccNode> reorderPair) {
        StringBuilder sb = new StringBuilder(Reorder.INITSZ_L * 10);
        for (ArrayList<AbstractNode> nodes : map.values()) {
            // at least cBegin/cEnd
            if (nodes == null || nodes.size() <= 1) continue;

            short tid = nodes.get(0).tid;
            AbstractNode firstNode = NewReachEngine.tidFirstNode.get(tid);
            if (firstNode != null){
                for (AbstractNode node : nodes) {
                    if (node.gid == firstNode.gid) continue;
                    sb.append("(assert(< ").append(makeVariable(firstNode)).append(' ').append(makeVariable(node)).append("))\n");
                }
            }

            AbstractNode lastNode = NewReachEngine.tidLastNode.get(tid);
            if (lastNode != null){
                for (AbstractNode node : nodes) {
                    if (node.gid == lastNode.gid) continue;
                    if (firstNode != null && node.gid == firstNode.gid) continue;
                    sb.append("(assert(< ").append(makeVariable(node)).append(' ').append(makeVariable(lastNode)).append("))\n");
                }
            }

            AbstractNode lastN = nodes.get(0);
            String lastVar = makeVariable(lastN);
            boolean skip = false;
            for (int i = 1; i < nodes.size(); i++) {
                AbstractNode curN = nodes.get(i);
                String var = makeVariable(curN);
                // 检查是否在 reorderPair 的范围内
                if (makeVariable(reorderPair.key).equals(lastVar)) {
                    skip = true;
                }
                if (skip) {
                    if (makeVariable(reorderPair.value).equals(var)) {
                        skip = false;
                    }
                    // 跳过排序约束
                    lastVar = var;
                    continue;
                }

                // 如果不在 reorderPair 的范围内，添加约束
                sb.append("(assert(< ").append(lastVar).append(' ').append(var).append("))\n");
                lastVar = var;
            }
        }
        return sb.toString();
    }


    @Override
    public void buildSyncConstr(Indexer index) {
        StringBuilder sb = new StringBuilder(Reorder.INITSZ_S * 10);

        Short2ObjectOpenHashMap<AbstractNode> firstNodes = NewReachEngine.tidFirstNode;
        Short2ObjectOpenHashMap<AbstractNode> lastNodes = NewReachEngine.tidLastNode;

        ArrayList<TStartNode> thrStartNodeList = NewReachEngine.thrStartNodeList;
        for (TStartNode node : thrStartNodeList) {
            short tid = node.tidKid;
            AbstractNode fnode = firstNodes.get(tid);
            if (fnode != null) {
                sb.append("(assert (< ").append(makeVariable(node)).append(' ').append(makeVariable(fnode)).append(" ))\n");
            }
        }
        ArrayList<TJoinNode> joinNodeList = NewReachEngine.joinNodeList;
        for (TJoinNode node : joinNodeList) {
            short tid = node.tid_join;
            AbstractNode lnode = lastNodes.get(tid);
            if (lnode != null) {
                sb.append("(assert (< ").append(makeVariable(lnode)).append(' ').append(makeVariable(node)).append(" ))\n");
            }
        }


        Long2ObjectOpenHashMap<ArrayList<LockPair>> addr2LpLs = index.getAddr2LockPairLs();
        StringBuilder constr = new StringBuilder(256);

        for (Long2ObjectMap.Entry<ArrayList<LockPair>> e1 : addr2LpLs.long2ObjectEntrySet()) {
            ArrayList<LockPair> lpLs = e1.getValue();

            for (int i = 0; i < lpLs.size(); i++) {
                for (int j = 0; j < lpLs.size(); j++) {
                    if (j == i) continue;

                    LockPair p1 = lpLs.get(i);
                    ISyncNode p1LK = p1.nLock;
                    ISyncNode p1UN = p1.nUnlock;
                    if (p1LK == null) continue;
                    short p1LKTid = p1LK.tid;

                    LockPair p2 = lpLs.get(j);
                    ISyncNode p2LK = p2.nLock;
                    ISyncNode p2UN = p2.nUnlock;

                    if (p2LK == null || p2LK.tid == p1LKTid) continue;

                    if (NewReachEngine.canReach(p1LK, p2LK) || NewReachEngine.canReach(p2LK, p1LK)) continue;
                    // parallel lock pairs

                    constr.setLength(0);
                    int count = 0;
                    if (p1UN != null) {
                        constr.append(" (< ").append(makeVariable(p1UN)).append(' ').append(makeVariable(p2LK)).append(' ').append(')');
                        count++;
                    }
                    if (p2UN != null) {
                        constr.append(" (< ").append(makeVariable(p2UN)).append(' ').append(makeVariable(p1LK)).append(' ').append(')');
                        count++;
                    }

                    if (count == 1) {
                        sb.append("(assert ").append(constr).append(')').append('\n');
                    }
                    if (count == 2) {
                        sb.append("(assert (or").append(constr).append("))").append('\n');
                    }

                } // for 2
            } // for 1

        } // for one lock

        constrSync = sb.toString();
    }


    Indexer currentIndexer;

    public void setCurrentIndexer(Indexer indexer) {
        this.currentIndexer = indexer;
    }


    public String buildReorderConstrOpt(ArrayList<ReadNode> allReadNodes, boolean influence) {

        Long2ObjectOpenHashMap<ArrayList<WriteNode>> indexedWriteNodes = currentIndexer.getTSAddr2SeqWrite();

        StringBuilder csb = new StringBuilder(1024);

        for (ReadNode readNode : allReadNodes) {
            if (readNode.tid == -1) {
                continue;  // Skip invalid read nodes
            }

            ArrayList<WriteNode> writeNodes = indexedWriteNodes.get(readNode.addr);
            if (writeNodes == null || writeNodes.isEmpty()) {
                continue;  // Skip if there are no write nodes for the address
            }

            WriteNode preNode = null;
            ArrayList<WriteNode> matchingWriteNodes = new ArrayList<>(64);

            // Find all write nodes that write the same value and are not reachable from the read node
            for (WriteNode writeNode : writeNodes) {
                if (writeNode.value == readNode.value && !NewReachEngine.canReach(readNode, writeNode)) {
                    if (writeNode.tid != readNode.tid) {
                        matchingWriteNodes.add(writeNode);
                    } else {
                        if (preNode == null || (preNode.gid < writeNode.gid && writeNode.gid < readNode.gid)) {
                            preNode = writeNode;
                        }
                    }
                }
            }
            if (preNode != null) {
                matchingWriteNodes.add(preNode);
            }

            if (!matchingWriteNodes.isEmpty()) {
                String varRead = makeVariable(readNode);

                StringBuilder consB = new StringBuilder();
                StringBuilder consBEnd = new StringBuilder();

                for (int i = 0; i < matchingWriteNodes.size(); i++) {
                    WriteNode wNode1 = matchingWriteNodes.get(i);
                    String varWrite1 = makeVariable(wNode1);

                    String consBPart = String.format("(< %s %s)\n", varWrite1, varRead);

                    StringBuilder consC = new StringBuilder();
                    StringBuilder consCEnd = new StringBuilder();
                    String lastConsD = null;

                    for (WriteNode wNode2 : writeNodes) {
                        if (!matchingWriteNodes.contains(wNode2) && !NewReachEngine.canReach(wNode2, wNode1) && !NewReachEngine.canReach(readNode, wNode2)) {
                            String varWrite2 = makeVariable(wNode2);
                            String consD = String.format("(or (< %s %s) (< %s %s ))\n", varRead, varWrite2, varWrite2, varWrite1);

                            if (lastConsD != null) {
                                consC.append("(and ").append(lastConsD);
                                consCEnd.append(" )");
                            }
                            lastConsD = consD;
                        }
                    }

                    if (lastConsD != null) {
                        consC.append(lastConsD);
                    }
                    consC.append(consCEnd);

                    if (consC.length() > 0) {
                        consBPart = String.format("(and %s %s )\n", consBPart, consC);
                    }

                    if (i + 1 < matchingWriteNodes.size()) {
                        consB.append("(or ").append(consBPart);
                        consBEnd.append(" )");
                    } else {
                        consB.append(consBPart);
                    }
                }

                consB.append(consBEnd);
                if (!influence) {
                    csb.append(String.format("%s", consB));
                } else {
                    csb.append(String.format("(not %s)", consB));
                }
            } else {
                // No matching value writes, ensure it reads the initial write
                String varRead = makeVariable(readNode);

                StringBuilder compositeConstraint = new StringBuilder();
                boolean firstConstraint = true;

                for (WriteNode wNode : writeNodes) {
                    if (wNode.tid != readNode.tid && !NewReachEngine.canReach(readNode, wNode)) {
                        String varWrite = makeVariable(wNode);
                        String consE = String.format("(< %s %s)", varRead, varWrite);
                        if (!firstConstraint) {
                            compositeConstraint.append(" ");
                        }
                        compositeConstraint.append(consE);
                        firstConstraint = false;
                    }
                }

                if (compositeConstraint.length() > 0) {
                    if (!influence) {
                        csb.append(String.format("(and %s)", compositeConstraint));
                    } else {
                        csb.append(String.format("(not (and %s))", compositeConstraint));
                    }
                }
            }
        }
        return csb.toString();
    }


    // 依赖
    public Result searchReorderSchedule(Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, Pair<MemAccNode, MemAccNode> switchPair, Pair<MemAccNode, MemAccNode> dependPair) {
        Result res = new Result();
        boolean doSolve = true;

        MemAccNode switchNode1 = switchPair.key;
        MemAccNode switchNode2 = switchPair.value;

        MemAccNode dependNode1 = dependPair.key;
        MemAccNode dependNode2 = dependPair.value;

        //改变支配变量的值
        ArrayList<ReadNode> dependReadNodes = new ArrayList<>();
        currentIndexer.getReorderDependentRead(dependReadNodes, dependNode1);
        String obeyStr = buildReorderConstrOpt(dependReadNodes, false);

        ArrayList<ReadNode> changeReadNodes = new ArrayList<>();
        currentIndexer.getReorderDependentRead(changeReadNodes, dependNode2);
        buildReorderConstrOpt(changeReadNodes, true);
        String violateStr = buildReorderConstrOpt(changeReadNodes, true);

        // 支配变量去改变别人
        ArrayList<ReadNode> swapRelReadNodes = new ArrayList<>();
        currentIndexer.getSwapBehindRelRead(swapRelReadNodes, switchNode1);
        String violateStr1 = buildReorderConstrOpt(swapRelReadNodes, true);

        if (obeyStr == null || obeyStr.isEmpty() || ((violateStr == null || violateStr.isEmpty()) && ((violateStr1 == null || violateStr1.isEmpty())))) return res;

        String finalVio = "(assert (or" + violateStr +  " " + violateStr1+ "))\n";
        String finalObeyStr = "(assert" + obeyStr + ")\n";


        // tid: a1 < a2 < a3
        String constrMHB = rebuildIntraThrConstr(map, switchPair);
        String switchNode1Str = makeVariable(switchNode1);
        String switchNode2Str = makeVariable(switchNode2);

        String csb = CONS_SETLOGIC + constrDeclare + constrMHB + constrSync + finalObeyStr + finalVio + "(assert (< " + switchNode2Str + " " + switchNode1Str + " ))" + CONS_CHECK_GETMODEL;
         res.logString = "CONS_SETLOGIC: " + CONS_SETLOGIC + "\n" +
                "constrDeclare: " + constrDeclare + "\n" +
                "constrMHB: " + constrMHB + "\n" +
                "constrSync: " + constrSync + "\n" +
                "obeyStr: " + finalObeyStr + "\n" +
                "violateStr: " + finalVio + "\n";

        synchronized (ct_constr) {
            ct_constr.push(Reorder.countMatches(csb, "assert"));
        }

        if (!doSolve) return null;

        Z3Run task = new Z3Run(config, taskId.getAndIncrement());
        res.schedule = task.buildSchedule(csb);
        return res;
    }


    @Override
    public boolean canReach(AbstractNode node1, AbstractNode node2) {
        return NewReachEngine.canReach(node1, node2);
    }

    public static String makeVariable(AbstractNode node) {
        return "x" + node.gid + "-" + node.tid;
    }

    public void reset() {
        this.constrDeclare = null;
        this.constrSync = null;
        this.constrCasual = null;
        this.taskId.set(0);
        this.reachEngine = null;
    }
}
