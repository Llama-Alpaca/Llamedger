package com.jizhang.assistant.core.engine;

import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.core.parse.Lexicon;
import com.jizhang.assistant.core.parse.ParseResult;

import java.util.List;

/**
 * 内部转账配对引擎。
 *
 * 「微信零钱提现到招行卡」这类操作会同时产生两条通知：
 *     微信：提现  3000 元（钱离开零钱）
 *     招行：收款  3000 元（钱进入银行卡）
 * 钱只是从你自己的一个口袋换到另一个口袋，既不是支出也不是收入。
 *
 * 这个引擎负责把这两条流水认成一对，双方都标记为「转账」，
 * 从而在收支统计里双双被排除，不会虚增支出或收入。
 */
public final class TransferEngine {

    private TransferEngine() {}

    /** 配对时间窗：提现到账通常在几分钟到几小时内，给足 3 天余量 */
    public static final long PAIR_WINDOW = 3L * 24 * 3600 * 1000L;

    /**
     * 转账线索的有效窗口。
     *
     * 微信/支付宝的「零钱提现已到账」通知里根本不带金额，
     * 没法直接记成一笔流水。所以只在解析失败时记下一条线索，
     * 等银行那侧「收款 xxx 元」到达时，用它来判定「这笔是我的提现入账，不是收入」。
     *
     * 窗口取 2 小时：提现已到账与银行收款推送几乎同时到达，窗口太宽容易误伤真实入账。
     */
    public static final long HINT_WINDOW = 2L * 3600 * 1000L;

    /** 这段文本是否属于「自己的账户之间搬钱」 */
    public static boolean looksLikeInternalTransfer(String text) {
        return Lexicon.containsAny(text, Lexicon.INTERNAL_TRANSFER_WORDS);
    }

    /**
     * 解析失败时的兜底：如果这条通知其实是「提现/充值」类，只是没带金额，
     * 就记下一条线索，留给银行那侧使用。
     *
     * @return 是否记下了线索
     */
    public static boolean noteHintIfInternalTransfer(RawEvent raw, ParseResult pr, TxnStore store) {
        if (raw == null || store == null) return false;
        if (pr != null && pr.success) return false;      // 能解析出金额就不用线索
        String text = raw.fullText();
        if (!looksLikeInternalTransfer(text)) return false;
        store.insertTransferHint(raw.receivedAt,
                raw.pkg != null ? raw.pkg : raw.sender, text);
        return true;
    }

    /**
     * 尝试为一条流水找到「另一半」。找到就双向配对并落库。
     *
     * @return 配对到的流水 id，未配对返回 0
     */
    public static long tryPair(Txn t, TxnStore store) {
        if (t == null || store == null) return 0;
        if (t.pairTxnId != 0) return t.pairTxnId;
        if (t.originalTxnId != 0) return 0;          // 退款冲抵记录不参与配对
        if (t.status == Txn.STATUS_VOIDED) return 0;

        int opposite = (t.direction == Txn.DIR_OUT) ? Txn.DIR_IN : Txn.DIR_OUT;
        long from = t.occurredAt - PAIR_WINDOW;
        long to = t.occurredAt + PAIR_WINDOW;

        List<Txn> cands = store.findRecentByDirection(opposite, from, to, 300);
        Txn best = null;
        long bestDt = Long.MAX_VALUE;

        for (Txn c : cands) {
            if (c == null || c.id == t.id) continue;
            if (c.pairTxnId != 0) continue;
            if (c.originalTxnId != 0) continue;
            if (c.status == Txn.STATUS_VOIDED) continue;
            if (c.amountCents != t.amountCents) continue;   // 金额必须一分不差
            if (c.amountCents <= 0) continue;

            // 关键约束：至少有一方已经明确是内部转账（提现/充值/还款），
            // 否则「同样金额的一进一出」可能只是巧合，不能乱配。
            if (!t.isTransfer && !c.isTransfer) continue;

            long dt = Math.abs(c.occurredAt - t.occurredAt);
            if (dt < bestDt) {
                bestDt = dt;
                best = c;
            }
        }

        // 另一半还没到（银行入账通常晚几秒到几分钟）。
        // 自己的转账标记已经立住，等对方到达时由对方触发配对即可。
        if (best == null) return 0;

        long now = System.currentTimeMillis();

        t.isTransfer = true;
        t.pairTxnId = best.id;
        t.updatedAt = now;
        store.updateTxn(t);

        best.isTransfer = true;
        best.pairTxnId = t.id;
        best.updatedAt = now;
        store.updateTxn(best);

        return best.id;
    }

    /** 取消一次配对，两条流水都恢复为普通记录 */
    public static void unpair(Txn t, TxnStore store) {
        if (t == null || t.pairTxnId == 0) return;
        Txn other = store.findTxnById(t.pairTxnId);
        long now = System.currentTimeMillis();
        if (other != null) {
            other.pairTxnId = 0;
            other.isTransfer = false;
            other.updatedAt = now;
            store.updateTxn(other);
        }
        t.pairTxnId = 0;
        t.isTransfer = false;
        t.updatedAt = now;
        store.updateTxn(t);
    }
}
