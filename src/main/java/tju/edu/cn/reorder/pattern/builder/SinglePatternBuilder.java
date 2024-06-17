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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SinglePatternBuilder extends AbstractPatternBuilder<Pair<MemAccNode, MemAccNode>> {
    public SinglePatternBuilder(SimpleSolver solver) {
        super(solver);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.Single;
    }


    @Override
    public Set<Pair<MemAccNode, MemAccNode>> loadData(boolean onlyDynamic) {
        List<Pair<MemAccNode, MemAccNode>> racePairsList = solver.getCurrentIndexer().getRacePairsList();
        if (onlyDynamic) {
            for (Pair<MemAccNode, MemAccNode> pair1 : racePairsList) {
                Pair<MemAccNode, MemAccNode> result = isSinglePattern(pair1);
                data.add(result);
            }
        } else {
            //todo
            System.out.print("todo");
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
        return new RawReorder(searchContext.getSwitchPair(), null, result.schedule, result.logString);
    }

    @Override
    public SearchContext buildSearchContext(Pair<MemAccNode, MemAccNode> e, Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, PatternType patternType) {
        SearchContext searchContext = new SearchContext();
        searchContext.setSwitchPair(e);
        searchContext.setPatternType(patternType);
        searchContext.setMap(map);
        return searchContext;
    }


    private Pair<MemAccNode, MemAccNode> isSinglePattern(Pair<MemAccNode, MemAccNode> pair1) {
        // todo 屏障后面补
        MemAccNode first = pair1.key;
        MemAccNode second = pair1.value;
        return (first.gid < second.gid) ? new Pair<>(first, second) : new Pair<>(second, first);
    }

}
