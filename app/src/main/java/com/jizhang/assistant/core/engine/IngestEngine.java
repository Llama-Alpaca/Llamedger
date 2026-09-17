package com.jizhang.assistant.core.engine;

import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.core.parse.AmountParser;
import com.jizhang.assistant.core.parse.BankParser;
import com.jizhang.assistant.core.parse.EventParser;
import com.jizhang.assistant.core.parse.Lexicon;
import com.jizhang.assistant.core.parse.ParseResult;

import java.util.List;

/**
 * 入账编排：原始事件 → 解析 → 去重 → 落库 → 退款冲销。
 * 这是整个自动记账流程的入口，capture 层捕获到的每条通知都走这里。
 */
public final class IngestEngine {

    private IngestEngine() {}

    private static final int NOTE_MAX = 500;

    public static class Result {
        public boolean parsed;
        public boolean stored;
        public boolean duplicate;
        public boolean refund;
        public boolean refundMatched;
        public boolean originalRemoved;
        public boolean partialRefund;
        public long txnId;
        public long originalTxnId;
        public String message = "";
        public Txn txn;
        public ParseResult parse;

        @Override
        public String toString() {
            return "Ingest{parsed=" + parsed + ", stored=" + stored + ", dup=" + duplicate
                    + ", refund=" + refund + ", matched=" + refundMatched
                    + ", removed=" + originalRemoved + ", msg=" + message + "}";
        }
    }

    /** 便捷入口：解析并入库 */
    public static Result ingest(RawEvent raw, TxnStore store, RefundEngine.Policy policy) {
        ParseResult pr = EventParser.parse(raw);
        return ingest(raw, pr, store, policy);
    }

    public static Result ingest(RawEvent raw, ParseResult pr, TxnStore store,
                                RefundEngine.Policy policy) {
        Result res = new Result();
        res.parse = pr;
        if (pr == null || !pr.success) {
            res.message = pr == null ? "解析失败" : pr.reason;
            return res;
        }
        res.parsed = true;
        res.refund = pr.refund;

        Txn t = build(raw, pr);
        res.txn = t;

        // 银行入账侧：如果此前收到过「提现已到账」这类不带金额的转账通知，
        // 就认定这笔收款是账户间转账，而不是收入。
        if (!pr.refund && !t.isTransfer && t.direction == Txn.DIR_IN) {
            long hintId = store.findTransferHint(t.occurredAt, TransferEngine.HINT_WINDOW);
            if (hintId != 0) {
                t.isTransfer = true;
                t.category = "转账";
                store.consumeTransferHint(hintId);
            }
        }

        // 去重：候选窗口放宽到 ±6 小时。
        // 因为招行通知的时间是从文案解析出来的，微信/支付宝通知常常没带时间只能用接收时间，
        // 两者可能有分钟级偏差；真正的判重由 DedupeEngine 的精确规则负责。
        long win = 6 * 3600 * 1000L;
        List<Txn> recent = store.findRecentByDirection(
                t.direction, t.occurredAt - win, t.occurredAt + win, 200);
        DedupeEngine.DupResult dup = DedupeEngine.check(t, recent);
        if (dup.duplicate && dup.existing != null) {
            res.duplicate = true;
            res.txnId = dup.existing.id;
            res.txn = dup.existing;
            if (dup.shouldEnrich) {
                DedupeEngine.enrich(dup.existing, t, store);
            }
            res.message = "重复记录已合并：" + Format.money(t.amountCents)
                    + (t.merchant != null ? " " + t.merchant : "");
            return res;
        }

        long id = store.insertTxn(t);
        t.id = id;
        res.txnId = id;
        res.stored = true;

        if (pr.refund) {
            // ★ 退款/退回：尝试找到对应原支出并冲销
            RefundEngine.Outcome o = RefundEngine.apply(t, store, policy);
            res.refundMatched = o.matched;
            res.originalTxnId = o.originalTxnId;
            res.originalRemoved = o.removed;
            res.partialRefund = o.mode == com.jizhang.assistant.core.model.RefundLink.MODE_PARTIAL;

            if (o.matched) {
                if (o.removed) {
                    res.message = "识别到退款 " + Format.money(t.amountCents)
                            + "，已移除对应支出记录（匹配分 " + o.score + "）";
                } else {
                    res.message = "识别到部分退款 " + Format.money(t.amountCents)
                            + "，已冲减原支出（匹配分 " + o.score + "）";
                }
                res.txn = t;
            } else {
                // 没有找到原支出：记为一笔普通收入，并提示用户核对
                t.category = "退款";
                t.note = append(t.note, "[未匹配到原支出，" + o.rejectHint + "]");
                store.updateTxn(t);
                res.message = "识别到退款 " + Format.money(t.amountCents)
                        + "，但未找到对应支出，已按收入记录";
            }
        } else if (t.isTransfer) {
            // 尝试与另一半（如银行入账）配对
            long pairId = TransferEngine.tryPair(t, store);
            String side = t.direction == Txn.DIR_OUT ? "转出" : "转入";
            if (pairId != 0) {
                res.message = "识别到账户间转账 " + Format.money(t.amountCents)
                        + "（" + side + "），已与对应入账配对，不计入收支";
            } else {
                res.message = "识别到账户间转账 " + Format.money(t.amountCents)
                        + "（" + side + "），不计入收支统计";
            }
        } else {
            // 普通支出/收入：也看看是不是某笔转账的另一半（如银行收款先到）
            long pairId = TransferEngine.tryPair(t, store);
            if (pairId != 0) {
                t.isTransfer = true;
                t.category = "转账";
                store.updateTxn(t);
                res.message = "识别到账户间转账 " + Format.money(t.amountCents)
                        + "，已与对应转出配对，不计入收支";
            } else {
                res.message = (t.direction == Txn.DIR_OUT ? "记支出 " : "记收入 ")
                        + Format.money(t.amountCents)
                        + (t.merchant != null ? " · " + t.merchant : "")
                        + " · " + t.category;
            }
        }
        return res;
    }

