package com.jizhang.assistant.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.capture.KeepAliveService;
import com.jizhang.assistant.capture.NotifyListenerService;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Fmt;
import com.jizhang.assistant.util.Prefs;

import java.util.List;

/**
 * 识别诊断。
 *
 * 当「消费没有被记录」时，这个页面用来回答三个问题：
 *   1. 通知到底有没有到达本应用？   -> 看「最近收到的通知」，为空说明根本没收到
 *   2. 到达了但被忽略？             -> 看该条是否标记为「未识别」
 *   3. 权限/开关是不是被关了？       -> 看顶部「权限与开关」
 */
public class DiagnoseActivity extends Activity {

    private SqliteStore store;
    private LinearLayout statusBox, srcList;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnose);
        store = App.db();

        statusBox = (LinearLayout) findViewById(R.id.statusBox);
        srcList = (LinearLayout) findViewById(R.id.srcList);
        empty = (TextView) findViewById(R.id.empty);

        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refresh();
                Ui.toast(DiagnoseActivity.this, "已刷新");
            }
        });
        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(DiagnoseActivity.this)
                        .setTitle("清空通知记录？")
                        .setMessage("只清空本页的来源记录，不影响已入账的流水。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                store.clearNotifySources();
                                refresh();
                            }
                        }).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        renderStatus();
        renderSources();
    }

    // ------------------------------------------------------------ 权限与开关

    private void renderStatus() {
        statusBox.removeAllViews();
        boolean access = NotifyListenerService.isEnabled(this);
        boolean connected = NotifyListenerService.connected;
        boolean monitor = Prefs.monitorEnabled(store);
        boolean keep = KeepAliveService.running;

        addRow("通知使用权", access, "已开启", "未开启 —— 点这里去开启", true);
        addRow("监听服务连接", connected, "已连接", "未连接（可能被系统回收，打开一次应用即可）", false);
        addRow("自动记账总开关", monitor, "已开启", "已关闭 —— 到设置里打开", false);
        addRow("后台常驻服务", keep, "运行中", "未运行", false);
    }

    private void addRow(String name, boolean ok, String okText, String badText,
                        final boolean clickableToSettings) {
        View row = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2,
                statusBox, false);
        TextView t1 = (TextView) row.findViewById(android.R.id.text1);
        TextView t2 = (TextView) row.findViewById(android.R.id.text2);
        t1.setText(name);
        t1.setTextSize(15f);
        t1.setTextColor(0xFF1F2329);
        t2.setText(ok ? okText : badText);
        t2.setTextSize(12f);
        t2.setTextColor(ok ? 0xFF2E7D5B : 0xFFD84343);
        if (!ok) t1.setTypeface(null, Typeface.BOLD);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        if (!ok && clickableToSettings) {
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Ui.openNotificationAccess(DiagnoseActivity.this); }
            });
        } else if (!ok) {
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Ui.toast(DiagnoseActivity.this, "打开一次本应用通常可恢复监听");
                }
            });
        }
        statusBox.addView(row);

        View line = new View(this);
        line.setBackgroundColor(0xFFEDEFF2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        lp.leftMargin = Ui.dp(this, 14);
        line.setLayoutParams(lp);
        statusBox.addView(line);
    }

    // ------------------------------------------------------------ 通知来源

    private void renderSources() {
        List<Object[]> rows = store.recentNotifySources(80);
        srcList.removeAllViews();
        if (rows.isEmpty()) {
            srcList.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        srcList.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            long at = (Long) r[0];
            String pkg = (String) r[1];
            String title = (String) r[2];
            boolean watched = (Boolean) r[3];

            View v = inf.inflate(R.layout.item_source, srcList, false);
            ((TextView) v.findViewById(R.id.srcTime)).setText(Fmt.time(at));
            ((TextView) v.findViewById(R.id.srcPkg)).setText(pkg == null ? "?" : pkg);
            TextView badge = (TextView) v.findViewById(R.id.srcBadge);
            if (watched) {
                badge.setText("已识别");
                badge.setTextColor(0xFF2E7D5B);
                badge.setBackgroundResource(R.drawable.bg_pill_green);
            } else {
                badge.setText("未识别");
                badge.setTextColor(0xFFD84343);
                badge.setBackgroundResource(R.drawable.bg_pill_red);
            }
            TextView tv = (TextView) v.findViewById(R.id.srcTitle);
            tv.setText(title == null || title.length() == 0 ? "（无标题）" : title);

            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(0xFFEDEFF2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
                lp.leftMargin = Ui.dp(this, 14);
                line.setLayoutParams(lp);
                srcList.addView(line);
            }
            srcList.addView(v);
        }
    }
}
