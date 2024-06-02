package tju.edu.cn.reorder.trace;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.trace.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public class LoadingTask2 implements Callable<TLEventSeq> {

    private static final Logger LOG = LoggerFactory.getLogger(LoadingTask2.class);

    public final FileInfo fileInfo;
    public final long eLimit;

    public LoadingTask2(FileInfo fi, long limit) {
        this.fileInfo = fi;
        this.eLimit = limit;
    }

    public TLEventSeq call() throws Exception {
        return load();
    }

    private MemAccNode lastMemAcc = null;

    public TLEventSeq load() {
        final short tid = fileInfo.tid;
        TLEventSeq seq = new TLEventSeq(tid);
        seq.events = new ArrayList<>((int) eLimit); // fast util big list

        TLHeader header;
        try {
            ByteReader br = new BufferedByteReader();
            br.init(fileInfo);
            int bnext;
            if (fileInfo.fileOffset == 0) {
                bnext = br.read();
                if (bnext != -1) {
                    header = getHeader(bnext, br);
                    seq.header = header;
                }
            }
            bnext = br.read();
            int eCount = 0;

            try {
                while (bnext != -1 && eCount < eLimit) {
                    AbstractNode node = getNode(tid, bnext, br, seq.stat);

                    if (node != null) {
                        seq.numOfEvents++;

                        node.gid = (int) Bytes.longs.add(tid, seq.numOfEvents);//check consistency
                        LOG.debug("aser.ufo.trace.LoadingTask2.load" + node);

                        if (node instanceof TStartNode) {
                            seq.newTids.add(((TStartNode) node).tidKid);
                        }

                        //DEBUG
                        if (node instanceof ISyncNode) {
                            seq.events.add(node);
                        } else {
                            seq.events.add(node);
                            if (node instanceof MemAccNode) {
                                lastMemAcc = (MemAccNode) node;
                            }
                            eCount++;
                        }
                    }
                    bnext = br.read();
                }
            } catch (IOException e) {
                e.printStackTrace();
                TEndNode node = new TEndNode(tid, tid, 0, 0);//failed
                seq.numOfEvents++;
                node.gid = (int) Bytes.longs.add(tid, seq.numOfEvents);
            }
            br.finish(fileInfo);
        } catch (Exception e) {
            LOG.error("error parsing trace " + tid, e);
            seq.events = null;
            return seq;
        }
        return seq;
    }

    private static TLHeader getHeader(final int typeIdx, ByteReader breader) throws IOException {
        if (typeIdx != 13) throw new RuntimeException("Could not read header");
//        const u64 version = UFO_VERSION;
//        const TidType tid;
//        const u64 timestamp;
//        const u32 length;
        long version = getLong64b(breader);
        short tidParent = getShort(breader);
        long time = getLong64b(breader);
        int len = getInt(breader);
        //LOG.debug(">>> UFO header version:{}  tid:{}  time:{}  len:{}", version , tidParent, new Date(time), len);
        return new TLHeader(tidParent, version, time, len);
    }

    private AbstractNode getNode(final short curTid, final int typeIdx, ByteReader breader, TLStat stat) throws IOException {
        short tidParent;
        short tidKid;
        long addr;
        long pc;
        int size;
        long time;
        int eTime;
        long len;
        long order;

        int type_idx__ = typeIdx & 0xffffff3f;
        switch (typeIdx) {
            case 0: // cBegin
                tidParent = getShort(breader);
                pc = getLong48b(breader);
                eTime = getInt(breader);
//                System.out.println("Begin " + tidParent + "  from " + _tidParent);
//                node = new TStartNode(gidGen++, _tidParent, pc_id, "" + tidParent, AbstractNode.TYPE.START);
                long tmp = getLong48b(breader);
                tmp = getInt(breader);
                tmp = getLong48b(breader);
                tmp = getInt(breader);
                order = getLong48b(breader);
                return new TBeginNode(curTid, tidParent, eTime, order);

            case 1: // cEnd
                tidParent = getShort(breader);
                eTime = getInt(breader);
                order = getLong48b(breader);

//                return new TJoinNode(_tidParent, pc_id, "" + tidParent, AbstractNode.TYPE.JOIN);
//                System.out.println("End " + tidParent + "  to " + _tidParent);
                return new TEndNode(curTid, tidParent, eTime, order);
            case 2: // thread start
                long index = getLong48b(breader);
                tidKid = getShort(breader);
                eTime = getInt(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

//                System.out.println("Start  " + _tidParent + "  ->  " + tidParent);
//                println(s"#$_tidParent ---> #$tidParent")
                stat.c_tstart++;
                return new TStartNode(index, curTid, tidKid, eTime, pc, order);
            case 3: // join
                index = getLong48b(breader);
                tidKid = getShort(breader);
                eTime = getInt(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

//                System.out.println("Join  " + tidParent + "  <-  " + _tidParent);
                stat.c_join++;
                return new TJoinNode(index, curTid, tidKid, eTime, pc, order);
//      * ThreadAcqLock,
//  * ThreadRelLock = 5,
//  * MemAlloc,
//  * MemDealloc,
//  * MemRead = 8,
//  * MemWrite,
//  * MemRangeRead = 10,
//  * MemRangeWrite
            case 4: // lock  8 + (13 + 48 -> 64) -> 72
                //long index = getLong48b(breader);
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

//                System.out.println("#" + _tid + " lock  " + addr);
                stat.c_lock++;
                //lastIdx = index;
//	public LockNode(short tid, long lockID, long pc, long idx) {
                return new LockNode(curTid, addr, pc, order);
            case 5: // nUnlock
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

//                System.out.println("#" + _tid + " nUnlock  " + addr);
                stat.c_unlock++;
                return new UnlockNode(curTid, addr, pc, order);
            case 6: // alloc
//        index = getLong48b(breader);
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);

//        System.out.println("allocate #" + _tid + " " + fsize + "  from " + addr);
                stat.c_alloc++;
                // lastIdx = index;
//                return new AllocNode(curTid, pc, addr, size, order);
                return null;
            case 7: // dealloc
//        index = getLong48b(breader);
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);
//        System.out.println("deallocate #" + _tid + "  from " + addr);
                stat.c_dealloc++;
                //lastIdx = index;
//                return new DeallocNode(curTid, pc, addr, size, order);
                return null;
            case 10: // range r
//        index = getLong48b(breader);
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);

                stat.c_range_r++;
                //lastIdx = index;
                return new RangeReadNode(curTid, pc, addr, size, order);
//                System.out.println("#" + _tid + " read range " + fsize + "  from " + addr);
            case 11: // range w
//        index = getLong48b(breader);
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);

                //lastIdx = index;
//                System.out.println("#" + _tid + " read write " + fsize + "  from " + addr);
                stat.c_range_w++;
                return new RangeWriteNode(curTid, pc, addr, size, order);
            case 12: // PtrAssignment
                long src = getLong48b(breader);
                long dest = getLong48b(breader);
                order = getLong48b(breader);

//        System.out.println(">>> prop " + Long.toHexString(dest) + "   <= " + Long.toHexString(src));
                //long idx = lastIdx;
                //lastIdx = 0;
//  public PtrPropNode(short tid, long src, long dest, long idx) {
                return new PtrPropNode(curTid, src, dest, 0, order);//JEFF idx
            case 14: // InfoPacket
//        const u64 timestamp;
//        const u64 length;
                time = getLong64b(breader);
                len = getLong64b(breader);
                //LOG.debug(">>> UFO packet:{}  time: {} len: {} ", new Date(time), len);
                return null;
            case 15:
                pc = getLong48b(breader);
                order = getLong48b(breader);
                FuncEntryNode funcEntryNode = new FuncEntryNode(curTid, pc, order);


                //JEFF
                //LOG.debug(funcEntryNode.toString());
                return funcEntryNode;
            case 16:
                order = getLong48b(breader);
                return new FuncExitNode(curTid, order);
            case 17: // ThrCondWait
                index = getLong48b(breader);
                long cond = getLong48b(breader);
                long mutex = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

                return new WaitNode(index, curTid, cond, mutex, pc, order);
            case 18: // ThrCondSignal
                index = getLong48b(breader);
                cond = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

                return new NotifyNode(index, curTid, cond, pc, order);
            case 19: // ThrCondBroadCast
                index = getLong48b(breader);
                cond = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);

                return new NotifyAllNode(index, curTid, cond, pc, order);
            case 20: // de-ref
                long ptrAddr = getLong48b(breader);
