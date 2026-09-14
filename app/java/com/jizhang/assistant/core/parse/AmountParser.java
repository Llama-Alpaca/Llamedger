package com.jizhang.assistant.core.parse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 金额解析器。
 *
 * 银行/支付通知里除了交易金额，还常混着「余额」「可用额度」「2小时内到账」这些数字。
 * 早期版本「第一个匹配就返回」，结果把「预计2小时内到账」里的 2 当成了金额，
 * 记出一笔 2 元支出 —— 这是必须避免的严重错误。
 *
 * 现在的做法：
 *   1. 收集所有候选金额（不再命中即返回）
 *   2. 逐条剔除「假金额」：余额/额度后面的数字、时间或数量单位前面的数字
 *   3. 按上下文可信度打分：带货币符号 > 后跟「元」> 带小数 > 带千分位
 *   4. 取得分最高者，同分取出现最早者
 */
public final class AmountParser {

    private AmountParser() {}

    /** 方向词：金额通常紧跟在它后面 */
    private static final String DIR_WORDS =
            "支出|消费|支付|付款|扣款|扣费|转出|取出|取现|提现|收入|入账|转入|存入"
                    + "|退款|退回|返还|退还|冲正|汇款|代扣|缴费|收款|到账|实收|实付";

    private static final String CUR = "人民币|RMB|rmb|¥|￥|CNY";
    private static final String NUM = "(\\d[\\d,]*(?:\\.\\d{1,2})?)";

    /**
     * 方向词锚定。注意这里只允许「少量连接词」，不再允许跨越任意字符 ——
     * 否则会从「支付」一路跳到后面的无关数字上。
     */
    private static final Pattern P_ANCHORED = Pattern.compile(
            "(?:" + DIR_WORDS + ")\\s*(?:金额|为|是|共计)?\\s*[:：]?\\s*"
                    + "(?:" + CUR + ")?\\s*" + NUM);

    /** 货币符号在前 / 在后 */
    private static final Pattern P_CUR_FIRST = Pattern.compile(
            "(?:" + CUR + ")\\s*" + NUM);

    private static final Pattern P_CUR_LAST = Pattern.compile(NUM + "\\s*元");

    /** 带标签的金额 */
    private static final Pattern P_LABELED = Pattern.compile(
            "(?:交易金额|交易额|金额|amt|amount)\\s*(?:为|是)?\\s*[:：]?\\s*"
                    + "(?:" + CUR + ")?\\s*" + NUM);

    /** 这些词后面的数字是余额、不是交易金额 */
    private static final String[] EXCLUDE_PREFIX = {
            "余额", "可用", "额度", "剩余", "欠款", "积分", "账单余额", "账户余额"
    };

    /** 数字后面紧跟这些，说明它是时间/数量，不是钱 */
    private static final String[] QTY_UNITS = {
            "小时", "分钟", "秒钟", "个工作日", "工作日", "个自然日", "个月",
            "天后", "日内", "天内", "秒后", "条通知", "条消息", "条新消息"
    };

    /**
     * 数量单位。金额后面绝不会直接跟这些字，跟了就说明这个数字是数量。
     * 「条」尤其重要：通知分组会写成「[2条]微信支付: …」，
     * 早期版本正是把这个 2 当成了 2 元。
     */
    private static final String QTY_CHARS =
            "个天月年次笔折期秒日周时条位张件份瓶只盒袋包箱台部本套双对组名人岁米克斤升度步层成倍种类";

    /** 数字被括号包住时基本可以断定是计数/标号，不是金额，如 [2条]、[3]、【2】 */
    private static final String OPEN_BRACKETS = "[［(（【";
    private static final String CLOSE_BRACKETS = "]］)）】";

    private static class Cand {
        long cents;
        int score;
        int pos;
    }

