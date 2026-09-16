package com.jizhang.assistant.core.parse;

import com.jizhang.assistant.core.model.RawEvent;

/**
 * 统一分发：根据来源把原始事件交给对应的解析器。
 *
 * 通道优先级：银行 App 推送通知（招行，主力） > 微信/支付宝通知 > 银行短信（次要）
 */
public final class EventParser {

    private EventParser() {}

    public static ParseResult parse(RawEvent e) {
        if (e == null) return ParseResult.fail("事件为空");
        String text = e.fullText();

        if (e.kind == RawEvent.KIND_SMS) {
            return BankParser.parse(e.sender, null, text, e.receivedAt);
        }

        // 通知通道
        if (BankParser.isBankSource(e.sender, e.pkg)) {
            return BankParser.parse(e.sender, e.pkg, text, e.receivedAt);
        }
        if (isPayPkg(e.pkg)) {
            return NotificationParser.parse(e.pkg, e.title, e.body, e.receivedAt);
        }

        // 未知来源：两种解析都试，置信度高者胜
        ParseResult a = BankParser.parse(e.sender, e.pkg, text, e.receivedAt);
        ParseResult b = NotificationParser.parse(e.pkg, e.title, e.body, e.receivedAt);
        if (a.success && b.success) {
            return a.confidence >= b.confidence ? a : b;
        }
        if (a.success) return a;
        if (b.success) return b;
        return ParseResult.fail("两个解析器都未识别：" + a.reason + " / " + b.reason);
    }

    /**
     * 这段文字看起来像不像一笔银行交易？
     *
     * 用途：当通知的包名不在已知名单里时，靠文本内容兜底判断，
     *       避免因为银行 App 改了包名就把交易全部漏掉。
     *
     * 判定条件（三条都要满足，避免误抓聊天消息）：
     *   1. 能解析出金额
     *   2. 含银行特征词（账户 / 尾号 / 人民币 / 余额 …）
     *   3. 含交易行为词（支付 / 扣款 / 收入 …）
     */
    public static boolean looksLikeBankTransaction(String text) {
        if (text == null) return false;
        String s = Lexicon.normalize(text);
        if (s.length() < 8) return false;
        if (AmountParser.parseAbsCents(s) == null) return false;
        return Lexicon.containsAny(s, Lexicon.BANK_TEXT_MARKERS)
                && Lexicon.containsAny(s, Lexicon.TXN_TEXT_MARKERS);
    }

    /** 包名是否属于我们关心的来源：精确名单 → 模糊特征 → 文本兜底 */
    public static boolean isWatchedPkg(String pkg, String text) {
        if (pkg == null) return false;
        for (String p : Lexicon.BANK_PKGS) if (p.equals(pkg)) return true;
        for (String p : Lexicon.PAY_PKGS) if (p.equals(pkg)) return true;
        String lower = pkg.toLowerCase();
        for (String h : Lexicon.BANK_PKG_HINTS) if (lower.contains(h)) return true;
        for (String h : Lexicon.PAY_PKG_HINTS) if (lower.contains(h)) return true;
        return looksLikeBankTransaction(text);
    }

    public static boolean isPayPkg(String pkg) {
        if (pkg == null) return false;
        for (String p : Lexicon.PAY_PKGS) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }

    /** 是否是我们关心的来源（银行 App / 微信 / 支付宝 / 银行短信） */
    public static boolean isWatched(RawEvent e) {
        if (e == null) return false;
        if (e.kind == RawEvent.KIND_SMS) {
            return Lexicon.isBankSender(e.sender);
        }
        return BankParser.isBankSource(e.sender, e.pkg) || isPayPkg(e.pkg);
    }
}
