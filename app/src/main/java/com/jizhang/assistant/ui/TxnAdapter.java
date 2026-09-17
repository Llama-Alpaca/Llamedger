package com.jizhang.assistant.ui;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.jizhang.assistant.R;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** 流水列表适配器 */
public class TxnAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Txn> data = new ArrayList<Txn>();

    public TxnAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setData(List<Txn> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public List<Txn> getData() {
        return data;
    }

    public Txn getItemObj(int i) {
        return data.get(i);
    }

    @Override public int getCount() { return data.size(); }

    @Override public Object getItem(int i) { return data.get(i); }

    @Override public long getItemId(int i) { return data.get(i).id; }

    @Override
    public View getView(int pos, View convert, ViewGroup parent) {
        View v = convert;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_txn, parent, false);
        }
        bind(ctx, v, data.get(pos));
        return v;
    }

    /** 渲染一条流水到已 inflate 的 item 视图上（首页也复用这套渲染） */
    public static void bind(Context ctx, View v, Txn t) {
        TextView merchant = (TextView) v.findViewById(R.id.txnMerchant);
        TextView meta = (TextView) v.findViewById(R.id.txnMeta);
        TextView amount = (TextView) v.findViewById(R.id.txnAmount);
        TextView time = (TextView) v.findViewById(R.id.txnTime);
        TextView badge = (TextView) v.findViewById(R.id.txnBadge);

        String name = t.merchant;
        if (name == null || name.trim().length() == 0) {
            name = (t.category == null || t.category.length() == 0) ? "未分类" : t.category;
        }
        merchant.setText(name);

        // 副标题：分类 · 账户 · 来源
        StringBuilder sb = new StringBuilder();
        if (t.category != null && t.category.length() > 0) sb.append(t.category);
        if (t.account != null && t.account.length() > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(t.account);
        }
        if (t.autoCreated) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("自动");
        }
        meta.setText(sb.toString());

        // 金额：转账不带正负号（它不是收入也不是支出）
        long showCents = t.direction == Txn.DIR_OUT ? t.remainingCents() : t.amountCents;
        if (t.isTransfer) {
            amount.setText(Fmt.money(showCents));
            amount.setTextColor(0xFF8A9099);
        } else {
            amount.setText(Fmt.signed(t.direction, showCents));
            amount.setTextColor(t.direction == Txn.DIR_IN ? 0xFF2E7D5B : 0xFF1F2329);
        }

        // 时间
        time.setText(Fmt.time(t.occurredAt));

        // 角标
        if (t.isTransfer) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(t.pairTxnId != 0 ? "转账·已配对" : "转账");
            badge.setTextColor(0xFFE08A1E);
            badge.setBackgroundResource(R.drawable.bg_pill_gray);
        } else if (t.originalTxnId != 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("退款冲抵");
            badge.setTextColor(0xFF2E7D5B);
            badge.setBackgroundResource(R.drawable.bg_pill_green);
        } else if (t.status == Txn.STATUS_VOIDED) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("已撤销");
            badge.setTextColor(0xFF8A9099);
            badge.setBackgroundResource(R.drawable.bg_pill_gray);
        } else if (t.status == Txn.STATUS_PENDING) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("待确认退款");
            badge.setTextColor(0xFFD84343);
            badge.setBackgroundResource(R.drawable.bg_pill_red);
        } else if (t.refundedCents > 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("已退" + Fmt.money(t.refundedCents));
            badge.setTextColor(0xFFE08A1E);
            badge.setBackgroundResource(R.drawable.bg_pill_gray);
        } else if (t.confidence > 0 && t.confidence < 60 && t.autoCreated) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("待核对");
            badge.setTextColor(0xFFE08A1E);
            badge.setBackgroundResource(R.drawable.bg_pill_gray);
        } else {
            badge.setVisibility(View.GONE);
        }

        // 已撤销的支出加删除线
        if (t.status == Txn.STATUS_VOIDED) {
            merchant.setPaintFlags(merchant.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            amount.setPaintFlags(amount.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            merchant.setTextColor(0xFF8A9099);
            amount.setTextColor(0xFF8A9099);
        } else {
            merchant.setPaintFlags(merchant.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            amount.setPaintFlags(amount.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            merchant.setTextColor(0xFF1F2329);
        }
    }
}
