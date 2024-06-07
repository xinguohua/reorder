package tju.edu.cn.reorder;


import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.config.Configuration;
import tju.edu.cn.reorder.misc.Addr2line;
import tju.edu.cn.reorder.misc.Pair;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.misc.Result;
import tju.edu.cn.reorder.trace.EventLoader;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.reorder.trace.TLEventSeq;
import tju.edu.cn.trace.AbstractNode;
import tju.edu.cn.trace.MemAccNode;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;


public class Session {
    protected static final Logger LOG = LoggerFactory.getLogger(Session.class);

    public final Configuration config;
    public final EventLoader traceLoader;
    public final SimpleSolver solver;
    public Addr2line addr2line;
    public final ExecutorService exe;
    protected int windowSize;

    public Session(Configuration c) {
        config = c;
        exe = Executors.newFixedThreadPool(Reorder.PAR_LEVEL);
        traceLoader = new EventLoader(exe, config.traceDir);
        solver = new SimpleSolver(config);
    }


    int loadedEventCount = 0;


    public void init() {

        addr2line = new Addr2line(traceLoader.getModuleList());
        windowSize = (int) config.window_size;
        if (windowSize < 10) {
            windowSize = (int) (Reorder.MAX_MEM_SIZE * 0.9 / Reorder.AVG_EVENT / traceLoader.fileInfoMap.size()
                    // half mem for events, half for z3
                    / 0.7);
            LOG.info("Suggested window size {}", windowSize);
        }
        traceLoader.init(windowSize);
    }

    public void start() {
        traceLoader.loadAllEvent(addr2line);
        traceLoader.processSycn();
        printTraceStats();
        while (traceLoader.hasNext()) {
            Indexer indexer = new Indexer();
            traceLoader.updateIndexerWithAliveThreads(indexer);

            //1. set the number of threads
            //2. assign index to each thread
            indexer.processNode(config.only_dynamic);
            loadedEventCount += indexer.metaInfo.rawNodeCount;


            Set<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> reorderPairMaps = indexer.getReorderPairMap();
            if (reorderPairMaps == null || reorderPairMaps.isEmpty()) return;

            prepareConstraints(indexer);

            solver.setCurrentIndexer(indexer);

            List<RawReorder> rawReorders = solveReorderConstr(indexer.getTSTid2sqeNodes(), indexer.getReorderPairMap().iterator(), Reorder.PAR_LEVEL);

            displayRawReorders(rawReorders, indexer, traceLoader, config.outputName);
        }
        exe.shutdownNow();
    }


    public static void displayRawReorders(List<RawReorder> rawReorders, Indexer indexer, EventLoader traceLoader, String outputName) {
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


    private static boolean isPartOfPair(Pair<MemAccNode, MemAccNode> pair, AbstractNode node) {
        // Check if node matches either part of the pair
        return node != null && (nodeMatchesMemAccNode(node, pair.key) || nodeMatchesMemAccNode(node, pair.value));
    }

    private static boolean nodeMatchesMemAccNode(AbstractNode node, AbstractNode node1) {
        return node.gid == node1.gid && node.tid == node1.tid;
    }


    public void printTraceStats() {
        System.out.println("Start Events: " + TLEventSeq.stat.c_tstart);
        System.out.println("Join Events: " + TLEventSeq.stat.c_join);
        System.out.println("Lock Events: " + TLEventSeq.stat.c_lock);
        System.out.println("Unlock Events: " + TLEventSeq.stat.c_unlock);
        System.out.println("Wait/Notify Events: " + TLEventSeq.stat.c_isync);

        long totalsync = TLEventSeq.stat.c_tstart + TLEventSeq.stat.c_join + TLEventSeq.stat.c_lock + TLEventSeq.stat.c_unlock + TLEventSeq.stat.c_isync;

        System.out.println("Alloc Events: " + TLEventSeq.stat.c_alloc);
        System.out.println("DeAlloc Events: " + TLEventSeq.stat.c_dealloc);

        //total reads
        long reads = TLEventSeq.stat.c_read[0];
        for (int i = 1; i < 4; i++)
            reads += TLEventSeq.stat.c_read[i];

        //total writes
        long writes = TLEventSeq.stat.c_write[0];
        for (int i = 1; i < 4; i++)
            writes += TLEventSeq.stat.c_write[i];

        long toreads = reads + TLEventSeq.stat.c_range_r;
        long towrites = writes + TLEventSeq.stat.c_range_w;


        System.out.println("Total Sync Events: " + totalsync);
        System.out.println("Total Alloc Events: " + TLEventSeq.stat.c_alloc);
        System.out.println("Total USE Events: " + (toreads + towrites));
        System.out.println("Total Free Events: " + TLEventSeq.stat.c_dealloc);
        System.out.println("Total Read Events: " + toreads);
        System.out.println("Total Write Events: " + towrites);
        System.out.println("Total Events: " + TLEventSeq.stat.c_total);
    }


    public List<RawReorder> solveReorderConstr(final Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map,
                                               Iterator<Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>>> iter, int limit) {

        for (ArrayList<AbstractNode> nodes : map.values()) {
            nodes.sort(Comparator.comparingInt(AbstractNode::getGid));
        }

        CompletionService<RawReorder> cexe = new ExecutorCompletionService<>(exe);

        int task = 0;
        while (iter.hasNext() && limit > 0) {
            limit--;
            Pair<Pair<MemAccNode, MemAccNode>, Pair<MemAccNode, MemAccNode>> e = iter.next();
            final Pair<MemAccNode, MemAccNode> switchPair = e.key;
            final Pair<MemAccNode, MemAccNode> dependPair = e.value;

            cexe.submit(() -> {
                Result result = solver.searchReorderSchedule(map, switchPair, dependPair);
                if (result.schedule != null) return new RawReorder(switchPair, dependPair, result.schedule, result.logString);
                else return null;

            });
            task++;
        }

        ArrayList<RawReorder> ls = new ArrayList<>(task);
        try {
            while (task-- > 0) {
                Future<RawReorder> f = cexe.take(); //blocks if none available
                RawReorder rawReorder = f.get();
                if (rawReorder != null) ls.add(rawReorder);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ls;
    }


    protected void prepareConstraints(Indexer indexer) {
        solver.declareVariables(indexer.getAllNodeSeq());
        // start < tid_first
        solver.buildSyncConstr(indexer);
    }

}
