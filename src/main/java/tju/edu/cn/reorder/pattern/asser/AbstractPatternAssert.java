package tju.edu.cn.reorder.pattern.asser;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import tju.edu.cn.reorder.NewReachEngine;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.ReadNode;
import tju.edu.cn.trace.WriteNode;

import java.util.ArrayList;

import static tju.edu.cn.reorder.SimpleSolver.makeVariable;

public abstract class AbstractPatternAssert implements PatternAssert {

    @Override
    public abstract String buildReorderAssert(AssertContext assertContext);


    protected String buildReorderConstrOpt(Indexer currentIndexer, ArrayList<ReadNode> allReadNodes, boolean influence) {

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
}
