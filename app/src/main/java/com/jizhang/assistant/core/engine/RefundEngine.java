package com.jizhang.assistant.core.engine;

import com.jizhang.assistant.core.model.RefundLink;
import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.core.parse.Lexicon;

import java.util.List;

/**
 * 退款 / 退回 自动冲销引擎  —— 本软件的核心特色。
 *
 * 目标：识别到一笔退款或退回（含转账过期退回）时，自动找到对应原支出，
 *       全额退款则把原支出整条移除，部分退款则冲减原支出金额。
 *
 * 匹配思路是「打分」而不是「精确相等」，因为通知/短信里的商户名、时间往往有出入：
 *   金额一致        +60   金额部分一致   +40
 *   商户完全相同    +25   商户互相包含   +15   商户明显不同  -35
 *   时间越近加分越多（1 小时内 +20，最长 90 天 +3）
 *   账户/渠道一致   +10   来源一致 +5     转账退回且原分类为转账 +10
 * 总分达到阈值（默认 55）才认定为同一笔，避免误删无关支出。
 */
public final class RefundEngine {

    private RefundEngine() {}

    public static final int DEFAULT_MIN_SCORE = 55;
    public static final int DEFAULT_WINDOW_DAYS = 90;

    private static final long HOUR = 3600L * 1000L;
    private static final long DAY = 24L * HOUR;

    /** 退款处理策略 */
    public static class Policy {
        /** true = 命中后自动移除/冲减原支出；false = 标记为待确认，交由用户点确认 */
        public boolean autoRemove = true;
        /** 向前回溯多少天找原支出 */
        public int windowDays = DEFAULT_WINDOW_DAYS;
        /** 认定阈值 */
        public int minScore = DEFAULT_MIN_SCORE;
        /** 是否允许部分退款（多次小额退款）冲减原支出 */
        public boolean allowPartial = true;

        public static Policy defaults() {
            return new Policy();
        }
    }

    /** 匹配/处理结果 */
    public static class Outcome {
        public boolean matched;
        public long originalTxnId;
        public int score;
        public String reason = "";
        /** 本次冲抵金额(分) */
        public long appliedCents;
        /** RefundLink.MODE_FULL / MODE_PARTIAL，未匹配为 -1 */
        public int mode = -1;
        /** 原支出是否已被整条移除 */
        public boolean removed;
        /** 未达阈值时，最接近的候选说明，便于排查误判 */
        public String rejectHint = "";

        @Override
        public String toString() {
            return "Outcome{matched=" + matched + ", original=" + originalTxnId
                    + ", score=" + score + ", applied=" + appliedCents
                    + ", removed=" + removed + ", reason=" + reason
                    + (matched ? "" : ", hint=" + rejectHint) + "}";
        }
    }

    /** 只做匹配，不改数据 */
    public static Outcome match(Txn refund, List<Txn> candidates, Policy p) {
        Outcome best = new Outcome();
        int bestScore = Integer.MIN_VALUE;
        Txn bestTxn = null;
        String bestWhy = null;

        long refundTime = refund.occurredAt > 0 ? refund.occurredAt : refund.createdAt;

        for (Txn c : candidates) {
            if (c == null) continue;
            if (c.direction != Txn.DIR_OUT) continue;
            if (c.status != Txn.STATUS_ACTIVE) continue;
            if (c.isTransfer) continue;   // 账户间转账不是消费，不该被退款冲销
            if (refund.id != 0 && c.id == refund.id) continue;
            if (refundTime > 0 && c.occurredAt > refundTime) continue;  // 原支出必须在退款之前
            if (c.remainingCents() <= 0) continue;

            Score s = score(refund, c, p);
            if (s == null) continue;
            if (s.value > bestScore) {
                bestScore = s.value;
                bestTxn = c;
                bestWhy = s.why;
            }
        }

        if (bestTxn == null) {
            best.rejectHint = "没有可匹配的原支出记录";
            return best;
        }

        best.score = bestScore;
        best.originalTxnId = bestTxn.id;
        best.reason = bestWhy;

        // 双保险：分数达标之外，还要有至少一项「佐证」，否则宁可不动账。
        // 防的是「两个月前也有一笔 100 元支出」这种纯金额巧合被误删。
        boolean ok = bestScore >= p.minScore && corroborated(refund, bestTxn);
        if (ok) {
            best.matched = true;
            long remaining = bestTxn.remainingCents();
            long apply = Math.min(refund.amountCents, remaining);
            best.appliedCents = apply;
            best.mode = (bestTxn.refundedCents + apply >= bestTxn.amountCents)
                    ? RefundLink.MODE_FULL : RefundLink.MODE_PARTIAL;
        } else if (bestScore >= p.minScore) {
            best.rejectHint = "分数 " + bestScore + " 虽达标，但缺少佐证（商户/时间/渠道都对不上），"
                    + "为避免误删未自动冲销。原判定：" + bestWhy;
        } else {
            best.rejectHint = "最高分 " + bestScore + " 未达阈值 " + p.minScore
                    + "：" + bestWhy;
        }
        return best;
    }

