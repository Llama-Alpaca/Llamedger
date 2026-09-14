package com.jizhang.assistant.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Fmt;

import java.util.List;

/**
 * 流水明细。
 *
 * 交互：
 *   点按条目 = 编辑
 *   长按条目 = 操作菜单（退款冲销 / 转账标记 / 编辑 / 删除）
 */
public class TxnListActivity extends Activity {

    private SqliteStore store;
    private TxnAdapter adapter;
    private ListView list;
    private TextView empty, summary;
    private EditText search;
    private int direction = -1;
    private boolean includeVoided = false;
    private long from = 0, to = 0;

    private final TxnActions.Callback changed = new TxnActions.Callback() {
        public void onChanged() {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_txn_list);
        store = App.db();

        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);
        summary = (TextView) findViewById(R.id.summary);
        search = (EditText) findViewById(R.id.search);

        adapter = new TxnAdapter(this);
        list.setAdapter(adapter);

        findViewById(R.id.fAll).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { direction = -1; styleChips(); refresh(); }
        });
        findViewById(R.id.fOut).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { direction = Txn.DIR_OUT; styleChips(); refresh(); }
        });
        findViewById(R.id.fIn).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { direction = Txn.DIR_IN; styleChips(); refresh(); }
        });
        findViewById(R.id.fVoided).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { includeVoided = !includeVoided; styleChips(); refresh(); }
        });

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });

        // 点按 = 编辑
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Intent i = new Intent(TxnListActivity.this, AddTxnActivity.class);
                i.putExtra("txn_id", id);
                startActivity(i);
            }
        });

        // 长按 = 更多操作
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Txn t = adapter.getItemObj(pos);
                if (t != null) TxnActions.show(TxnListActivity.this, store, t, changed);
                return true;
            }
        });

        styleChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void styleChips() {
        style(R.id.fAll, direction == -1);
        style(R.id.fOut, direction == Txn.DIR_OUT);
        style(R.id.fIn, direction == Txn.DIR_IN);
        style(R.id.fVoided, includeVoided);
    }

    private void style(int id, boolean on) {
        TextView v = (TextView) findViewById(id);
        v.setBackgroundResource(on ? R.drawable.bg_pill_green : R.drawable.bg_pill_gray);
        v.setTextColor(on ? 0xFF2E7D5B : 0xFF8A9099);
    }

    private void refresh() {
        SqliteStore.TxnFilter f = new SqliteStore.TxnFilter();
        f.direction = direction;
        f.keyword = search.getText().toString().trim();
        f.includeVoided = includeVoided;
        f.from = from;
        f.to = to;
        f.limit = 1000;

        List<Txn> rows = store.query(f);
        adapter.setData(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);

        long in = 0, out = 0, transfer = 0;
        for (Txn t : rows) {
            if (t.originalTxnId != 0) continue;
            if (t.status == Txn.STATUS_VOIDED) continue;
            if (t.isTransfer) { transfer += t.amountCents; continue; }
            if (t.direction == Txn.DIR_IN) in += t.amountCents;
            else out += t.remainingCents();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(rows.size()).append(" 笔");
        sb.append("\u3000支出 ").append(Fmt.money(out));
        sb.append("\u3000收入 ").append(Fmt.money(in));
        if (transfer > 0) sb.append("\u3000转账 ").append(Fmt.money(transfer)).append("（不计收支）");
        summary.setText(sb.toString());
    }
}
