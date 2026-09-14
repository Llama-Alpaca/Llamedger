package com.jizhang.assistant.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import com.jizhang.assistant.core.engine.RefundEngine;
import com.jizhang.assistant.core.engine.TransferEngine;
import com.jizhang.assistant.core.model.RefundLink;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水的操作菜单（编辑 / 删除 / 退款冲销 / 转账标记）。
 * 抽成公共组件，首页和流水页都能用同一套逻辑。
 */
public final class TxnActions {

    private TxnActions() {}

    public interface Callback {
        void onChanged();
    }

    /** 弹出操作菜单 */
    public static void show(final Activity act, final SqliteStore store,
                            final Txn t, final Callback cb) {
        if (act == null || t == null) return;

        final List<String> actions = new ArrayList<String>();

        if (t.originalTxnId != 0) {
            actions.add("撤销这次退款冲销");
        } else if (t.status == Txn.STATUS_VOIDED) {
            actions.add("恢复这条支出记录");
        }
        if (t.status == Txn.STATUS_PENDING) {
            actions.add("确认退款并移除本条支出");
        }
        if (t.isTransfer) {
            actions.add(t.pairTxnId != 0 ? "取消转账配对" : "取消转账标记");
        } else if (t.originalTxnId == 0) {
            actions.add("标记为转账（不计入收支）");
        }
        actions.add("编辑");
        actions.add("删除");

        String title = Fmt.money(t.amountCents)
                + (t.merchant != null && t.merchant.length() > 0 ? "  " + t.merchant : "");

        new AlertDialog.Builder(act)
                .setTitle(title)
                .setItems(actions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        handle(act, store, t, actions.get(which), cb);
                    }
                })
                .show();
    }

    private static void handle(Activity act, SqliteStore store, Txn t,
                               String action, Callback cb) {
        if ("编辑".equals(action)) {
            Intent i = new Intent(act, AddTxnActivity.class);
            i.putExtra("txn_id", t.id);
            act.startActivity(i);
            return;
        }
        if ("删除".equals(action)) {
            confirmDelete(act, store, t, cb);
            return;
        }
        if ("撤销这次退款冲销".equals(action)) {
            List<RefundLink> links = store.refundLinksForOriginal(t.originalTxnId);
            RefundLink target = null;
            for (RefundLink l : links) {
                if (l.refundTxnId == t.id) { target = l; break; }
            }
            if (target == null && !links.isEmpty()) target = links.get(0);
            if (target == null) {
                Ui.toast(act, "找不到冲销记录");
                return;
            }
            RefundEngine.undo(target, store);
            Ui.toast(act, "已恢复原支出记录");
            if (cb != null) cb.onChanged();
            return;
        }
        if ("恢复这条支出记录".equals(action)) {
            List<RefundLink> links = store.refundLinksForOriginal(t.id);
            for (RefundLink l : links) RefundEngine.undo(l, store);
            t.status = Txn.STATUS_ACTIVE;
            t.refundedCents = 0;
            t.updatedAt = System.currentTimeMillis();
            store.updateTxn(t);
            Ui.toast(act, "已恢复");
            if (cb != null) cb.onChanged();
            return;
        }
        if ("确认退款并移除本条支出".equals(action)) {
            List<RefundLink> links = store.refundLinksForOriginal(t.id);
            for (RefundLink l : links) {
                l.autoRemoved = true;
                Txn refund = store.findTxnById(l.refundTxnId);
                if (refund != null) {
                    refund.category = "退款冲抵";
                    store.updateTxn(refund);
                }
            }
            t.refundedCents = t.amountCents;
            t.status = Txn.STATUS_VOIDED;
            t.updatedAt = System.currentTimeMillis();
            store.updateTxn(t);
            Ui.toast(act, "已确认移除");
            if (cb != null) cb.onChanged();
            return;
        }
        if ("标记为转账（不计入收支）".equals(action)) {
            t.isTransfer = true;
            t.category = "转账";
            t.updatedAt = System.currentTimeMillis();
            store.updateTxn(t);
            TransferEngine.tryPair(t, store);
            Ui.toast(act, "已标记为转账，不再计入收支");
            if (cb != null) cb.onChanged();
            return;
        }
        if ("取消转账配对".equals(action)) {
            TransferEngine.unpair(t, store);
            Ui.toast(act, "已取消配对");
            if (cb != null) cb.onChanged();
            return;
        }
        if ("取消转账标记".equals(action)) {
            t.isTransfer = false;
            if ("转账".equals(t.category)) t.category = "其他";
            t.updatedAt = System.currentTimeMillis();
            store.updateTxn(t);
            Ui.toast(act, "已取消转账标记");
            if (cb != null) cb.onChanged();
        }
    }

    /** 删除前确认 */
    public static void confirmDelete(final Activity act, final SqliteStore store,
                                     final Txn t, final Callback cb) {
        if (act == null || t == null) return;
        StringBuilder msg = new StringBuilder(Fmt.money(t.amountCents));
        if (t.merchant != null && t.merchant.length() > 0) msg.append("  ").append(t.merchant);
        msg.append("\n").append(Fmt.dateTime(t.occurredAt));

        new AlertDialog.Builder(act)
                .setTitle("删除这条记录？")
                .setMessage(msg.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        // 若是转账且已配对，解除配对，避免另一条挂着失效的关联
                        if (t.pairTxnId != 0) TransferEngine.unpair(t, store);
                        store.deleteTxn(t.id);
                        Ui.toast(act, "已删除");
                        if (cb != null) cb.onChanged();
                    }
                })
                .show();
    }
}