//        System.out.println(">>> deref " + Long.toHexString(ptrAddr));
                if (lastMemAcc != null) {
                    lastMemAcc.ptr = ptrAddr;
                }
                lastMemAcc = null;
                order = getLong48b(breader);
                return null;
            default: // 8 + (13 + 48 -> 64) -> 72 + header (1, 2, 4, 8)
                int type_idx = typeIdx & 0xffffff3f;
                if (type_idx <= 13) {
                    size = 1 << (typeIdx >> 6);
                    MemAccNode accN = null;
                    if (type_idx == 8) accN = getRWNode(size, false, curTid, breader, stat);
                    else if (type_idx == 9) accN = getRWNode(size, true, curTid, breader, stat);
                    //lastIdx = accN.idx;
                    return accN;
                }
                //JEFF may be the trace is corrupted
//      System.err.println("Unrecognized trace, type index " + typeIdx + " m " + type_idx);
                return null;
            //throw new IOException("Unrecognized trace, tid #"+curTid+" type index " + typeIdx + " m " + type_idx);
        }
    }

    private static MemAccNode getRWNode(int size, boolean isW, short curTid, ByteReader breader, TLStat stat) throws IOException {

        ByteBuffer valueBuf = ByteBuffer.wrap(new byte[8]).order(ByteOrder.LITTLE_ENDIAN);
//    long index = getLong48b(breader);
        long addr = getLong48b(breader);
        long pc = getLong48b(breader);
        long order = getLong48b(breader);

        int sz = 0;
        while (sz != size) {
            int v = breader.read();
            valueBuf.put((byte) v);
            sz++;
        }
        long[] st = stat.c_read;
        if (isW) st = stat.c_write;
        Number obj = null;
        switch (size) {
            case 1:
                st[0]++;
                obj = valueBuf.get(0);
                break;
            case 2:
                st[1]++;
                obj = valueBuf.getShort(0);
                break;
            case 4:
                st[2]++;
                obj = valueBuf.getInt(0);
                break;
            case 8:
                st[3]++;
                obj = valueBuf.getLong(0);
                break;
        }
        valueBuf.clear();
        if (isW) {
            return new WriteNode(curTid, pc, addr, (byte) size, obj.longValue(), order);
        } else { // type_idx 9
            return new ReadNode(curTid, pc, addr, (byte) size, obj.longValue(), order);
        }
    }

    public static short getShort(ByteReader breader) throws IOException {
        byte b1 = (byte) breader.read();
        byte b2 = (byte) breader.read();
        return Bytes.shorts.add(b2, b1);
    }

    public static int getInt(ByteReader breader) throws IOException {

        byte b1 = (byte) breader.read();
        byte b2 = (byte) breader.read();
        byte b3 = (byte) breader.read();
        byte b4 = (byte) breader.read();
        return Bytes.ints._Ladd(b4, b3, b2, b1);
//    ints.add(getShort(breader), getShort(breader))
    }


    public static long getLong48b(ByteReader breader) throws IOException {
        byte b0 = (byte) breader.read();
        byte b1 = (byte) breader.read();
        byte b2 = (byte) breader.read();
        byte b3 = (byte) breader.read();
        byte b4 = (byte) breader.read();
        byte b5 = (byte) breader.read();
        return Bytes.longs._Ladd((byte) 0x00, (byte) 0x00, b5, b4, b3, b2, b1, b0);
    }

    public static long getLong64b(ByteReader breader) throws IOException {
        byte b0 = (byte) breader.read();
        byte b1 = (byte) breader.read();
        byte b2 = (byte) breader.read();
        byte b3 = (byte) breader.read();
        byte b4 = (byte) breader.read();
        byte b5 = (byte) breader.read();
        byte b6 = (byte) breader.read();
        byte b7 = (byte) breader.read();
        return Bytes.longs._Ladd(b7, b6, b5, b4, b3, b2, b1, b0);
    }
}