    /**
     * 执行冲销：匹配 + 落库 + 移除/冲减原支出。
     * 调用前 refund 必须已经入库（有 id）。
     */
    public static Outcome apply(Txn refund, TxnStore store, Policy p) {
        long refundTime = refund.occurredAt > 0 ? refund.occurredAt : refund.createdAt;
        long from = refundTime - (long) p.windowDays * DAY;
        List<Txn> candidates = store.findRefundCandidates(refundTime, from, 800);

        Outcome out = match(refund, candidates, p);
        if (!out.matched) {
            return out;
        }

        Txn original = store.findTxnById(out.originalTxnId);
        if (original == null) {
            out.matched = false;
            out.rejectHint = "候选原支出已不存在";
            return out;
        }

        long remaining = original.remainingCents();
        long apply = Math.min(refund.amountCents, remaining);
        int mode = (original.refundedCents + apply >= original.amountCents)
                ? RefundLink.MODE_FULL : RefundLink.MODE_PARTIAL;

        RefundLink link = new RefundLink();
        link.refundTxnId = refund.id;
        link.originalTxnId = original.id;
        link.amountCents = apply;
        link.mode = mode;
        link.score = out.score;
        link.reason = out.reason;
        link.autoRemoved = p.autoRemove;
        link.createdAt = System.currentTimeMillis();
        store.insertRefundLink(link);

        out.appliedCents = apply;
        out.mode = mode;

        if (p.autoRemove) {
            original.refundedCents += apply;
            if (original.refundedCents >= original.amountCents) {
                original.refundedCents = original.amountCents;
                original.status = Txn.STATUS_VOIDED;   // 全额退款 → 整条支出记录移除
                out.removed = true;
            }
            original.updatedAt = System.currentTimeMillis();
            store.updateTxn(original);

            // 退款本身作为「冲抵记录」关联到原支出，不计入收入统计
            refund.originalTxnId = original.id;
            refund.category = "退款冲抵";
            store.updateTxn(refund);
        } else {
            original.status = Txn.STATUS_PENDING;
            original.updatedAt = System.currentTimeMillis();
            store.updateTxn(original);
            refund.originalTxnId = original.id;
            refund.category = "退款待确认";
            store.updateTxn(refund);
        }
        return out;
    }

    /** 撤销一次自动冲销，恢复原支出 */
    public static boolean undo(RefundLink link, TxnStore store) {
        if (link == null) return false;
        Txn original = store.findTxnById(link.originalTxnId);
        if (original != null) {
            original.refundedCents -= link.amountCents;
            if (original.refundedCents < 0) original.refundedCents = 0;
            original.status = Txn.STATUS_ACTIVE;
            original.updatedAt = System.currentTimeMillis();
            store.updateTxn(original);
        }
        Txn refund = store.findTxnById(link.refundTxnId);
        if (refund != null) {
            refund.originalTxnId = 0;
            refund.category = "退款";
            refund.updatedAt = System.currentTimeMillis();
            store.updateTxn(refund);
        }
        store.deleteRefundLinkByRefundTxn(link.refundTxnId);
        return true;
    }

