package com.jizhang.assistant.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.capture.KeepAliveService;
import com.jizhang.assistant.capture.Notifier;
import com.jizhang.assistant.capture.NotifyListenerService;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Fmt;
import com.jizhang.assistant.util.Prefs;

import java.util.List;

/** 首页：本月概览 + 运行状态 + 最近流水 */
public class MainActivity extends Activity {

    private SqliteStore store;
    private LinearLayout recentList;
    private TextView emptyHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = App.db();
        recentList = (LinearLayout) findViewById(R.id.recentList);
        emptyHint = (TextView) findViewById(R.id.emptyHint);

        findViewById(R.id.btnAdd).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AddTxnActivity.class));
            }
        });
        findViewById(R.id.btnAll).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TxnListActivity.class));
            }
        });
        findViewById(R.id.btnStats).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StatsActivity.class));
            }
        });
        findViewById(R.id.statusCard).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.statusAction).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // Android 13+ 需要运行时申请通知权限
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        Notifier.ensureChannels(this);

        // 第一次打开且未授权，直接引导去开权限
        if (!Prefs.monitorEnabled(store)) {
            // 用户手动关过，不打扰
        } else if (!NotifyListenerService.isEnabled(this)
                && !store.getBool(Prefs.GUIDE_DONE, false)) {
            startActivity(new Intent(this, PermissionGuideActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // 自动记账开着就保活
        if (Prefs.monitorEnabled(store)) {
            KeepAliveService.start(this);
        }
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        long from = Fmt.startOfMonth(now);
        long to = Fmt.endOfMonth(now);
        long[] t = store.totals(from, to);

        ((TextView) findViewById(R.id.monthTitle)).setText(Fmt.month(now) + " 支出");
        ((TextView) findViewById(R.id.monthOut)).setText(Fmt.money(t[1]));
        ((TextView) findViewById(R.id.monthIn)).setText(Fmt.money(t[0]));
        long balance = t[0] - t[1];
        ((TextView) findViewById(R.id.monthBalance)).setText(Fmt.money(balance));

        boolean enabled = Prefs.monitorEnabled(store);
        boolean access = NotifyListenerService.isEnabled(this);
        View dot = findViewById(R.id.statusDot);
        TextView title = (TextView) findViewById(R.id.statusTitle);
        TextView sub = (TextView) findViewById(R.id.statusSub);
        TextView action = (TextView) findViewById(R.id.statusAction);

        if (!enabled) {
            dot.setBackgroundResource(R.drawable.bg_pill_gray);
            title.setText("自动记账已暂停");
            sub.setText("点击右侧进入设置重新开启");
            action.setText("开启");
        } else if (!access) {
            dot.setBackgroundResource(R.drawable.bg_pill_red);
            title.setText("还需要开启通知使用权");
            sub.setText("否则无法读取招行、微信、支付宝的付款通知");
            action.setText("去设置");
            findViewById(R.id.statusCard).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, PermissionGuideActivity.class));
                }
            });
        } else {
            dot.setBackgroundResource(R.drawable.bg_dot);
            title.setText("自动记账运行中");
            sub.setText("正在监听招行、微信、支付宝通知");
            action.setText("设置");
        }

        // 最近流水
        List<Txn> recent = store.recentTxns(8);
        recentList.removeAllViews();
        if (recent.isEmpty()) {
            recentList.setVisibility(View.GONE);
            emptyHint.setVisibility(View.VISIBLE);
        } else {
            recentList.setVisibility(View.VISIBLE);
            emptyHint.setVisibility(View.GONE);
            LayoutInflater inf = LayoutInflater.from(this);
            for (int i = 0; i < recent.size(); i++) {
                if (i > 0) {
                    View line = new View(this);
                    line.setBackgroundColor(0xFFEDEFF2);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
                    lp.leftMargin = Ui.dp(this, 14);
                    line.setLayoutParams(lp);
                    recentList.addView(line);
                }
                Txn item = recent.get(i);
                View row = inf.inflate(R.layout.item_txn, recentList, false);
                TxnAdapter.bind(this, row, item);
                row.setOnClickListener(new RowClick(item.id));
                recentList.addView(row);
            }
        }
    }

    private class RowClick implements View.OnClickListener {
        private final long id;
        RowClick(long id) { this.id = id; }

        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, AddTxnActivity.class);
            i.putExtra("txn_id", id);
            startActivity(i);
        }
    }
}
