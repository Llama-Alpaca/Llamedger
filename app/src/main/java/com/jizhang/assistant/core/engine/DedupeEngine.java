package com.jizhang.assistant.core.engine;

import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.core.parse.Lexicon;

import java.util.List;

/**
 * 去重引擎。
 *
 * 同一笔支出经常会同时惊动两个通道：招行 App 通知报一次、微信通知再报一次，
 * 若不去重会重复记账。这里按「金额 + 方向 + 时间邻近 + 商户/渠道可解释」判定重复，
 * 并在新数据信息更全时回填商户名等字段（而不是简单丢弃）。
 */
public final class DedupeEngine {

    private DedupeEngine() {}

    /** 跨通道去重窗口（毫秒） */
    private static final long CROSS_WINDOW = 5 * 60 * 1000L;
    /** 同通道去重窗口，同一渠道的重复推送间隔更短 */
    private static final long SAME_WINDOW = 3 * 60 * 1000L;

    public static class DupResult {
        public boolean duplicate;
        public Txn existing;
        public boolean shouldEnrich;
        public String reason = "";
    }

    /**
     * @param recent 同一时间区间内的近期流水（调用方限定 direction 与时间范围）
     */
    public static DupResult check(Txn incoming, List<Txn> recent) {
        DupResult r = new DupResult();
        if (incoming == null || recent == null) return r;

        for (Txn o : recent) {
            if (o == null || o.id == incoming.id) continue;
            if (o.direction != incoming.direction) continue;
            if (o.amountCents != incoming.amountCents) continue;
            // 一笔是转账、一笔是消费，即便金额相同也不是同一件事
            if (o.isTransfer != incoming.isTransfer) continue;

            long window;
            long dt;
            boolean sameSource = (o.source == incoming.source);
            if (sameSource) {
                // 同通道：必须交易时间对得上
                dt = Math.abs(incoming.occurredAt - o.occurredAt);
                window = SAME_WINDOW;
            } else {
                // 跨通道：招行通知的时间是从文案里解析的，微信/支付宝通知往往没带时间
                // 只能用接收时间，两者天然可能对不齐。取「交易时间差」和「入库时间差」
                // 中较小的那个，任一维度对得上即认为时间邻近。
                long byOccur = Math.abs(incoming.occurredAt - o.occurredAt);
                long byIngest = Math.abs(incoming.createdAt - o.createdAt);
                dt = Math.min(byOccur, byIngest);
                window = CROSS_WINDOW;
            }
            if (dt > window) continue;

            if (!explainable(o, incoming)) continue;

            r.duplicate = true;
            r.existing = o;
            r.shouldEnrich = isRicher(incoming, o);
            r.reason = "金额方向一致，时间相差 " + (dt / 1000) + " 秒，"
                    + (sameSource ? "同通道" : "跨通道") + "判定为同一笔";
            return r;
        }
        return r;
    }

    /**
     * 两笔金额方向都相同且时间邻近时，还要能解释得通才算重复：
     * 商户相同 / 互相包含 / 有一方是支付渠道名（微信、支付宝…）/ 有一方缺商户名。
     */
    private static boolean explainable(Txn a, Txn b) {
        String ma = norm(a.merchant);
        String mb = norm(b.merchant);
        if (ma.length() == 0 || mb.length() == 0) return true;
        if (ma.equals(mb)) return true;
        if (ma.contains(mb) || mb.contains(ma)) return true;
        if (isChannelName(ma) || isChannelName(mb)) return true;
        return false;
    }

    private static boolean isChannelName(String m) {
        if (m.length() == 0) return false;
        String[] channels = {"微信", "支付宝", "财付通", "云闪付", "微信支付",
                "支付宝中国", "tenpay", "alipay", "wechat"};
        for (String c : channels) {
            if (m.contains(norm(c))) return true;
        }
        return false;
    }

    /** incoming 是否比 existing 信息更全（用于回填商户名/卡号） */
    private static boolean isRicher(Txn incoming, Txn existing) {
        if (existing.merchant == null || existing.merchant.length() == 0) {
            if (incoming.merchant != null && incoming.merchant.length() > 0) return true;
        }
        if (existing.account == null || existing.account.length() == 0) {
            if (incoming.account != null && incoming.account.length() > 0) return true;
        }
        return incoming.confidence > existing.confidence;
    }

    /** 回填更全的字段，但不覆盖已有的人工备注 */
    public static void enrich(Txn existing, Txn incoming, TxnStore store) {
        boolean changed = false;
        if ((existing.merchant == null || existing.merchant.length() == 0)
                && incoming.merchant != null && incoming.merchant.length() > 0) {
            existing.merchant = incoming.merchant;
            changed = true;
        }
        if ((existing.account == null || existing.account.length() == 0)
                && incoming.account != null && incoming.account.length() > 0) {
            existing.account = incoming.account;
            changed = true;
        }
        if (incoming.confidence > existing.confidence) {
            existing.confidence = incoming.confidence;
            changed = true;
        }
        if (changed) {
            existing.updatedAt = System.currentTimeMillis();
            store.updateTxn(existing);
        }
    }

    /** 生成去重指纹（存库便于排查） */
    public static String buildKey(Txn t) {
        long bucket = t.occurredAt / CROSS_WINDOW;   // 按去重窗口分档
        return t.direction + "|" + t.amountCents + "|" + bucket + "|" + norm(t.merchant);
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Lexicon.normalize(s).toLowerCase().replace(" ", "");
    }
}
