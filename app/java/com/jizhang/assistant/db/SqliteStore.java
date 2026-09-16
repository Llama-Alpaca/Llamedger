package com.jizhang.assistant.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.jizhang.assistant.core.engine.TxnStore;
import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.model.RefundLink;
import com.jizhang.assistant.core.model.Txn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TxnStore 的 SQLite 实现，同时提供界面需要的查询。 */
public class SqliteStore implements TxnStore {

    private final DbHelper helper;

    public SqliteStore(Context ctx) {
        this.helper = new DbHelper(ctx.getApplicationContext());
    }

    private SQLiteDatabase w() {
        return helper.getWritableDatabase();
    }

    private SQLiteDatabase r() {
        return helper.getReadableDatabase();
    }

    // ------------------------------------------------------------ 流水

    @Override
    public synchronized long insertTxn(Txn t) {
        ContentValues v = toValues(t);
        long id = w().insert("txn", null, v);
        t.id = id;
        return id;
    }

    @Override
    public synchronized void updateTxn(Txn t) {
        w().update("txn", toValues(t), "id=?", new String[]{String.valueOf(t.id)});
    }

    @Override
    public Txn findTxnById(long id) {
        Cursor c = r().rawQuery("SELECT * FROM txn WHERE id=?", new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally {
            c.close();
        }
    }

    public synchronized void deleteTxn(long id) {
        w().delete("txn", "id=?", new String[]{String.valueOf(id)});
    }

    @Override
    public List<Txn> findRefundCandidates(long beforeTime, long afterTime, int limit) {
        Cursor c = r().rawQuery(
                "SELECT * FROM txn WHERE direction=? AND occurred_at<=? AND occurred_at>=?"
                        + " ORDER BY occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(Txn.DIR_OUT), String.valueOf(beforeTime),
                        String.valueOf(afterTime), String.valueOf(limit)});
        return readAll(c);
    }

