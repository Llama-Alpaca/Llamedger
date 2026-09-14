package com.jizhang.assistant.capture;

import android.content.Context;
import android.util.Log;

import com.jizhang.assistant.App;
import com.jizhang.assistant.core.engine.IngestEngine;
import com.jizhang.assistant.core.engine.TransferEngine;
import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 采集总入口：把一条原始事件（通知/短信）走完「落原文 → 解析 → 去重 → 入账 → 退款冲销」。
 */
public final class Monitor {

    private Monitor() {}

    private static final String TAG = "JZMonitor";

    /** 单线程串行处理，保证同一笔不会被并发重复入账 */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    public static void handleAsync(final Context ctx, final RawEvent e) {
        EXEC.execute(new Runnable() {
            public void run() {
                try {
                    handle(ctx, e);
                } catch (Throwable t) {
                    Log.e(TAG, "处理失败", t);
                }
            }
        });
    }

    public static IngestEngine.Result handle(Context ctx, RawEvent e) {
        SqliteStore store = App.db();
        if (store == null) return null;

        // 总开关
        if (!Prefs.monitorEnabled(store)) {
            Log.i(TAG, "自动记账已关闭，忽略本次事件");
            return null;
        }

        // 同一通知可能被系统重复回调，用 key+时间 做幂等
        if (e.dedupeKey != null && store.rawEventExists(e.dedupeKey)) {
            return null;
        }
        long rawId = store.insertRawEvent(e);
        e.id = rawId;

        IngestEngine.Result res;
        try {
            res = IngestEngine.ingest(e, store, App.refundPolicy());
        } catch (Throwable t) {
            Log.e(TAG, "入账异常", t);
            e.parsed = 2;
            store.updateRawEvent(e);
            return null;
        }

        if (res != null && res.parsed) {
            e.parsed = 1;
        } else if (TransferEngine.noteHintIfInternalTransfer(e, res == null ? null : res.parse, store)) {
            // 微信/支付宝只说「零钱提现已到账」却不给金额：
            // 记一条线索，等银行那侧「收款」到达时据此判定为转账。
            e.parsed = 3;
            Log.i(TAG, "识别为无金额的转账通知，已记下线索");
        } else {
            e.parsed = 2;
        }
        store.updateRawEvent(e);

        if (res != null && res.parsed && Prefs.notifyOnRecord(store)) {
            String title;
            if (res.refundMatched) {
                title = res.originalRemoved ? "已撤销一笔支出" : "已冲减一笔支出";
            } else if (res.duplicate) {
                title = "已合并重复记录";
            } else {
                title = res.txn != null && res.txn.direction == Txn.DIR_IN ? "记了一笔收入" : "记了一笔支出";
            }
            Notifier.record(ctx, title, res.message);
        }
        Log.i(TAG, "处理完成: " + res);
        return res;
    }
}