    /**
     * 佐证检查：满足任意一条才允许自动冲销。
     *   ① 商户名能对上（相同或互相包含）
     *   ② 24 小时内发生（时间足够近）
     *   ③ 转账退回，且原支出分类就是转账/红包
     *   ④ 同一渠道且 7 天内
     */
    private static boolean corroborated(Txn refund, Txn c) {
        String rm = norm(refund.merchant);
        String cm = norm(c.merchant);
        if (rm.length() > 0 && cm.length() > 0) {
            if (rm.equals(cm) || rm.contains(cm) || cm.contains(rm)) return true;
        }
        long refundTime = refund.occurredAt > 0 ? refund.occurredAt : refund.createdAt;
        long dt = Math.max(0, refundTime - c.occurredAt);
        if (dt <= DAY) return true;

        String refundText = (refund.note == null ? "" : refund.note) + " "
                + (refund.merchant == null ? "" : refund.merchant);
        if (Lexicon.containsAny(refundText, Lexicon.TRANSFER_WORDS)
                && ("转账".equals(c.category) || "人情".equals(c.category))) {
            return true;
        }
        String ra = norm(refund.account);
        String ca = norm(c.account);
        if (ra.length() > 0 && ca.length() > 0 && ra.equals(ca) && dt <= 7 * DAY) {
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 打分

    private static class Score {
        int value;
        String why;
    }

    private static Score score(Txn refund, Txn c, Policy p) {
        Score s = new Score();
        StringBuilder why = new StringBuilder();
        int v = 0;

        long remaining = c.remainingCents();
        if (refund.amountCents == remaining) {
            v += 60; why.append("金额一致+60 ");
        } else if (refund.amountCents < remaining) {
            if (!p.allowPartial) return null;
            v += 40; why.append("部分退款+40 ");
        } else {
            // 退款金额大于该笔剩余金额：可能是子交易多退，弱支持但不否定
            if (!p.allowPartial) return null;
            v += 15; why.append("金额偏大+15 ");
        }

        String rm = norm(refund.merchant);
        String cm = norm(c.merchant);
        if (rm.length() > 0 && cm.length() > 0) {
            if (rm.equals(cm)) {
                v += 25; why.append("商户一致+25 ");
            } else if (rm.contains(cm) || cm.contains(rm)) {
                v += 15; why.append("商户相近+15 ");
            } else {
                v -= 35; why.append("商户不同-35 ");
            }
        }

        long refundTime = refund.occurredAt > 0 ? refund.occurredAt : refund.createdAt;
        long dt = Math.max(0, refundTime - c.occurredAt);
        if (dt <= HOUR) {
            v += 20; why.append("1小时内+20 ");
        } else if (dt <= DAY) {
            v += 18; why.append("24小时内+18 ");
        } else if (dt <= 7 * DAY) {
            v += 14; why.append("7天内+14 ");
        } else if (dt <= 30 * DAY) {
            v += 8; why.append("30天内+8 ");
        } else if (dt <= 90 * DAY) {
            v += 3; why.append("90天内+3 ");
        }

        String ra = norm(refund.account);
        String ca = norm(c.account);
        if (ra.length() > 0 && ca.length() > 0 && ra.equals(ca)) {
            v += 10; why.append("渠道一致+10 ");
        }

        if (refund.source == c.source) {
            v += 5; why.append("来源一致+5 ");
        }

        // 转账过期退回：命中原分类为转账时额外加权
        String refundText = (refund.note == null ? "" : refund.note) + " "
                + (refund.merchant == null ? "" : refund.merchant);
        boolean transferRefund = Lexicon.containsAny(refundText, Lexicon.TRANSFER_WORDS);
        if (transferRefund && "转账".equals(c.category)) {
            v += 10; why.append("转账退回+10 ");
        }

        if (remaining > refund.amountCents * 3 && refund.amountCents > 0) {
            // 原支出远大于退款额，且没有任何强证据时降低误配风险
            if (rm.length() == 0 || cm.length() == 0) {
                v -= 10; why.append("金额悬殊-10 ");
            }
        }

        s.value = v;
        s.why = why.toString().trim();
        return s;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String t = Lexicon.normalize(s).toLowerCase();
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isLetterOrDigit(ch)) sb.append(ch);
        }
        return sb.toString();
    }
}
