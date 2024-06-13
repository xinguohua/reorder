package tju.edu.cn.reorder.pattern;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import tju.edu.cn.reorder.NewReachEngine;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.MemAccNode;
import tju.edu.cn.trace.ReadNode;
import tju.edu.cn.trace.WriteNode;

import java.util.ArrayList;

import static tju.edu.cn.reorder.SimpleSolver.makeVariable;

public class InitialPatternBuilder implements PatternBuilder {
    @Override
    public String buildReorderAssert(Indexer currentIndexer, MemAccNode switchNode1, MemAccNode switchNode2, MemAccNode dependNode1, MemAccNode dependNode2) {
        ArrayList<ReadNode> dependReadNodes1 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(dependReadNodes1, dependNode1);
        String obeyStr1 = buildReorderConstrOpt(currentIndexer, dependReadNodes1, false);
        String violateStr1 = buildReorderConstrOpt(currentIndexer, dependReadNodes1, true);

        ArrayList<ReadNode> dependReadNodes2 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(dependReadNodes2, dependNode2);
        String obeyStr2 = buildReorderConstrOpt(currentIndexer, dependReadNodes2, false);
        String violateStr2 = buildReorderConstrOpt(currentIndexer, dependReadNodes2, true);


        ArrayList<ReadNode> swapReadNodes1 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(swapReadNodes1, switchNode1);
        String obeyStr3 = buildReorderConstrOpt(currentIndexer, swapReadNodes1, false);
        String violateStr3 = buildReorderConstrOpt(currentIndexer, swapReadNodes1, true);


        ArrayList<ReadNode> swapReadNodes2 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(swapReadNodes2, switchNode2);
        String obeyStr4 = buildReorderConstrOpt(currentIndexer, swapReadNodes2, false);
        String violateStr4 = buildReorderConstrOpt(currentIndexer, swapReadNodes2, true);

        ArrayList<String> combinations = generateCombinations(violateStr1, violateStr2, violateStr3, violateStr4, obeyStr1, obeyStr2, obeyStr3, obeyStr4);
        if (combinations.isEmpty()) return null;
        return buildFinalAssert(combinations);
    }


    public String buildReorderConstrOpt(Indexer currentIndexer, ArrayList<ReadNode> allReadNodes, boolean influence) {

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


    public ArrayList<String> generateCombinations(String violateStr1, String violateStr2, String violateStr3, String violateStr4, String obeyStr1, String obeyStr2, String obeyStr3, String obeyStr4) {
        ArrayList<String> combinations = new ArrayList<>();
        if (violateStr1 != null && !violateStr1.isEmpty()) {
            combinations.add("(and " + violateStr1 + " " + obeyStr2 + " " + obeyStr3 + " " + obeyStr4 + ")\n");
        }
        if (violateStr2 != null && !violateStr2.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + violateStr2 + " " + obeyStr3 + " " + obeyStr4 + ")\n");
        }
        if (violateStr3 != null && !violateStr3.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + obeyStr2 + " " + violateStr3 + " " + obeyStr4 + ")\n");
        }
        if (violateStr4 != null && !violateStr4.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + obeyStr2 + " " + obeyStr3 + " " + violateStr4 + ")\n");
        }
        return combinations;
    }

    public String buildFinalAssert(ArrayList<String> combinations) {
        StringBuilder finalAssert = new StringBuilder("(assert (or ");
        for (String combination : combinations) {
            finalAssert.append(combination.trim()).append(" ");
        }
        finalAssert.append("))\n");
        return finalAssert.toString();
    }
}