    @Override
    public List<Txn> findRecentByDirection(int direction, long fromTime, long toTime, int limit) {
        Cursor c = r().rawQuery(
                "SELECT * FROM txn WHERE direction=? AND occurred_at>=? AND occurred_at<=?"
                        + " ORDER BY occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(direction), String.valueOf(fromTime),
                        String.valueOf(toTime), String.valueOf(limit)});
        return readAll(c);
    }

    @Override
    public List<Txn> recentTxns(int limit) {
        Cursor c = r().rawQuery("SELECT * FROM txn ORDER BY occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        return readAll(c);
    }

    /** 条件查询（界面列表用） */
    public List<Txn> query(TxnFilter f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM txn WHERE 1=1");
        List<String> args = new ArrayList<String>();
        if (f.from > 0) { sql.append(" AND occurred_at>=?"); args.add(String.valueOf(f.from)); }
        if (f.to > 0) { sql.append(" AND occurred_at<=?"); args.add(String.valueOf(f.to)); }
        if (f.direction >= 0) { sql.append(" AND direction=?"); args.add(String.valueOf(f.direction)); }
        if (f.category != null && f.category.length() > 0) {
            sql.append(" AND category=?"); args.add(f.category);
        }
        if (f.account != null && f.account.length() > 0) {
            sql.append(" AND account=?"); args.add(f.account);
        }
        if (f.keyword != null && f.keyword.length() > 0) {
            sql.append(" AND (merchant LIKE ? OR note LIKE ? OR category LIKE ?)");
            String like = "%" + f.keyword + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (!f.includeVoided) sql.append(" AND status!=1");
        sql.append(" ORDER BY occurred_at DESC LIMIT ?");
        args.add(String.valueOf(f.limit <= 0 ? 500 : f.limit));
        Cursor c = r().rawQuery(sql.toString(), args.toArray(new String[0]));
        return readAll(c);
    }

    private List<Txn> readAll(Cursor c) {
        List<Txn> out = new ArrayList<Txn>();
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------ 原始事件

    @Override
    public synchronized long insertRawEvent(RawEvent e) {
        ContentValues v = new ContentValues();
        v.put("kind", e.kind);
        v.put("pkg", e.pkg);
        v.put("sender", e.sender);
        v.put("title", e.title);
        v.put("body", e.body);
        v.put("received_at", e.receivedAt);
        v.put("parsed", e.parsed);
        v.put("dedupe_key", e.dedupeKey);
        long id = w().insert("raw_event", null, v);
        e.id = id;
        return id;
    }

    @Override
    public synchronized void updateRawEvent(RawEvent e) {
        ContentValues v = new ContentValues();
        v.put("parsed", e.parsed);
        w().update("raw_event", v, "id=?", new String[]{String.valueOf(e.id)});
    }

    @Override
    public boolean rawEventExists(String dedupeKey) {
        if (dedupeKey == null) return false;
        Cursor c = r().rawQuery("SELECT 1 FROM raw_event WHERE dedupe_key=? LIMIT 1",
                new String[]{dedupeKey});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    // ------------------------------------------------------------ 退款关联

    @Override
    public synchronized long insertRefundLink(RefundLink l) {
        ContentValues v = new ContentValues();
        v.put("refund_txn_id", l.refundTxnId);
        v.put("original_txn_id", l.originalTxnId);
        v.put("amount_cents", l.amountCents);
        v.put("mode", l.mode);
        v.put("score", l.score);
        v.put("reason", l.reason);
        v.put("auto_removed", l.autoRemoved ? 1 : 0);
        v.put("created_at", l.createdAt);
        long id = w().insert("refund_link", null, v);
        l.id = id;
        return id;
    }

    @Override
    public List<RefundLink> refundLinksForOriginal(long originalId) {
        Cursor c = r().rawQuery("SELECT * FROM refund_link WHERE original_txn_id=? ORDER BY created_at DESC",
                new String[]{String.valueOf(originalId)});
        List<RefundLink> out = new ArrayList<RefundLink>();
        try {
            while (c.moveToNext()) out.add(linkFromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<RefundLink> recentRefundLinks(int limit) {
        Cursor c = r().rawQuery("SELECT * FROM refund_link ORDER BY created_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        List<RefundLink> out = new ArrayList<RefundLink>();
        try {
            while (c.moveToNext()) out.add(linkFromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public synchronized void deleteRefundLinkByRefundTxn(long refundTxnId) {
        w().delete("refund_link", "refund_txn_id=?", new String[]{String.valueOf(refundTxnId)});
    }

    // ------------------------------------------------------------ 统计

    /** 某区间内的收入/支出合计（已扣除退款冲减，退款关联记录不计入） */
    public long[] totals(long from, long to) {
        long out = 0, in = 0;
        Cursor c = r().rawQuery(
                "SELECT direction, amount_cents, refunded_cents, status, original_txn_id,"
                        + " is_transfer FROM txn WHERE occurred_at>=? AND occurred_at<=?",
                new String[]{String.valueOf(from), String.valueOf(to)});
        try {
            while (c.moveToNext()) {
                int dir = c.getInt(0);
                long amt = c.getLong(1);
                long refunded = c.getLong(2);
                int status = c.getInt(3);
                long origId = c.getLong(4);
                boolean transfer = c.getInt(5) == 1;
                if (origId != 0) continue;              // 退款冲抵记录不计入收支
                if (status == Txn.STATUS_VOIDED) continue;  // 已撤销支出不计入
                if (transfer) continue;                 // 账户间转账不计入收支
                if (dir == Txn.DIR_OUT) {
                    out += Math.max(0, amt - refunded);
                } else {
                    in += amt;
                }
            }
        } finally {
            c.close();
        }
        return new long[]{in, out};
    }

    /** 分类汇总，key=分类名，value=金额(分) */
    public Map<String, Long> categoryTotals(long from, long to, int direction) {
        Map<String, Long> map = new LinkedHashMap<String, Long>();
        Cursor c = r().rawQuery(
                "SELECT category, amount_cents, refunded_cents, status, original_txn_id,"
                        + " is_transfer FROM txn"
                        + " WHERE occurred_at>=? AND occurred_at<=? AND direction=?",
                new String[]{String.valueOf(from), String.valueOf(to),
                        String.valueOf(direction)});
        try {
            while (c.moveToNext()) {
                String cat = c.getString(0);
                long amt = c.getLong(1);
                long refunded = c.getLong(2);
                int status = c.getInt(3);
                long origId = c.getLong(4);
                boolean transfer = c.getInt(5) == 1;
                if (origId != 0 || status == Txn.STATUS_VOIDED || transfer) continue;
                if (cat == null || cat.length() == 0) cat = "其他";
                long v = direction == Txn.DIR_OUT ? Math.max(0, amt - refunded) : amt;
                Long old = map.get(cat);
                map.put(cat, (old == null ? 0 : old) + v);
            }
        } finally {
            c.close();
        }
        return map;
    }

    public int countTxns() {
        Cursor c = r().rawQuery("SELECT COUNT(*) FROM txn", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    // ------------------------------------------------------------ 配置

    @Override
    public String getSetting(String key, String def) {
        Cursor c = r().rawQuery("SELECT v FROM setting WHERE k=?", new String[]{key});
        try {
            if (c.moveToFirst()) {
                String v = c.getString(0);
                return v == null ? def : v;
            }
            return def;
        } finally {
            c.close();
        }
    }

    @Override
    public synchronized void setSetting(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("k", key);
        v.put("v", value);
        w().insertWithOnConflict("setting", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------------- 内部转账线索 ----------------

    @Override
    public synchronized long insertTransferHint(long at, String channel, String note) {
        try {
            ContentValues v = new ContentValues();
            v.put("at", at);
            v.put("channel", channel);
            v.put("note", note);
            v.put("consumed", 0);
            return w().insert("transfer_hint", null, v);
        } catch (Throwable e) {
            android.util.Log.w("JZStore", "转账线索表不可用，已忽略本次线索", e);
            return 0;
        }
    }

    @Override
    public long findTransferHint(long centerAt, long windowMs) {
        Cursor c;
        try {
            c = r().rawQuery(
                "SELECT id, at FROM transfer_hint WHERE consumed=0 AND at>=? AND at<=?"
                        + " ORDER BY at DESC LIMIT 20",
                new String[]{String.valueOf(centerAt - windowMs),
                        String.valueOf(centerAt + windowMs)});
        } catch (Throwable e) {
            return 0;   // 表不存在就当作没有线索
        }
        long bestId = 0;
        long bestDt = Long.MAX_VALUE;
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long at = c.getLong(1);
                long dt = Math.abs(at - centerAt);
                if (dt < bestDt) {
                    bestDt = dt;
                    bestId = id;
                }
            }
        } finally {
            c.close();
        }
        return bestId;
    }

    @Override
    public synchronized void consumeTransferHint(long id) {
        try {
            ContentValues v = new ContentValues();
            v.put("consumed", 1);
            w().update("transfer_hint", v, "id=?", new String[]{String.valueOf(id)});
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 通知来源（诊断用）----------------

    private static final int SOURCE_KEEP = 300;

    /** 记录一条通知来源，只存包名与标题，并自动清理旧记录 */
    public synchronized void recordNotifySource(long at, String pkg, String title, boolean watched) {
        try {
            ContentValues v = new ContentValues();
            v.put("at", at);
            v.put("pkg", pkg);
            v.put("title", title);
            v.put("watched", watched ? 1 : 0);
            SQLiteDatabase db = w();
            db.insert("notify_src", null, v);
            // 只保留最近若干条，避免无限增长
            db.execSQL("DELETE FROM notify_src WHERE id NOT IN "
                    + "(SELECT id FROM notify_src ORDER BY id DESC LIMIT " + SOURCE_KEEP + ")");
        } catch (Throwable e) {
            android.util.Log.w("JZStore", "记录通知来源失败", e);
        }
    }

    /** 最近的通知来源（时间倒序）*/
    public java.util.List<Object[]> recentNotifySources(int limit) {
        java.util.List<Object[]> out = new java.util.ArrayList<Object[]>();
        Cursor c;
        try {
            c = r().rawQuery("SELECT at, pkg, title, watched FROM notify_src "
                    + "ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit)});
        } catch (Throwable e) {
            return out;
        }
        try {
            while (c.moveToNext()) {
                out.add(new Object[]{c.getLong(0), c.getString(1),
                        c.getString(2), c.getInt(3) == 1});
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** 清空通知来源记录 */
    public synchronized void clearNotifySources() {
        try {
            w().delete("notify_src", null, null);
        } catch (Throwable ignored) {
        }
    }

    public boolean getBool(String key, boolean def) {
        return "1".equals(getSetting(key, def ? "1" : "0"));
    }

    public void setBool(String key, boolean value) {
        setSetting(key, value ? "1" : "0");
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(getSetting(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    /** 清空账本数据（保留设置与分类） */
    public synchronized void clearAll() {
        SQLiteDatabase db = w();
        db.delete("txn", null, null);
        db.delete("refund_link", null, null);
        db.delete("raw_event", null, null);
        try {
            db.delete("notify_src", null, null);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ 分类

    public List<String> categories() {
        List<String> out = new ArrayList<String>();
        Cursor c = r().rawQuery("SELECT k FROM setting WHERE k LIKE 'category:%' ORDER BY v, k", null);
        try {
            while (c.moveToNext()) {
                String k = c.getString(0);
                if (k != null && k.startsWith("category:")) out.add(k.substring(9));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void addCategory(String name) {
        if (name == null || name.trim().length() == 0) return;
        setSetting("category:" + name.trim(), "1");
    }

    public void removeCategory(String name) {
        w().delete("setting", "k=?", new String[]{"category:" + name});
    }

    public List<String> accounts() {
        List<String> out = new ArrayList<String>();
        Cursor c = r().rawQuery(
                "SELECT DISTINCT account FROM txn WHERE account IS NOT NULL AND account!=''", null);
        try {
            while (c.moveToNext()) {
                String a = c.getString(0);
                if (a != null && a.length() > 0) out.add(a);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------ 映射

    private ContentValues toValues(Txn t) {
        ContentValues v = new ContentValues();
        v.put("occurred_at", t.occurredAt);
        v.put("amount_cents", t.amountCents);
        v.put("direction", t.direction);
        v.put("merchant", t.merchant);
        v.put("category", t.category);
        v.put("account", t.account);
        v.put("note", t.note);
        v.put("source", t.source);
        v.put("dedupe_key", t.dedupeKey);
        v.put("status", t.status);
        v.put("refunded_cents", t.refundedCents);
        v.put("original_txn_id", t.originalTxnId);
        v.put("raw_id", t.rawId);
        v.put("confidence", t.confidence);
        v.put("created_at", t.createdAt);
        v.put("updated_at", t.updatedAt);
        v.put("auto_created", t.autoCreated ? 1 : 0);
        v.put("is_transfer", t.isTransfer ? 1 : 0);
        v.put("pair_txn_id", t.pairTxnId);
        return v;
    }

    private Txn fromCursor(Cursor c) {
        Txn t = new Txn();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.occurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at"));
        t.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        t.direction = c.getInt(c.getColumnIndexOrThrow("direction"));
        t.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
        t.category = c.getString(c.getColumnIndexOrThrow("category"));
        t.account = c.getString(c.getColumnIndexOrThrow("account"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        t.source = c.getInt(c.getColumnIndexOrThrow("source"));
        t.dedupeKey = c.getString(c.getColumnIndexOrThrow("dedupe_key"));
        t.status = c.getInt(c.getColumnIndexOrThrow("status"));
        t.refundedCents = c.getLong(c.getColumnIndexOrThrow("refunded_cents"));
        t.originalTxnId = c.getLong(c.getColumnIndexOrThrow("original_txn_id"));
        t.rawId = c.getLong(c.getColumnIndexOrThrow("raw_id"));
        t.confidence = c.getInt(c.getColumnIndexOrThrow("confidence"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        t.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        t.autoCreated = c.getInt(c.getColumnIndexOrThrow("auto_created")) == 1;
        // 这两个列是 v2 才加的，用容错读取：万一升级语句没跑成功，
        // 也只是「转账标记丢失」，绝不能因为一列读不到就让整个列表崩掉。
        t.isTransfer = colInt(c, "is_transfer", 0) == 1;
        t.pairTxnId = colLong(c, "pair_txn_id", 0);
        return t;
    }

    /** 容错读整数：列不存在时返回默认值，而不是抛异常 */
    private static int colInt(Cursor c, String name, int def) {
        int i = c.getColumnIndex(name);
        return i < 0 ? def : c.getInt(i);
    }

    /** 容错读长整数 */
    private static long colLong(Cursor c, String name, long def) {
        int i = c.getColumnIndex(name);
        return i < 0 ? def : c.getLong(i);
    }

    private RefundLink linkFromCursor(Cursor c) {
        RefundLink l = new RefundLink();
        l.id = c.getLong(c.getColumnIndexOrThrow("id"));
        l.refundTxnId = c.getLong(c.getColumnIndexOrThrow("refund_txn_id"));
        l.originalTxnId = c.getLong(c.getColumnIndexOrThrow("original_txn_id"));
        l.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        l.mode = c.getInt(c.getColumnIndexOrThrow("mode"));
        l.score = c.getInt(c.getColumnIndexOrThrow("score"));
        l.reason = c.getString(c.getColumnIndexOrThrow("reason"));
        l.autoRemoved = c.getInt(c.getColumnIndexOrThrow("auto_removed")) == 1;
        l.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return l;
    }

    /** 查询条件 */
    public static class TxnFilter {
        public long from;
        public long to;
        public int direction = -1;
        public String category;
        public String account;
        public String keyword;
        public boolean includeVoided = false;
        public int limit = 500;
    }
}