    /** 从解析结果构造流水 */
    public static Txn build(RawEvent raw, ParseResult pr) {
        Txn t = new Txn();
        t.occurredAt = pr.occurredAt > 0 ? pr.occurredAt
                : (raw.receivedAt > 0 ? raw.receivedAt : System.currentTimeMillis());
        t.amountCents = pr.amountCents;
        t.direction = pr.direction;
        t.merchant = pr.merchant;

        boolean bank = raw.kind == RawEvent.KIND_SMS
                || BankParser.isBankSource(raw.sender, raw.pkg);
        if (pr.account != null && pr.account.length() > 0) {
            t.account = bank ? ("尾号" + pr.account) : pr.account;
        } else {
            t.account = bank ? "银行卡" : Lexicon.pkgLabel(raw.pkg);
        }

        String text = raw.fullText();
        t.category = CategoryGuesser.guess(pr.merchant, text, pr.direction);
        if (pr.refund && "其他".equals(t.category)) {
            t.category = "退款";
        }

        // 内部转账：提现/充值/还款 —— 钱只是在自己的账户之间搬家，
        // 既不是支出也不是收入，必须标记出来并从统计里排除。
        if (!pr.refund && TransferEngine.looksLikeInternalTransfer(text)) {
            t.isTransfer = true;
            t.category = "转账";
        }
        t.note = text.length() > NOTE_MAX ? text.substring(0, NOTE_MAX) : text;
        t.source = raw.kind == RawEvent.KIND_SMS ? Txn.SRC_SMS : Txn.SRC_NOTIFICATION;
        t.rawId = raw.id;
        t.confidence = pr.confidence;
        t.autoCreated = true;
        t.createdAt = System.currentTimeMillis();
        t.updatedAt = t.createdAt;
        t.dedupeKey = DedupeEngine.buildKey(t);
        return t;
    }

    private static String append(String base, String extra) {
        if (base == null) return extra;
        return base + " " + extra;
    }

    /** 金额格式化小工具（核心层内自用，避免依赖 Android） */
    public static final class Format {
        public static String money(long cents) {
            return "¥" + AmountParser.yuan(cents);
        }
    }
}
