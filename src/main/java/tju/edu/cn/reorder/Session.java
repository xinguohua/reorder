package tju.edu.cn.reorder;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.config.Configuration;
import tju.edu.cn.reorder.misc.Addr2line;
import tju.edu.cn.reorder.misc.RawReorder;
import tju.edu.cn.reorder.pattern.builder.PatternBuilder;
import tju.edu.cn.reorder.pattern.builder.PatternBuilderFactory;
import tju.edu.cn.reorder.trace.EventLoader;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.reorder.trace.TLEventSeq;

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

    public void init() {

        addr2line = new Addr2line(traceLoader.getModuleList());
        windowSize = (int) config.window_size;
        if (windowSize < 10) {
            windowSize = (int) (Reorder.MAX_MEM_SIZE * 0.9 / Reorder.AVG_EVENT / traceLoader.fileInfoMap.size()
                    // half mem for events, half for z3
                    / 0.7);
            LOG.info("Suggested window size {}", windowSize);
        }
        traceLoader.init();
    }

    public void start() {
        traceLoader.loadAllEvent(addr2line);
        traceLoader.processSycn();
        printTraceStats();
        while (traceLoader.hasNext()) {
            Indexer indexer = new Indexer();
            // load
            traceLoader.updateIndexerWithAliveThreads(indexer);

            // node && Constraint
            indexer.processNode();

            // pattern process
            patternBuildProcess(indexer);
        }
    }

    private void patternBuildProcess(Indexer indexer) {
        solver.setCurrentIndexer(indexer);
        PatternBuilder<?> patternBuilder = PatternBuilderFactory.getPatternBuilder(config.patternType, solver);
        Set set = patternBuilder.loadData(config.only_dynamic);
        if (set == null || set.isEmpty()) return;
        prepareConstraints(indexer);
        List<RawReorder> rawReorders = patternBuilder.solveReorderConstr(set.iterator(), Reorder.PAR_LEVEL);
        patternBuilder.displayRawReorders(rawReorders, traceLoader, config.outputName);
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


    protected void prepareConstraints(Indexer indexer) {
        solver.declareVariables(indexer.getAllNodeSeq());
        // start < tid_first
        solver.buildSyncConstr(indexer);
    }

}
