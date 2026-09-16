package com.jizhang.assistant.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 数据库结构。
 *
 * 设计要点：
 *  - txn 同时承载「支出/收入/退款/转账」多种角色，用 direction + original_txn_id
 *    + is_transfer 区分
 *  - 退款不改历史，只把原支出 status 置为已撤销并记 refunded_cents，保证账可回溯
 *  - raw_event 先落原文再解析，解析规则以后改进时可以对历史数据重跑
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "jizhang.db";

    /**
     * v1 初始版本
     * v2 增加内部转账字段（is_transfer / pair_txn_id）
     * v3 增加转账线索表（应对「提现已到账」这类不带金额的通知）
     * v4 增加通知来源表（诊断用：即使通知被过滤也能看到它来过、来自哪个包名）
     */
    public static final int DB_VERSION = 4;

    public DbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE txn ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "occurred_at INTEGER NOT NULL,"
                + "amount_cents INTEGER NOT NULL,"
                + "direction INTEGER NOT NULL,"
                + "merchant TEXT,"
                + "category TEXT,"
                + "account TEXT,"
                + "note TEXT,"
                + "source INTEGER NOT NULL DEFAULT 0,"
                + "dedupe_key TEXT,"
                + "status INTEGER NOT NULL DEFAULT 0,"
                + "refunded_cents INTEGER NOT NULL DEFAULT 0,"
                + "original_txn_id INTEGER NOT NULL DEFAULT 0,"
                + "raw_id INTEGER NOT NULL DEFAULT 0,"
                + "confidence INTEGER NOT NULL DEFAULT 100,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "auto_created INTEGER NOT NULL DEFAULT 0,"
                + "is_transfer INTEGER NOT NULL DEFAULT 0,"
                + "pair_txn_id INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE INDEX idx_txn_occurred ON txn(occurred_at)");
        db.execSQL("CREATE INDEX idx_txn_dir_occurred ON txn(direction, occurred_at)");
        db.execSQL("CREATE INDEX idx_txn_dedupe ON txn(dedupe_key)");
        db.execSQL("CREATE INDEX idx_txn_original ON txn(original_txn_id)");
        db.execSQL("CREATE INDEX idx_txn_pair ON txn(pair_txn_id)");

        db.execSQL("CREATE TABLE raw_event ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kind INTEGER NOT NULL,"
                + "pkg TEXT,"
                + "sender TEXT,"
                + "title TEXT,"
                + "body TEXT,"
                + "received_at INTEGER NOT NULL,"
                + "parsed INTEGER NOT NULL DEFAULT 0,"
                + "dedupe_key TEXT)");
        db.execSQL("CREATE INDEX idx_raw_dedupe ON raw_event(dedupe_key)");
        db.execSQL("CREATE INDEX idx_raw_received ON raw_event(received_at)");

        db.execSQL("CREATE TABLE refund_link ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "refund_txn_id INTEGER NOT NULL,"
                + "original_txn_id INTEGER NOT NULL,"
                + "amount_cents INTEGER NOT NULL,"
                + "mode INTEGER NOT NULL,"
                + "score INTEGER NOT NULL DEFAULT 0,"
                + "reason TEXT,"
                + "auto_removed INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_link_original ON refund_link(original_txn_id)");
        db.execSQL("CREATE INDEX idx_link_refund ON refund_link(refund_txn_id)");

        db.execSQL("CREATE TABLE setting ("
                + "k TEXT PRIMARY KEY,"
                + "v TEXT)");

        createHintTable(db);
        createSourceTable(db);

        seedCategories(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v2：增加内部转账支持。用 ALTER 加列而不是重建表，老数据不会丢。
        if (oldV < 2) {
            addColumnIfMissing(db, "txn", "is_transfer",
                    "ALTER TABLE txn ADD COLUMN is_transfer INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "txn", "pair_txn_id",
                    "ALTER TABLE txn ADD COLUMN pair_txn_id INTEGER NOT NULL DEFAULT 0");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_txn_pair ON txn(pair_txn_id)");

            // 复核：升级完必须真的拿到这两个列，否则明确记一条错误日志
            if (!hasColumn(db, "txn", "is_transfer")
                    || !hasColumn(db, "txn", "pair_txn_id")) {
                android.util.Log.e("JZDb",
                        "升级到 v2 失败：txn 表缺少 is_transfer / pair_txn_id 列。"
                                + "读取时会退化为「非转账」，但转账功能将不可用。");
            }
        }
        // v3：转账线索表
        if (oldV < 3) {
            createHintTable(db);
        }
        // v4：通知来源表
        if (oldV < 4) {
            createSourceTable(db);
        }
    }

    /**
     * 通知来源表。
     * 只记录「时间 + 包名 + 标题 + 是否被识别」，不记录通知正文，
     * 用于排查「通知到底有没有到达」这类问题。
     */
    private static void createSourceTable(SQLiteDatabase db) {
        safeExec(db, "CREATE TABLE IF NOT EXISTS notify_src ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "pkg TEXT,"
                + "title TEXT,"
                + "watched INTEGER NOT NULL DEFAULT 0)");
        safeExec(db, "CREATE INDEX IF NOT EXISTS idx_src_at ON notify_src(at)");
    }

    private static void createHintTable(SQLiteDatabase db) {
        safeExec(db, "CREATE TABLE IF NOT EXISTS transfer_hint ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "channel TEXT,"
                + "note TEXT,"
                + "consumed INTEGER NOT NULL DEFAULT 0)");
        safeExec(db, "CREATE INDEX IF NOT EXISTS idx_hint_at ON transfer_hint(at)");
    }

    /** SQLite 的 ALTER TABLE ADD COLUMN 没有 IF NOT EXISTS，只能先探测再执行 */
    private static void addColumnIfMissing(SQLiteDatabase db, String table,
                                           String column, String alterSql) {
        if (hasColumn(db, table, column)) return;
        safeExec(db, alterSql);
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        android.database.Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (c.moveToNext()) {
                if (column.equals(c.getString(c.getColumnIndexOrThrow("name")))) return true;
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    /**
     * 执行 DDL。
     *
     * 注意：这里以前是「静默吞掉异常」，那是个隐患 —— 万一 ALTER TABLE 没成功，
     * 数据库版本号照样会被 SQLiteOpenHelper 提升，导致新增的列永久缺失。
     * 现在改成失败要留日志，并在调用处复核结果。
     */
    private static void safeExec(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (Throwable e) {
            android.util.Log.e("JZDb", "DDL 执行失败: " + sql, e);
        }
    }

    private static void seedCategories(SQLiteDatabase db) {
        for (String c : new String[]{"餐饮", "交通", "购物", "居住", "通讯", "娱乐",
                "医疗", "教育", "人情", "旅行", "金融", "转账", "退款", "收入", "其他"}) {
            db.execSQL("INSERT INTO setting(k, v) VALUES(?, ?)",
                    new Object[]{"category:" + c, "1"});
        }
    }
}
