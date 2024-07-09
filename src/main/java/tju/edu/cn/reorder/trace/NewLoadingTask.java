package tju.edu.cn.reorder.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tju.edu.cn.reorder.misc.Addr2line;
import tju.edu.cn.reorder.misc.AddrInfo;
import tju.edu.cn.trace.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class NewLoadingTask{

    private static final Logger LOG = LoggerFactory.getLogger(NewLoadingTask.class);

    public final FileInfo fileInfo;

    public NewLoadingTask(FileInfo fi) {
        this.fileInfo = fi;
    }

    public TLEventSeq loadAllEvent(Addr2line addr2line) {
        final short tid = fileInfo.tid;
        TLEventSeq seq = new TLEventSeq(tid);
        if (seq.events == null){
            seq.events = new ArrayList<>();
        }
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
            try {
                while (bnext != -1) {
                    AbstractNode node = getAllNode(tid, bnext, br, TLEventSeq.stat, seq);
                    TLEventSeq.stat.c_total++;
                    seq.numOfEvents++;
                    if (node != null) {
                        ArrayList<AbstractNode> nodes = new ArrayList<>();
                        nodes.add(node);
                        if (addr2line.sourceInfo(nodes).values().iterator().hasNext()){
                            AddrInfo next = addr2line.sourceInfo(nodes).values().iterator().next();
                            if (next != null) node.setAi(String.valueOf(next));
                        }
                        node.gid = (int) Bytes.longs.add(tid, seq.numOfEvents);
                        LOG.info("Synchronize " + node);
                        seq.events.add(node);
                    }
                    bnext = br.read();
                }
            } catch (IOException e) {
                TEndNode node = new TEndNode(tid, tid, 0, 0);//failed
                seq.numOfEvents++;
                node.gid = (int) Bytes.longs.add(tid, seq.numOfEvents);
                seq.events.add(node);
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

    private AbstractNode getAllNode(final short curTid, final int typeIdx, ByteReader breader, TLStat stat, TLEventSeq seq) throws IOException {
        short tidParent;
        short tidKid;
        long addr;
        long pc;
        int size;
        long time;
        int eTime;
        long len;
        long order;
        int line;
        char[] file;
        int orderType;

        int type_idx__ = typeIdx & 0xffffff3f;

        switch (typeIdx) {
            case 0: // cBegin
                tidParent = getShort(breader);
                pc = getLong48b(breader);
                eTime = getInt(breader);
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
                return new TEndNode(curTid, tidParent, eTime, order);
            case 2: // thread start
                long index = getLong48b(breader);
                tidKid = getShort(breader);
                eTime = getInt(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_tstart++;
                seq.newTids.add(tidKid);
                return new TStartNode(index, curTid, tidKid, eTime, pc, order);
            case 3: // join
                index = getLong48b(breader);
                tidKid = getShort(breader);
                eTime = getInt(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_join++;
                return new TJoinNode(index, curTid, tidKid, eTime, pc, order);
            case 4: // lock  8 + (13 + 48 -> 64) -> 72
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_lock++;
                return new LockNode(curTid, addr, pc, order);
            case 5: // nUnlock
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_unlock++;
                return new UnlockNode(curTid, addr, pc, order);
            case 6: // alloc
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);
                stat.c_alloc++;
                // lastIdx = index;
                 return new AllocNode(curTid, pc, addr, size, order);
            case 7: // dealloc
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);
                stat.c_dealloc++;
                return new DeallocNode(curTid, pc, addr, size, order);
            case 10: // range r
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                stat.c_range_r++;
                order = getLong48b(breader);
                return new RangeReadNode(curTid, pc, addr, size, order);
            case 11: // range w
                addr = getLong48b(breader);
                pc = getLong48b(breader);
                size = getInt(breader);
                order = getLong48b(breader);
                stat.c_range_w++;
                return new RangeWriteNode(curTid, pc, addr, size, order);
            case 12: // PtrAssignment
                long src = getLong48b(breader);
                long dest = getLong48b(breader);
                order = getLong48b(breader);
                return new PtrPropNode(curTid, src, dest, 0, order);//JEFF idx
            case 14: // InfoPacket
                time = getLong64b(breader);
                len = getLong64b(breader);
                return null;
            case 15: //Func Entry
                pc = getLong48b(breader);
                order = getLong48b(breader);
                FuncEntryNode funcEntryNode = new FuncEntryNode(curTid, pc, order);;
                return funcEntryNode;
            case 16: //Func Exit
                order = getLong48b(breader);
                return new FuncExitNode(curTid, order);
            case 17: // ThrCondWait
                index = getLong48b(breader);
                long cond = getLong48b(breader);
                long mutex = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_wait++;
                return new WaitNode(index, curTid, cond, mutex, pc, order);
            case 18: // ThrCondSignal
                index = getLong48b(breader);
                cond = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_notify++;
                return new NotifyNode(index, curTid, cond, pc, order);
            case 19: // ThrCondBroadCast
                index = getLong48b(breader);
                cond = getLong48b(breader);
                pc = getLong48b(breader);
                order = getLong48b(breader);
                stat.c_notifyAll++;
                return new NotifyAllNode(index, curTid, cond, pc, order);
            case 20: // de-ref
                long ptrAddr = getLong48b(breader);
                order = getLong48b(breader);
                return null;
            case 21:
                pc = getLong48b(breader);
                orderType = getInt(breader);
                order = getLong48b(breader);
                line = getInt(breader);
                file = getChars10(breader);
                return new BarrierNode(curTid, pc, orderType, order, line, file);
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
                return null;
        }
    }


    private static MemAccNode getRWNode(int size, boolean isW, short curTid, ByteReader breader, TLStat stat) throws IOException {

        ByteBuffer valueBuf = ByteBuffer.wrap(new byte[8]).order(ByteOrder.LITTLE_ENDIAN);
        long addr = getLong48b(breader);
        long pc = getLong48b(breader);
        long order = getLong48b(breader);
        int line = getInt(breader);
        char[] file = getChars10(breader);

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
            return new WriteNode(curTid, pc, addr, (byte) size, obj.longValue(), order, line, file);
        } else { // type_idx 9
            return new ReadNode(curTid, pc, addr, (byte) size, obj.longValue(), order, line, file);
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

    public static char[] getChars10(ByteReader breader) throws IOException {
        char[] chars = new char[10];
        for (int i = 0; i < 10; i++) {
            chars[i] = (char) breader.read();
        }
        return chars;
    }
}
