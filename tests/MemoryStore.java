import com.jizhang.assistant.core.engine.TxnStore;
import com.jizhang.assistant.core.model.*;

import java.util.*;

/** 内存实现，供电脑端测试使用 */
public class MemoryStore implements TxnStore {

    public final List<Txn> txns = new ArrayList<Txn>();
    public final List<RefundLink> links = new ArrayList<RefundLink>();
    public final List<RawEvent> raws = new ArrayList<RawEvent>();
    public final Map<String, String> settings = new HashMap<String, String>();
    long seq = 1;

    public long insertTxn(Txn t) {
        if (t.id == 0) t.id = seq++;
        txns.add(t);
        return t.id;
    }

    public void updateTxn(Txn t) {
        for (int i = 0; i < txns.size(); i++) {
            if (txns.get(i).id == t.id) { txns.set(i, t); return; }
        }
    }

    public Txn findTxnById(long id) {
        for (Txn t : txns) if (t.id == id) return t;
        return null;
    }

    public List<Txn> findRefundCandidates(long beforeTime, long afterTime, int limit) {
        List<Txn> out = new ArrayList<Txn>();
        for (Txn t : txns) {
            if (t.direction != Txn.DIR_OUT) continue;
            if (t.occurredAt > beforeTime || t.occurredAt < afterTime) continue;
            out.add(t);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public List<Txn> findRecentByDirection(int direction, long fromTime, long toTime, int limit) {
        List<Txn> out = new ArrayList<Txn>();
        for (Txn t : txns) {
            if (t.direction != direction) continue;
            if (t.occurredAt < fromTime || t.occurredAt > toTime) continue;
            out.add(t);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public long insertRefundLink(RefundLink l) {
        if (l.id == 0) l.id = seq++;
        links.add(l);
        return l.id;
    }

    public List<RefundLink> refundLinksForOriginal(long originalId) {
        List<RefundLink> out = new ArrayList<RefundLink>();
        for (RefundLink l : links) if (l.originalTxnId == originalId) out.add(l);
        return out;
    }

    public void deleteRefundLinkByRefundTxn(long refundTxnId) {
        Iterator<RefundLink> it = links.iterator();
        while (it.hasNext()) if (it.next().refundTxnId == refundTxnId) it.remove();
    }

    public long insertRawEvent(RawEvent e) {
        if (e.id == 0) e.id = seq++;
        raws.add(e);
        return e.id;
    }

    public void updateRawEvent(RawEvent e) { }

    public boolean rawEventExists(String dedupeKey) {
        for (RawEvent r : raws) if (r.dedupeKey != null && r.dedupeKey.equals(dedupeKey)) return true;
        return false;
    }

    public List<Txn> recentTxns(int limit) {
        List<Txn> out = new ArrayList<Txn>(txns);
        Collections.sort(out, new Comparator<Txn>() {
            public int compare(Txn a, Txn b) { return Long.compare(b.occurredAt, a.occurredAt); }
        });
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    public String getSetting(String key, String def) {
        String v = settings.get(key);
        return v == null ? def : v;
    }

    public void setSetting(String key, String value) { settings.put(key, value); }

    // ---------------- 转账线索（内存版） ----------------
    public final List<long[]> hints = new ArrayList<long[]>();
    // 每条: {id, at, consumed}

    public long insertTransferHint(long at, String channel, String note) {
        long id = seq++;
        hints.add(new long[]{id, at, 0});
        return id;
    }

    public long findTransferHint(long centerAt, long windowMs) {
        long bestId = 0;
        long bestDt = Long.MAX_VALUE;
        for (long[] h : hints) {
            if (h[2] != 0) continue;
            long dt = Math.abs(h[1] - centerAt);
            if (dt > windowMs) continue;
            if (dt < bestDt) { bestDt = dt; bestId = h[0]; }
        }
        return bestId;
    }

    public void consumeTransferHint(long id) {
        for (long[] h : hints) if (h[0] == id) h[2] = 1;
    }
}
