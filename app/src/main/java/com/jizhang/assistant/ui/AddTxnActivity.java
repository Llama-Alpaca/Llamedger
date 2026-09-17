package com.jizhang.assistant.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.core.parse.AmountParser;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Fmt;

import java.util.Calendar;
import java.util.List;

/** 记一笔 / 编辑一笔 */
public class AddTxnActivity extends Activity {

    private SqliteStore store;
    private int direction = Txn.DIR_OUT;
    private long occurredAt = System.currentTimeMillis();
    private long editingId = 0;

    private EditText amount, merchant, account, note;
    private Spinner category;
    private TextView time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_txn);
        store = App.db();

        amount = (EditText) findViewById(R.id.amount);
        merchant = (EditText) findViewById(R.id.merchant);
        account = (EditText) findViewById(R.id.account);
        note = (EditText) findViewById(R.id.note);
        category = (Spinner) findViewById(R.id.category);
        time = (TextView) findViewById(R.id.time);

        List<String> cats = store.categories();
        ArrayAdapter<String> ca = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, cats);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(ca);

        findViewById(R.id.tabOut).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setDirection(Txn.DIR_OUT); }
        });
        findViewById(R.id.tabIn).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setDirection(Txn.DIR_IN); }
        });
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        time.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickDate(); }
        });

        editingId = getIntent().getLongExtra("txn_id", 0);
        if (editingId > 0) {
            // 编辑模式才显示删除按钮
            View del = findViewById(R.id.delete);
            del.setVisibility(View.VISIBLE);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Txn t = store.findTxnById(editingId);
                    if (t == null) { finish(); return; }
                    TxnActions.confirmDelete(AddTxnActivity.this, store, t,
                            new TxnActions.Callback() {
                                public void onChanged() {
                                    finish();
                                }
                            });
                }
            });
            loadTxn(editingId);
        } else {
            setDirection(Txn.DIR_OUT);
            int idx = cats.indexOf("餐饮");
            if (idx >= 0) category.setSelection(idx);
        }
        updateTime();
    }

    private void setDirection(int d) {
        direction = d;
        TextView out = (TextView) findViewById(R.id.tabOut);
        TextView in = (TextView) findViewById(R.id.tabIn);
        if (d == Txn.DIR_OUT) {
            out.setBackgroundResource(R.drawable.bg_btn_primary);
            out.setTextColor(0xFFFFFFFF);
            in.setBackgroundResource(0);
            in.setTextColor(0xFF8A9099);
        } else {
            in.setBackgroundResource(R.drawable.bg_btn_primary);
            in.setTextColor(0xFFFFFFFF);
            out.setBackgroundResource(0);
            out.setTextColor(0xFF8A9099);
        }
    }

    private void loadTxn(long id) {
        Txn t = store.findTxnById(id);
        if (t == null) { finish(); return; }
        direction = t.direction;
        occurredAt = t.occurredAt;
        setDirection(direction);
        amount.setText(AmountParser.yuan(t.amountCents));
        merchant.setText(t.merchant == null ? "" : t.merchant);
        account.setText(t.account == null ? "" : t.account);
        note.setText(t.note == null ? "" : t.note);

        if (t.category != null) {
            android.widget.ArrayAdapter<?> ad = (android.widget.ArrayAdapter<?>) category.getAdapter();
            for (int i = 0; i < ad.getCount(); i++) {
                if (t.category.equals(String.valueOf(ad.getItem(i)))) {
                    category.setSelection(i);
                    break;
                }
            }
        }
        ((TextView) findViewById(R.id.save)).setText("保存修改");
        setTitle("编辑");
    }

    private void updateTime() {
        time.setText(Fmt.dateTime(occurredAt));
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(occurredAt);
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int m, int d) {
                Calendar cc = Calendar.getInstance();
                cc.setTimeInMillis(occurredAt);
                cc.set(Calendar.YEAR, y);
                cc.set(Calendar.MONTH, m);
                cc.set(Calendar.DAY_OF_MONTH, d);
                occurredAt = cc.getTimeInMillis();
                pickTime();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(occurredAt);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker v, int h, int mi) {
                Calendar cc = Calendar.getInstance();
                cc.setTimeInMillis(occurredAt);
                cc.set(Calendar.HOUR_OF_DAY, h);
                cc.set(Calendar.MINUTE, mi);
                occurredAt = cc.getTimeInMillis();
                updateTime();
            }
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private void save() {
        String amtText = amount.getText().toString().trim();
        if (amtText.length() == 0) {
            Ui.toast(this, "请输入金额");
            return;
        }
        long cents;
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(amtText);
            cents = bd.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        } catch (Exception e) {
            Ui.toast(this, "金额格式不正确");
            return;
        }
        if (cents <= 0) {
            Ui.toast(this, "金额必须大于 0");
            return;
        }

        String cat = category.getSelectedItem() == null ? "其他" : category.getSelectedItem().toString();
        String mer = merchant.getText().toString().trim();
        String acc = account.getText().toString().trim();
        String nt = note.getText().toString().trim();

        Txn t;
        boolean isNew = editingId == 0;
        if (isNew) {
            t = new Txn();
            t.createdAt = System.currentTimeMillis();
            t.source = Txn.SRC_MANUAL;
            t.confidence = 100;
            t.autoCreated = false;
        } else {
            t = store.findTxnById(editingId);
            if (t == null) { finish(); return; }
        }

        // 已退款金额不能超过新金额
        if (t.refundedCents > cents) t.refundedCents = cents;

        t.amountCents = cents;
        t.direction = direction;
        t.category = cat;
        t.merchant = mer;
        t.account = acc;
        t.note = nt.length() > 0 ? nt : t.note;
        t.occurredAt = occurredAt;
        t.updatedAt = System.currentTimeMillis();

        if (isNew) {
            t.note = nt;
            store.insertTxn(t);
            Ui.toast(this, "已记一笔");
        } else {
            store.updateTxn(t);
            Ui.toast(this, "已保存");
        }
        finish();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
    }
}
