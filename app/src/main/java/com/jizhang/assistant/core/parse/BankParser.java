package com.jizhang.assistant.core.parse;

import com.jizhang.assistant.core.model.Txn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 银行交易通知解析器 —— 主力通道（招商银行 App 推送通知）。
 *
 * 招行 App 的账户变动通知与短信文案基本同源，常见形态：
 *   账户变动通知 | 您尾号1234的账户于08月29日14:30支付人民币100.00元
 *   招商银行     | 您账户1234于08月29日14:30在支付宝消费人民币100.00元，余额12345.67元
 *   招商银行     | 您尾号1234的储蓄卡08月29日网上支付人民币100.00元
 *   招商银行     | 您账户1234于08月29日转账汇款人民币100.00元
 *   招商银行     | 您账户1234于08月29日收入人民币200.00元
 *   招商银行     | 您账户1234于08月29日退款人民币100.00元
 *   招商银行     | 您尾号1234的账户于08月29日14:30向XX商户支付100.00元
 *
 * 同时兼容其它银行短信（仅作为次要通道）。
 */
public final class BankParser {

    private BankParser() {}

    /**
     * 商户提取：优先级从高到低。
     *
     * 之所以把「括号里的名字」放在第一位，是因为招行新模板长这样：
     *   您账户8888于09月16日10:50在【星巴克】发生快捷支付扣款，人民币35.00
     * 若用「在…支付」去截，会把「发生快捷」一起吞进商户名（曾经就踩过这个坑）。
     */
    private static final Pattern[] MERCHANT_PATTERNS = {
            // 在【星巴克】… / 向（某某）… / 给「某某」…
            Pattern.compile("(?:在|向|给)\\s*[【（(「『]\\s*([^】）)」』]{1,40}?)\\s*[】）)」』]"),
            // 商户名称：某某
            Pattern.compile("(?:商户名称|商户|特约商户|收款方|对方户名|收款人)\\s*[:：]\\s*([^,，。;；|]{1,30})"),
            // 在 某某 消费 / 支付（排除括号，避免把整段括起来的内容当名字）
            Pattern.compile("在\\s*([^,，。;；|【】（）()]{2,24}?)\\s*(?:消费|支付|购买|刷卡|扣款)"),
            // 向 某某 付款 / 转账
            Pattern.compile("(?:向|付给|转给|转至|汇给)\\s*([^,，。;；|【】（）()]{2,24}?)\\s*(?:付款|支付|转账|汇款|消费)"),
            // 交易类型 / 摘要 / 用途 / 商品
            Pattern.compile("(?:交易类型|摘要|用途|商品)\\s*[:：]\\s*([^,，。;；|]{1,24})")
    };

    /** 商户名里不该出现的描述性词语，遇到就从这里截断 */
    private static final String[] MERCHANT_NOISE = {
            "发生", "快捷", "消费", "支付", "付款", "扣款", "购买", "刷卡",
            "转账", "汇款", "交易", "支出", "收入"
    };

    /** 账号尾号提取 */
    private static final Pattern[] ACCOUNT_PATTERNS = {
            Pattern.compile("(?:尾号|卡号|账户|账号|卡末|后四位)\\s*[:：]?\\s*(\\d{4,})"),
            Pattern.compile("\\*{2,}\\s*(\\d{4,})")
    };

    /** 银行 App 推送常见的通知标题，不应被当成商户名 */
    private static final String[] COMMON_TITLES = {
            "账户变动通知", "账户变动提醒", "交易提醒", "收支提醒", "招商银行", "消费提醒",
            "动账通知", "资金变动", "交易通知", "余额变动", "出入账通知", "信用卡"
    };

