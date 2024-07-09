package tju.edu.cn.reorder.pattern.builder;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.reorder.SimpleSolver;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.misc.Result;
import tju.edu.cn.reorder.pattern.PatternType;
import tju.edu.cn.reorder.trace.EventLoader;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

// key是switch value 依赖
public class CrossPatternBuilder extends AbstractPatternBuilder<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> {

    public CrossPatternBuilder(SimpleSolver solver) {
        super(solver);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.Cross;
    }



    @Override
    public Set<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> loadData(boolean onlyDynamic) {
        List<Pair<MemAccNode, MemAccNode>> racePairsList = solver.getCurrentIndexer().getRacePairsList();
        if (onlyDynamic) {
            for (int i = 0; i < racePairsList.size(); i++) {
                for (int j = i + 1; j < racePairsList.size(); j++) {
                    Pair<MemAccNode, MemAccNode> pair1 = racePairsList.get(i);
                    Pair<MemAccNode, MemAccNode> pair2 = racePairsList.get(j);
                    Set<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> crossPatterns = isCrossPattern(pair1, pair2);
                    if (!crossPatterns.isEmpty()) {
                        data.addAll(crossPatterns);
                    }
                }
            }
        } else {
            Pair<MemAccNode, MemAccNode> switchPair = new Pair<>();
            Pair<MemAccNode, MemAccNode> dependentPair = new Pair<>();
//                for (Pair<MemAccNode, MemAccNode> pair : racePairsList) {
//                    if (pair.key.equals(memNode) || pair.value.equals(memNode)) {
//                        if (memNode.order == 500) {
//                            switchPair.key = memNode;
//                        }
//                        if (memNode.order == 501) {
//                            switchPair.value = memNode;
//                        }
//                        if (memNode.order == 1048) {
//                            dependentPair.key = memNode;
//                        }
//                        if (memNode.order == 1051) {
//                            dependentPair.value = memNode;
//                        }
//                    }
//                }
            data.add(new Pair<>(switchPair, dependentPair));
        }
        return data;
    }

    @Override
    public void displayRawReorders(List<RawReorder> rawReorders, EventLoader traceLoader, String outputName) {
        Indexer indexer = solver.getCurrentIndexer();
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputName))) {

            for (RawReorder rawReorder : rawReorders) {
                String header = "RawReorder:";
                System.out.println(header);
                writer.println(header);

                String logString = "  constr: " + rawReorder.logString;
                System.out.println(logString);
                writer.println(logString);


                String switchPair = "  Switch Pair: " + rawReorder.switchPair;
                System.out.println(switchPair);
                writer.println(switchPair);

                String dependPair = "  Depend Pair: " + rawReorder.dependPair;
                System.out.println(dependPair);
                writer.println(dependPair);

                String scheduleHeader = "  Schedule:";
                System.out.println(scheduleHeader);
                writer.println(scheduleHeader);

                for (String s : rawReorder.schedule) {
                    String[] parts = s.split("-");
                    short tid = Short.parseShort(parts[1]);
                    String color = traceLoader.threadColorMap.get(tid);
                    AbstractNode node = indexer.getAllNodeMap().get(s);

                    String nodeString = node != null ? node.toString() : "[Node not found]";
                    String line = color + "    " + s + "    " + nodeString + "\u001B[0m";

                    if (isPartOfPair(rawReorder.switchPair, node)) {
                        line += " * Swap";
                    } else if (isPartOfPair(rawReorder.dependPair, node)) {
                        line += " * Depend";
                    }

                    System.out.println(line);  // Print colored line to console
                    writer.println(line);       // Write colored line to file
                }
                System.out.println();
                writer.println();
            }
        } catch (IOException e) {
            LOG.error("An error occurred: {}", e.getMessage(), e);
        }
    }

    @Override
    public RawReorder buildRawReorder(SearchContext searchContext, Result result) {
        return new RawReorder(searchContext.getSwitchPair(), searchContext.getDependPair(), result.schedule, result.logString);
    }

    @Override
    public SearchContext doBuildSearchContext(Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>> e, Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, PatternType patternType) {
        SearchContext searchContext = new SearchContext();
        final Pair<MemAccNode, MemAccNode> switchPair = e.key;
        final Pair<MemAccNode, MemAccNode> dependPair = e.value;
        searchContext.setSwitchPair(switchPair);
        searchContext.setDependPair(dependPair);
        searchContext.setPatternType(patternType);
        searchContext.setMap(map);
        return searchContext;
    }



    /**
     * op1A                  op3B
     * op2B                  op4A
     */
    private static Set<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> isCrossPattern(Pair<MemAccNode, MemAccNode> pair1, Pair<MemAccNode, MemAccNode> pair2) {
        MemAccNode op1A = pair1.key;
        MemAccNode op4A = pair1.value;
        MemAccNode op2B = pair2.key;
        MemAccNode op3B = pair2.value;
        Set<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> result = new HashSet<>();

        if (op1A.tid == op2B.tid && op3B.tid == op4A.tid && ((op1A.gid < op2B.gid && op3B.gid < op4A.gid) || (op1A.gid > op2B.gid && op3B.gid > op4A.gid))) {
            Pair<MemAccNode, MemAccNode> pair1Sorted = (op1A.gid < op2B.gid) ? new Pair<>(op1A, op2B) : new Pair<>(op2B, op1A);
            Pair<MemAccNode, MemAccNode> pair2Sorted = (op3B.gid < op4A.gid) ? new Pair<>(op3B, op4A) : new Pair<>(op4A, op3B);
            result.add(new Pair<>(pair1Sorted, pair2Sorted));
            result.add(new Pair<>(pair2Sorted, pair1Sorted));
        }

        if (op1A.tid == op3B.tid && op2B.tid == op4A.tid && ((op1A.gid < op3B.gid && op2B.gid < op4A.gid) || (op1A.gid > op3B.gid && op2B.gid > op4A.gid))) {
            Pair<MemAccNode, MemAccNode> pair1Sorted = (op1A.gid < op3B.gid) ? new Pair<>(op1A, op3B) : new Pair<>(op3B, op1A);
            Pair<MemAccNode, MemAccNode> pair2Sorted = (op2B.gid < op4A.gid) ? new Pair<>(op2B, op4A) : new Pair<>(op4A, op2B);
            result.add(new Pair<>(pair1Sorted, pair2Sorted));
            result.add(new Pair<>(pair2Sorted, pair1Sorted));
        }

        return result;
    }
}
