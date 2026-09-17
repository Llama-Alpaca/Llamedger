package com.jizhang.assistant.util;

import com.jizhang.assistant.db.SqliteStore;

/** 配置项读写（存 SQLite setting 表）。 */
public final class Prefs {

    private Prefs() {}

    /** 自动记账总开关 */
    public static final String MONITOR_ENABLED = "monitor_enabled";
    /** 抓到退款时是否自动移除原支出 */
    public static final String AUTO_REMOVE_REFUND = "auto_remove_refund";
    /** 回溯多少天找原支出 */
    public static final String REFUND_WINDOW_DAYS = "refund_window_days";
    /** 匹配阈值 */
    public static final String REFUND_MIN_SCORE = "refund_min_score";
    /** 是否允许部分退款冲减 */
    public static final String ALLOW_PARTIAL = "allow_partial";
    /** 记账后是否弹通知 */
    public static final String NOTIFY_ON_RECORD = "notify_on_record";
    /** 开机自启 */
    public static final String AUTO_START = "auto_start";
    /** 通知使用权引导是否已完成 */
    public static final String GUIDE_DONE = "guide_done";

    public static boolean monitorEnabled(SqliteStore s) {
        return s.getBool(MONITOR_ENABLED, true);
    }

    public static void setMonitorEnabled(SqliteStore s, boolean v) {
        s.setBool(MONITOR_ENABLED, v);
    }

    public static boolean autoRemoveRefund(SqliteStore s) {
        return s.getBool(AUTO_REMOVE_REFUND, true);
    }

    public static void setAutoRemoveRefund(SqliteStore s, boolean v) {
        s.setBool(AUTO_REMOVE_REFUND, v);
    }

    public static int refundWindowDays(SqliteStore s) {
        return s.getInt(REFUND_WINDOW_DAYS, 90);
    }

    public static void setRefundWindowDays(SqliteStore s, int v) {
        s.setSetting(REFUND_WINDOW_DAYS, String.valueOf(v));
    }

    public static int refundMinScore(SqliteStore s) {
        return s.getInt(REFUND_MIN_SCORE, 55);
    }

    public static void setRefundMinScore(SqliteStore s, int v) {
        s.setSetting(REFUND_MIN_SCORE, String.valueOf(v));
    }

    public static boolean allowPartial(SqliteStore s) {
        return s.getBool(ALLOW_PARTIAL, true);
    }

    public static void setAllowPartial(SqliteStore s, boolean v) {
        s.setBool(ALLOW_PARTIAL, v);
    }

    public static boolean notifyOnRecord(SqliteStore s) {
        return s.getBool(NOTIFY_ON_RECORD, true);
    }

    public static void setNotifyOnRecord(SqliteStore s, boolean v) {
        s.setBool(NOTIFY_ON_RECORD, v);
    }

    public static boolean autoStart(SqliteStore s) {
        return s.getBool(AUTO_START, true);
    }

    public static void setAutoStart(SqliteStore s, boolean v) {
        s.setBool(AUTO_START, v);
    }
}