    /**
     * @return 金额（分，恒为正），未识别返回 null
     */
    public static Long parseSignedCents(String text) {
        if (text == null) return null;
        String s = Lexicon.normalize(text);
        if (s.length() == 0) return null;

        List<Cand> cands = new ArrayList<Cand>();
        collect(cands, s, P_ANCHORED, 12);
        collect(cands, s, P_CUR_FIRST, 12);
        collect(cands, s, P_LABELED, 10);
        collect(cands, s, P_CUR_LAST, 8);
        if (cands.isEmpty()) return null;

        Cand best = null;
        for (Cand c : cands) {
            if (best == null) {
                best = c;
            } else if (c.score > best.score) {
                best = c;
            } else if (c.score == best.score && c.pos < best.pos) {
                best = c;
            }
        }
        return best == null ? null : best.cents;
    }

    public static Long parseAbsCents(String text) {
        Long v = parseSignedCents(text);
        return v == null ? null : Math.abs(v);
    }

    private static void collect(List<Cand> out, String s, Pattern p, int base) {
        Matcher m = p.matcher(s);
        while (m.find()) {
            int g = m.groupCount() >= 1 ? 1 : 0;
            String raw = m.group(g);
            int start = m.start(g);
            int end = m.end(g);
            if (raw == null || raw.length() == 0) continue;
            if (isExcluded(s, start)) continue;
            if (looksLikeQuantity(s, end)) continue;
            if (looksLikeBracketLabel(s, start, end)) continue;

            Long cents = toCents(raw);
            if (cents == null || cents <= 0) continue;

            int score = base;
            if (raw.indexOf('.') >= 0) score += 5;      // 有小数更像金额
            if (raw.indexOf(',') >= 0) score += 2;      // 千分位更像金额
            if (end < s.length() && s.charAt(end) == '元') score += 6;

            Cand c = new Cand();
            c.cents = cents;
            c.score = score;
            c.pos = start;
            out.add(c);
        }
    }

    private static boolean isExcluded(String s, int matchStart) {
        int from = Math.max(0, matchStart - 10);
        String prefix = s.substring(from, matchStart);
        for (String e : EXCLUDE_PREFIX) {
            if (prefix.contains(e)) return true;
        }
        return false;
    }

    /** 数字后面紧跟时间/数量单位 → 不是金额（如「预计2小时内到账」「[2条]」） */
    private static boolean looksLikeQuantity(String s, int end) {
        int to = Math.min(s.length(), end + 8);
        String tail = s.substring(end, to);
        for (String u : QTY_UNITS) {
            if (tail.startsWith(u)) return true;
        }
        if (tail.length() > 0 && QTY_CHARS.indexOf(tail.charAt(0)) >= 0) return true;
        return false;
    }

    /** 数字被括号整个包住 → 是计数/标号，不是金额 */
    private static boolean looksLikeBracketLabel(String s, int start, int end) {
        if (start <= 0 || end >= s.length()) return false;
        char before = s.charAt(start - 1);
        char after = s.charAt(end);
        return OPEN_BRACKETS.indexOf(before) >= 0 && CLOSE_BRACKETS.indexOf(after) >= 0;
    }

    private static Long toCents(String raw) {
        if (raw == null) return null;
        String s = raw.replace(",", "").trim();
        if (s.length() == 0) return null;
        try {
            return new BigDecimal(s).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    /** 提取卡号尾号，如「尾号1234」「账户1234」「卡号****1234」 */
    public static String parseTailNumber(String text) {
        if (text == null) return null;
        String s = Lexicon.normalize(text);
        Matcher m = Pattern.compile("(?:尾号|卡号|账户|账号|卡末|后四位)\\D{0,4}(\\d{4,})").matcher(s);
        if (m.find()) {
            String d = m.group(1);
            return d.length() >= 4 ? d.substring(d.length() - 4) : d;
        }
        Matcher m2 = Pattern.compile("\\*{2,}(\\d{4,})").matcher(s);
        if (m2.find()) {
            String d = m2.group(1);
            return d.length() >= 4 ? d.substring(d.length() - 4) : d;
        }
        return null;
    }

    /** 格式化分 -> "100.00" */
    public static String yuan(long cents) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
