package tju.edu.cn.reorder.pattern.builder;

import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import tju.edu.cn.reorder.SimpleSolver;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.misc.Result;
import tju.edu.cn.reorder.pattern.PatternType;
import tju.edu.cn.reorder.trace.AllocaPair;
import tju.edu.cn.reorder.trace.EventLoader;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.reorder.trace.TLAllocQ;
import tju.edu.cn.trace.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class InitialPatternBuilder extends AbstractPatternBuilder<Pair<MemAccNode, MemAccNode>> {
    public InitialPatternBuilder(SimpleSolver solver) {
        super(solver);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.Initial;
    }


    @Override
    public Set<Pair<MemAccNode, MemAccNode>> loadData(boolean onlyDynamic) {
        List<Pair<MemAccNode, MemAccNode>> racePairsList = solver.getCurrentIndexer().getRacePairsList();
        if (onlyDynamic) {
            for (int i = 0; i < racePairsList.size(); i++) {
                Pair<MemAccNode, MemAccNode> pair = racePairsList.get(i);
                Set<Pair<MemAccNode, MemAccNode>> initialPatterns = isInitialPattern(pair, solver.getCurrentIndexer());

                if (!initialPatterns.isEmpty()) {
                    data.addAll(initialPatterns);
                }
            }
        } else {
            //todo
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
    protected SearchContext doBuildSearchContext(Pair<MemAccNode, MemAccNode> e, Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map, PatternType patternType) {
        SearchContext searchContext = new SearchContext();
        searchContext.setSwitchPair(e);
        searchContext.setPatternType(patternType);
        searchContext.setMap(map);
        return searchContext;
    }


    private static Set<Pair<MemAccNode, MemAccNode>> isInitialPattern(Pair<MemAccNode, MemAccNode> pair,  Indexer indexer) {
        WriteNode writeNode;
        ReadNode readNode = null;
        Short2ObjectOpenHashMap<ArrayList<AbstractNode>> rawTid2seq = indexer.getRawTid2seq();
        Short2ObjectOpenHashMap<TLAllocQ> tid2AllocQ = indexer.getAllocator().getTid2AllocQ();
        Set<Pair<MemAccNode, MemAccNode>> result = new HashSet<>();

        if (pair.key instanceof WriteNode && pair.value instanceof ReadNode) {
            writeNode = (WriteNode) pair.key;
            readNode = (ReadNode) pair.value;
        } else if (pair.key instanceof ReadNode && pair.value instanceof WriteNode) {
            writeNode = (WriteNode) pair.value;
            readNode = (ReadNode) pair.key;
        } else {
            writeNode = null;
        }
        if (writeNode == null) return result;

        long accAddr = writeNode.value;


        for (Short2ObjectMap.Entry<TLAllocQ> e : tid2AllocQ.short2ObjectEntrySet()) {

            TLAllocQ q = e.getValue();
            ObjectCollection<ArrayList<AllocaPair>> lss = q.tree.tailMap(accAddr).values();
            for (ArrayList<AllocaPair> ls : lss)
                for (AllocaPair p : ls) {
                    AllocNode allocNode = p.allocNode;
                    if (accAddr >= allocNode.addr && accAddr <= allocNode.addr + allocNode.length && allocNode.tid == writeNode.tid && allocNode.order < writeNode.order) {
                        Optional<WriteNode> constructorOpt = Optional.ofNullable(rawTid2seq.get(writeNode.tid))
                                .flatMap(nodes -> nodes.stream().filter(node -> node instanceof WriteNode)
                                        .map(node -> (WriteNode) node).filter(node -> node.order > allocNode.order && node.order < writeNode.order)
                                        .filter(node -> node.addr >= allocNode.addr && node.addr <= allocNode.addr + allocNode.length)
                                        .findFirst());
                        if (constructorOpt.isPresent()){
                            result.add(new Pair<>(writeNode, constructorOpt.get()));
                            indexer.addNodeToSharedAndTid(constructorOpt.get());
                        }

                    }
                }
            // tail map
        } // tid
        return result;
    }
}
