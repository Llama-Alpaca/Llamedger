package com.jizhang.assistant.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Fmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 收支统计：按月看构成 */
public class StatsActivity extends Activity {

    private SqliteStore store;
    private long anchor = System.currentTimeMillis();
    private int direction = Txn.DIR_OUT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        store = App.db();

        findViewById(R.id.prev).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { anchor = Fmt.addMonths(anchor, -1); render(); }
        });
        findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { anchor = Fmt.addMonths(anchor, 1); render(); }
        });
        findViewById(R.id.tabOut).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { direction = Txn.DIR_OUT; render(); }
        });
        findViewById(R.id.tabIn).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { direction = Txn.DIR_IN; render(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        long from = Fmt.startOfMonth(anchor);
        long to = Fmt.endOfMonth(anchor);
        long[] totals = store.totals(from, to);

        ((TextView) findViewById(R.id.monthLabel)).setText(Fmt.month(anchor));
        ((TextView) findViewById(R.id.totalOut)).setText(Fmt.money(totals[1]));
        ((TextView) findViewById(R.id.totalIn)).setText(Fmt.money(totals[0]));

        TextView tabOut = (TextView) findViewById(R.id.tabOut);
        TextView tabIn = (TextView) findViewById(R.id.tabIn);
        if (direction == Txn.DIR_OUT) {
            tabOut.setBackgroundResource(R.drawable.bg_pill_green);
            tabOut.setTextColor(0xFF2E7D5B);
            tabIn.setBackgroundResource(R.drawable.bg_pill_gray);
            tabIn.setTextColor(0xFF8A9099);
        } else {
            tabIn.setBackgroundResource(R.drawable.bg_pill_green);
            tabIn.setTextColor(0xFF2E7D5B);
            tabOut.setBackgroundResource(R.drawable.bg_pill_gray);
            tabOut.setTextColor(0xFF8A9099);
        }

        Map<String, Long> map = store.categoryTotals(from, to, direction);
        List<Map.Entry<String, Long>> rows =
                new ArrayList<Map.Entry<String, Long>>(map.entrySet());
        Collections.sort(rows, new Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });

        LinearLayout box = (LinearLayout) findViewById(R.id.statList);
        TextView empty = (TextView) findViewById(R.id.statEmpty);
        box.removeAllViews();

        long total = 0;
        for (Map.Entry<String, Long> e : rows) total += e.getValue();

        if (rows.isEmpty() || total <= 0) {
            box.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<String, Long> e = rows.get(i);
            View v = inf.inflate(R.layout.item_stat, box, false);
            ((TextView) v.findViewById(R.id.statName)).setText(e.getKey());
            ((TextView) v.findViewById(R.id.statAmount)).setText(Fmt.money(e.getValue()));
            int pct = (int) Math.round(e.getValue() * 100.0 / total);
            ((TextView) v.findViewById(R.id.statPct)).setText(pct + "%");
            ProgressBar bar = (ProgressBar) v.findViewById(R.id.statBar);
            bar.setProgress(Math.max(2, pct));
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(0xFFEDEFF2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
                lp.leftMargin = Ui.dp(this, 14);
                lp.rightMargin = Ui.dp(this, 14);
                line.setLayoutParams(lp);
                box.addView(line);
            }
            box.addView(v);
        }
    }
}
