package com.jizhang.assistant.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.capture.KeepAliveService;
import com.jizhang.assistant.capture.NotifyListenerService;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

/** 设置页 */
public class SettingsActivity extends Activity {

    private SqliteStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        store = App.db();

        bindSwitches();
        bindRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncUi();
    }

    private void bindSwitches() {
        ((Switch) findViewById(R.id.swMonitor)).setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setMonitorEnabled(store, on);
                        if (on) {
                            KeepAliveService.start(SettingsActivity.this);
                        } else {
                            KeepAliveService.stop(SettingsActivity.this);
                        }
                        syncUi();
                    }
                });

        ((Switch) findViewById(R.id.swNotify)).setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setNotifyOnRecord(store, on);
                    }
                });

        ((Switch) findViewById(R.id.swAutoStart)).setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setAutoStart(store, on);
                    }
                });

        ((Switch) findViewById(R.id.swAutoRemove)).setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setAutoRemoveRefund(store, on);
                        syncUi();
                    }
                });

        ((Switch) findViewById(R.id.swPartial)).setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setAllowPartial(store, on);
                    }
                });
    }

    private void bindRows() {
        findViewById(R.id.rowWindow).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickWindow(); }
        });
        findViewById(R.id.rowScore).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickScore(); }
        });
        findViewById(R.id.rowGuide).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, PermissionGuideActivity.class));
            }
        });
        findViewById(R.id.rowDiagnose).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, DiagnoseActivity.class));
            }
        });
        findViewById(R.id.rowBattery).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.requestIgnoreBattery(SettingsActivity.this); }
        });
        findViewById(R.id.rowOverview).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showOverview(); }
        });
        findViewById(R.id.rowExport).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { exportCsv(); }
        });
        findViewById(R.id.rowClear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { confirmClear(); }
        });
    }

    private void syncUi() {
        ((Switch) findViewById(R.id.swMonitor)).setChecked(Prefs.monitorEnabled(store));
        ((Switch) findViewById(R.id.swNotify)).setChecked(Prefs.notifyOnRecord(store));
        ((Switch) findViewById(R.id.swAutoStart)).setChecked(Prefs.autoStart(store));
        ((Switch) findViewById(R.id.swAutoRemove)).setChecked(Prefs.autoRemoveRefund(store));
        ((Switch) findViewById(R.id.swPartial)).setChecked(Prefs.allowPartial(store));

        ((TextView) findViewById(R.id.tvWindow))
                .setText(Prefs.refundWindowDays(store) + " 天");
        ((TextView) findViewById(R.id.tvScore))
                .setText(String.valueOf(Prefs.refundMinScore(store)));
        ((TextView) findViewById(R.id.tvOverview))
                .setText(store.countTxns() + " 笔记录");

        boolean access = NotifyListenerService.isEnabled(this);
        ((TextView) findViewById(R.id.tvNotifyState))
                .setText(access ? "已开启 ›" : "未开启 ›");

        TextView sub = (TextView) findViewById(R.id.autoRemoveSub);
        if (Prefs.autoRemoveRefund(store)) {
            sub.setText("识别到退款时直接删掉原支出记录（可一键恢复）");
        } else {
            sub.setText("识别到退款时标记为「待确认」，由你手动处理");
        }
    }

    private void pickWindow() {
        final int[] opts = {7, 30, 60, 90, 180, 365};
        String[] labels = new String[opts.length];
        for (int i = 0; i < opts.length; i++) labels[i] = opts[i] + " 天";
        new AlertDialog.Builder(this)
                .setTitle("回溯查找天数")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Prefs.setRefundWindowDays(store, opts[w]);
                        syncUi();
                    }
                }).show();
    }

    private void pickScore() {
        final int[] opts = {40, 45, 50, 55, 60, 70};
        String[] labels = new String[opts.length];
        for (int i = 0; i < opts.length; i++) {
            labels[i] = opts[i] + (opts[i] <= 45 ? "（宽松，易误判）"
                    : opts[i] >= 60 ? "（严格，可能漏判）" : "（推荐）");
        }
        new AlertDialog.Builder(this)
                .setTitle("匹配严格度")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Prefs.setRefundMinScore(store, opts[w]);
                        syncUi();
                    }
                }).show();
    }

    private void showOverview() {
        int total = store.countTxns();
        long now = System.currentTimeMillis();
        long[] month = store.totals(
                com.jizhang.assistant.util.Fmt.startOfMonth(now),
                com.jizhang.assistant.util.Fmt.endOfMonth(now));
        String msg = "全部流水：" + total + " 笔\n"
                + "本月支出：" + com.jizhang.assistant.util.Fmt.money(month[1]) + "\n"
                + "本月收入：" + com.jizhang.assistant.util.Fmt.money(month[0]) + "\n"
                + "退款冲销：共 " + store.recentRefundLinks(9999).size() + " 条关联\n\n"
                + "数据全部保存在本机，不联网、不上传。";
        new AlertDialog.Builder(this)
                .setTitle("数据概览")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void exportCsv() {
        try {
            List<Txn> all = store.query(buildExportFilter());
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File out = new File(dir, "jizhang_export_" + System.currentTimeMillis() + ".csv");
            FileOutputStream fos = new FileOutputStream(out);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            w.write('\ufeff');   // BOM，Excel 打开不乱码
            w.write("时间,方向,金额,分类,商户,账户,来源,状态,已退金额,备注\n");
            for (Txn t : all) {
                w.write(csv(com.jizhang.assistant.util.Fmt.dateTime(t.occurredAt)) + ",");
                w.write((t.direction == Txn.DIR_OUT ? "支出" : "收入") + ",");
                w.write(com.jizhang.assistant.util.Fmt.money(t.amountCents) + ",");
                w.write(csv(t.category) + ",");
                w.write(csv(t.merchant) + ",");
                w.write(csv(t.account) + ",");
                w.write(sourceName(t.source) + ",");
                w.write(statusName(t.status) + ",");
                w.write((t.refundedCents > 0
                        ? com.jizhang.assistant.util.Fmt.money(t.refundedCents) : "") + ",");
                w.write(csv(t.note));
                w.write("\n");
            }
            w.close();
            new AlertDialog.Builder(this)
                    .setTitle("导出成功")
                    .setMessage("文件位置：\n" + out.getAbsolutePath())
                    .setPositiveButton("知道了", null)
                    .show();
        } catch (Throwable e) {
            Ui.toast(this, "导出失败：" + e.getMessage());
        }
    }

    private SqliteStore.TxnFilter buildExportFilter() {
        SqliteStore.TxnFilter f = new SqliteStore.TxnFilter();
        f.includeVoided = true;
        f.limit = 100000;
        return f;
    }

    private static String sourceName(int s) {
        switch (s) {
            case Txn.SRC_SMS: return "银行短信";
            case Txn.SRC_NOTIFICATION: return "通知自动";
            case Txn.SRC_IMPORT: return "导入";
            default: return "手动";
        }
    }

    private static String statusName(int s) {
        switch (s) {
            case Txn.STATUS_VOIDED: return "已撤销";
            case Txn.STATUS_PENDING: return "待确认";
            default: return "有效";
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空所有数据？")
                .setMessage("将删除全部流水、退款关联和原始事件记录，不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        store.clearAll();
                        Ui.toast(SettingsActivity.this, "已清空");
                        syncUi();
                    }
                }).show();
    }
}
