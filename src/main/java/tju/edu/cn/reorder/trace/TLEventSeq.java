package tju.edu.cn.reorder.trace;


import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import tju.edu.cn.trace.AbstractNode;

import java.util.ArrayList;

public class TLEventSeq {

    public final short tid;
    public final ShortOpenHashSet newTids = new ShortOpenHashSet(40);

    public TLHeader header;
    public ArrayList<AbstractNode> events;
    public final static TLStat stat = new TLStat();
    public int numOfEvents;

    public TLEventSeq(short tid) {
        this.tid = tid;
    }
}
