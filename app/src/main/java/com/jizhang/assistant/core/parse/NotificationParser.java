package com.jizhang.assistant.core.parse;

import com.jizhang.assistant.core.model.Txn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信 / 支付宝 / 云闪付 通知解析器。
 *
 * 微信典型通知： 标题「微信支付」 正文「已支付¥100.00」
 * 支付宝典型通知：标题「支付宝」   正文「支付成功 100.00元」
 * 退款通知：     正文「退款到账 ￥100.00」「转账已退回 ￥100.00」
 */
public final class NotificationParser {

    private NotificationParser() {}

    private static final Pattern[] MERCHANT_PATTERNS = {
            // 「美团外卖 已支付¥25.00」 -> 美团外卖
            Pattern.compile("^([^\\d¥￥]{2,20}?)\\s*(?:已支付|支付成功|付款成功|消费|扣款)"),
            Pattern.compile("(?:在|向)\\s*([^,，。;；\\d]{2,20}?)\\s*(?:消费|支付|付款|购买)"),
            Pattern.compile("(?:商户|收款方|付款给|收款人)\\s*[:：]?\\s*([^,，。;；\\d]{1,20})"),
            Pattern.compile("(?:收款方|商户名称)\\s*[:：]?\\s*([^,，。;；]{1,20})")
    };

    public static ParseResult parse(String pkg, String title, String body, long receivedAt) {
        ParseResult r = new ParseResult();
        r.direction = Txn.DIR_OUT;

        String t = Lexicon.normalize(title == null ? "" : title);
        String b = Lexicon.normalize(body == null ? "" : body);
        String all = (t + " " + b).trim();
        if (all.length() == 0) {
            return ParseResult.fail("通知内容为空");
        }

        Long abs = AmountParser.parseAbsCents(all);
        if (abs == null || abs <= 0) {
            return ParseResult.fail("未识别到金额");
        }
        r.amountCents = abs;

        String refundHit = Lexicon.firstHit(all, Lexicon.REFUND_WORDS);
        r.refund = refundHit != null;

        String outHit = Lexicon.firstHit(all, Lexicon.OUT_WORDS);
        String inHit = Lexicon.firstHit(all, Lexicon.IN_WORDS);

        if (r.refund) {
            r.direction = Txn.DIR_IN;
        } else if (outHit != null && inHit == null) {
            r.direction = Txn.DIR_OUT;
        } else if (inHit != null && outHit == null) {
            r.direction = Txn.DIR_IN;
        } else if (outHit != null && inHit != null) {
            r.direction = all.indexOf(outHit) <= all.indexOf(inHit) ? Txn.DIR_OUT : Txn.DIR_IN;
        } else {
            return ParseResult.fail("无法判定收支方向");
        }

        r.merchant = extractMerchant(t, b);
        r.account = Lexicon.pkgLabel(pkg);   // 支付渠道：微信 / 支付宝
        r.occurredAt = TimeExtractor.extract(all, receivedAt);
        r.category = null;

        int conf = 55;
        if (outHit != null || inHit != null || r.refund) conf += 20;
        if (r.merchant != null) conf += 10;
        if (r.occurredAt > 0) conf += 5;
        r.confidence = Math.min(100, conf);
        r.success = true;
        r.reason = "通知解析" + (r.refund ? "(退款)" : "")
                + (refundHit != null ? " 命中:" + refundHit : "");
        return r;
    }

    private static String extractMerchant(String title, String body) {
        // 标题通常就是商户名（微信支付的商户通知），先试正文再试标题
        String m = tryPatterns(body);
        if (m != null) return m;
        m = tryPatterns(title);
        if (m != null) return m;
        // 标题若是渠道名则丢弃
        if (title.length() >= 2 && title.length() <= 20
                && !title.contains("支付") && !title.equals("微信")
                && !title.equals("支付宝") && !title.contains("退款")) {
            return title;
        }
        return null;
    }

    private static String tryPatterns(String s) {
        if (s == null || s.length() == 0) return null;
        for (Pattern p : MERCHANT_PATTERNS) {
            Matcher mm = p.matcher(s);
            if (mm.find()) {
                String v = mm.group(1).trim();
                if (v.length() > 0) return v;
            }
        }
        return null;
    }
}
