package com.jizhang.assistant;

import android.app.Application;

import com.jizhang.assistant.core.engine.RefundEngine;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Prefs;

/** 应用入口，持有全局唯一的存储实例。 */
public class App extends Application {

    private static App inst;
    private SqliteStore store;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        store = new SqliteStore(this);
    }

    public static App get() {
        return inst;
    }

    public static SqliteStore db() {
        return inst.store;
    }

    /** 按当前设置构造退款处理策略 */
    public static RefundEngine.Policy refundPolicy() {
        SqliteStore s = inst != null ? inst.store : null;
        RefundEngine.Policy p = new RefundEngine.Policy();
        if (s == null) return p;
        p.autoRemove = Prefs.autoRemoveRefund(s);
        p.windowDays = Prefs.refundWindowDays(s);
        p.minScore = Prefs.refundMinScore(s);
        p.allowPartial = Prefs.allowPartial(s);
        return p;
    }
}