    public static ParseResult parse(String sender, String pkg, String text, long receivedAt) {
        ParseResult r = new ParseResult();
        r.direction = Txn.DIR_OUT;

        if (text == null || text.trim().length() == 0) {
            return ParseResult.fail("通知内容为空");
        }
        String t = Lexicon.normalize(text);
        if (t.length() < 4) {
            return ParseResult.fail("通知内容过短");
        }

        Long abs = AmountParser.parseAbsCents(t);
        if (abs == null || abs <= 0) {
            return ParseResult.fail("未识别到金额");
        }
        r.amountCents = abs;

        // 退款/退回判定（核心特色）
        String refundHit = Lexicon.firstHit(t, Lexicon.REFUND_WORDS);
        r.refund = refundHit != null;

        // 方向判定
        String outHit = Lexicon.firstHit(t, Lexicon.OUT_WORDS);
        String inHit = Lexicon.firstHit(t, Lexicon.IN_WORDS);
        if (r.refund) {
            r.direction = Txn.DIR_IN;
        } else if (outHit != null && inHit == null) {
            r.direction = Txn.DIR_OUT;
        } else if (inHit != null && outHit == null) {
            r.direction = Txn.DIR_IN;
        } else if (outHit != null && inHit != null) {
            r.direction = t.indexOf(outHit) <= t.indexOf(inHit) ? Txn.DIR_OUT : Txn.DIR_IN;
        } else {
            Long signed = AmountParser.parseSignedCents(t);
            if (signed != null && signed < 0) {
                r.direction = Txn.DIR_OUT;
            } else {
                return ParseResult.fail("无法判定收支方向");
            }
        }

        r.merchant = extractMerchant(t);
        r.account = extractAccount(t);
        r.occurredAt = TimeExtractor.extract(t, receivedAt);
        r.category = null; // 交由 CategoryGuesser

        // 置信度
        int conf = 55;
        if (outHit != null || inHit != null || r.refund) conf += 20;
        if (isBankSource(sender, pkg)) conf += 15;
        if (r.account != null) conf += 5;
        if (r.merchant != null) conf += 5;
        if (r.occurredAt > 0) conf += 5;
        r.confidence = Math.min(100, conf);
        r.success = true;
        r.reason = "银行通知解析" + (r.refund ? "(退款)" : "")
                + (refundHit != null ? " 命中:" + refundHit : "");
        return r;
    }

    /** 发件人或包名是否属于银行渠道 */
    public static boolean isBankSource(String sender, String pkg) {
        if (pkg != null) {
            for (String b : Lexicon.BANK_PKGS) {
                if (pkg.equals(b)) return true;
            }
            // 模糊匹配：银行 App 包名常随版本变化，只认精确名单会大量漏记
            String lower = pkg.toLowerCase();
            for (String h : Lexicon.BANK_PKG_HINTS) {
                if (lower.contains(h)) return true;
            }
        }
        return Lexicon.isBankSender(sender);
    }

    /** 该标题是否为通用通知标题（非商户名） */
    public static boolean isCommonTitle(String title) {
        if (title == null) return false;
        String t = Lexicon.normalize(title);
        for (String s : COMMON_TITLES) {
            if (t.contains(s)) return true;
        }
        return false;
    }

    static String extractMerchant(String text) {
        for (Pattern p : MERCHANT_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String v = clean(m.group(1));
                if (v.length() > 0) return v;
            }
        }
        return null;
    }

    static String extractAccount(String text) {
        for (Pattern p : ACCOUNT_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String digits = m.group(1).replaceAll("\\D", "");
                if (digits.length() >= 4) {
                    return digits.substring(digits.length() - 4);
                }
            }
        }
        return null;
    }

    private static String clean(String s) {
        if (s == null) return "";
        String v = s.trim();
        for (String t : COMMON_TITLES) {
            if (v.equals(t)) return "";
        }
        // 去掉可能残留的成对括号
        v = v.replaceAll("^[【（(「『\\[]+", "").replaceAll("[】）)」』\\]]+$", "").trim();
        if (v.length() == 0) return "";
        // 从描述性词语处截断：「星巴克发生快捷」-> 「星巴克」
        int cut = -1;
        for (String n : MERCHANT_NOISE) {
            int i = v.indexOf(n);
            if (i > 0 && (cut < 0 || i < cut)) cut = i;
        }
        if (cut > 0) v = v.substring(0, cut).trim();
        return v;
    }
}
