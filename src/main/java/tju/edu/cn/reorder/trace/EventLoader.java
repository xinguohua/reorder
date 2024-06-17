package tju.edu.cn.reorder.trace;


import it.unimi.dsi.fastutil.shorts.Short2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.reorder.NewReachEngine;
import tju.edu.cn.reorder.Reorder;
import tju.edu.cn.reorder.misc.Addr2line;
import tju.edu.cn.reorder.misc.CModuleList;
import tju.edu.cn.reorder.misc.CModuleSection;
import tju.edu.cn.trace.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class EventLoader {

    private static final Logger LOG = LoggerFactory.getLogger(EventLoader.class);

    private static final String[] COLORS = {
            "\u001B[31m", // Red
            "\u001B[32m", // Green
            "\u001B[33m", // Yellow
            "\u001B[34m", // Blue
            "\u001B[35m", // Magenta
            "\u001B[36m", // Cyan
            "\u001B[37m"  // White
    };

    public Map<Short, String> threadColorMap = new HashMap<>();

    Map<Short, TLEventSeq> fileSeqinfo = new HashMap<>();



    public final ExecutorService exe;
    public final String folderName;
    public final Short2ObjectRBTreeMap<FileInfo> fileInfoMap;
    private final ShortOpenHashSet aliveTids = new ShortOpenHashSet(80);
    private ShortOpenHashSet allThreads = new ShortOpenHashSet(80);

    public final CModuleList moduleList = new CModuleList();

    public EventLoader(ExecutorService exe, String folderName) {
        this.exe = exe;
        this.folderName = folderName;
        fileInfoMap = new Short2ObjectRBTreeMap<>();
    }

    public void init() {
        final File dir = new File(folderName);
        File[] traces = dir.listFiles(f -> {
            if (!f.canRead()) {
                throw new IllegalArgumentException("Could not read file " + f + "  at " + dir);
            }
            return f.isFile();
        });
        if (traces == null) throw new RuntimeException("Could not find folder " + folderName);
        if (traces.length == 0) throw new IllegalArgumentException("No trace file found at " + folderName);

        for (File f : traces) {
            switch (f.getName()) {
                case Reorder.MODULE_TXT:
                    loadCModuleInfo(f);
                    continue;
                case Reorder.STAT_TXT:
                    continue;
                case Reorder.STAT_CSV:
                    continue;
            }
            short tid = Short.parseShort(f.getName());
            long sz = f.length();
            FileInfo fi = new FileInfo(f, sz, tid);
            fileInfoMap.put(tid, fi);
            threadColorMap.put(tid,  COLORS[tid% COLORS.length]);
            allThreads.add(tid);
        }

        if (moduleList.size() < 1) LOG.error("Empty module info " + moduleList);


        short mainTid = fileInfoMap.firstShortKey();
        aliveTids.add(mainTid);
        fileInfoMap.get(mainTid).enabled = true;
    }

//  protected AtomicInteger gidGen = new AtomicInteger(1);

    public int validCount() {
        return fileSeqinfo.size();
    }

    public boolean hasNext() {
        return validCount() > 0;
    }


    void loadCModuleInfo(File f) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(f));
            String line = reader.readLine();
            while (null != line) {
                String[] infoLs = line.split("\\|");
                int idx = 0;
                String moduleFullName = infoLs[idx++]; // name with path

                // 要替换的子字符串
                String target = "/ufo/reorder";

                // 新的子字符串
                String replacement = "./module";

                // 执行替换
                String newModuleFullName = moduleFullName.replace(target, replacement);

                try {
                    long base = Long.parseUnsignedLong(infoLs[idx++].trim(), 16);
                    //JEFF
                    long max = Long.parseUnsignedLong(infoLs[idx++].trim(), 16);

                    if (max > base) {
                        CModuleSection m = new CModuleSection(newModuleFullName, base, max);
                        moduleList.add(m);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
    }

    public void updateIndexerWithAliveThreads(Indexer mIdx) {
        int tidCount = aliveTids.size();
        ShortOpenHashSet currentTids = new ShortOpenHashSet(aliveTids);
        HashSet<Short> visited = new HashSet<>();
        while (!currentTids.isEmpty()) {
            ShortOpenHashSet nextTids = addTLSeq1(mIdx, currentTids, visited);
            tidCount += nextTids.size();
            updateAliveTids(nextTids);
            currentTids = nextTids;
        }

        removeVisitSeq(visited);
        mIdx.metaInfo.tidCount = tidCount;
    }




    private ShortOpenHashSet addTLSeq1(Indexer mIdx, ShortOpenHashSet tids, HashSet<Short> visited) {
        ShortOpenHashSet newTids = new ShortOpenHashSet(3);
        for (short tid : tids) {
            TLEventSeq seq1 = fileSeqinfo.get(tid);
            if (seq1.events != null && !seq1.events.isEmpty()) {
                visited.add(seq1.tid);
                mIdx.addTidSeq(seq1.tid, seq1.events);
            }
            if (seq1.newTids.isEmpty()) continue;
            newTids.addAll(seq1.newTids);
        } // for
        return newTids;
    }




    private void updateAliveTids(ShortOpenHashSet newTids) {
        newTids.removeAll(aliveTids);
        aliveTids.addAll(newTids);
    }

    private void removeVisitSeq(HashSet<Short> visited) {
        fileSeqinfo.entrySet().removeIf(shortTLEventSeqEntry -> visited.contains(shortTLEventSeqEntry.getKey()));
    }

    public CModuleList getModuleList() {
        return moduleList;
    }


    public void loadAllEvent(Addr2line addr2line) {
        for (short tid : allThreads) {
            FileInfo fi = fileInfoMap.get(tid);
            if (fi != null) {
                TLEventSeq seq = new NewLoadingTask(fi).loadAllEvent(addr2line);
                LOG.info("tid: " + tid + " Total Events:  " + seq.numOfEvents);
                fileSeqinfo.put(tid, seq);
                //reset file info so that it can be reused again
                fi.fileOffset = 0;
                fi.lastFileOffset = 0;
            }
        }
        System.out.println(fileSeqinfo);
    }

    public void processSycn() {
        for (short tid : allThreads) {
            TLEventSeq tlEventSeq = fileSeqinfo.get(tid);
            if (tlEventSeq != null && tlEventSeq.events != null) {
                for (AbstractNode node : tlEventSeq.events) {
                    if (node != null) {
                        if (node instanceof TBeginNode) {
                            NewReachEngine.saveToThreadFirstNode(tid, (TBeginNode) node);
                        } else if (node instanceof TEndNode) {
                            NewReachEngine.saveToThreadLastNode(tid, (TEndNode) node);
                        } else if (node instanceof TStartNode) {
                            NewReachEngine.saveToStartNodeList((TStartNode) node);
                        } else if (node instanceof TJoinNode) {
                            NewReachEngine.saveToJoinNodeList((TJoinNode) node);
                        } else if (node instanceof WaitNode) {
                            TLEventSeq.stat.c_isync++;
                            NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
                        } else if (node instanceof NotifyNode) {
                            TLEventSeq.stat.c_isync++;
                            NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
                        } else if (node instanceof NotifyAllNode) {
                            TLEventSeq.stat.c_isync++;
                            NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
                        }
                    }
                }
            }
            NewReachEngine.setThreadIdsVectorClock(allThreads.toShortArray());
            NewReachEngine.processSyncNode();
        }
    }
}
