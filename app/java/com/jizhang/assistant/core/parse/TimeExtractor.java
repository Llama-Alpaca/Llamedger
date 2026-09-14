package com.jizhang.assistant.core.parse;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从短信/通知文本里提取交易时间。
 * 银行短信通常不带年份，需要根据接收时间推断（跨年时回退一年）。
 */
public final class TimeExtractor {

    private TimeExtractor() {}

    private static final Pattern P_FULL = Pattern.compile(
            "(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})日?\\s*(\\d{1,2})[:：](\\d{2})?");
    private static final Pattern P_FULL_NODATE_TIME = Pattern.compile(
            "(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})日?");
    private static final Pattern P_MD_HM = Pattern.compile(
            "(?<!\\d)(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})[:：](\\d{2})?(?!\\d)");
    private static final Pattern P_MD = Pattern.compile(
            "(?<!\\d)(\\d{1,2})月(\\d{1,2})日(?!\\d)");
    private static final Pattern P_DASH = Pattern.compile(
            "(?<!\\d)(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2})[:：](\\d{2})(?!\\d)");

    /** @return epoch millis，识别不到返回 0 */
    public static long extract(String text, long fallback) {
        if (text == null) return 0;
        String s = Lexicon.normalize(text);
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(fallback > 0 ? fallback : System.currentTimeMillis());

        Matcher m = P_FULL.matcher(s);
        if (m.find()) {
            return build(base, i(m, 1), i(m, 2), i(m, 3), i(m, 4), i(m, 5));
        }
        m = P_FULL_NODATE_TIME.matcher(s);
        if (m.find()) {
            return build(base, i(m, 1), i(m, 2), i(m, 3), 0, 0);
        }
        m = P_MD_HM.matcher(s);
        if (m.find()) {
            return buildInferYear(base, i(m, 1), i(m, 2), i(m, 3), i(m, 4));
        }
        m = P_DASH.matcher(s);
        if (m.find()) {
            return buildInferYear(base, i(m, 1), i(m, 2), i(m, 3), i(m, 4));
        }
        m = P_MD.matcher(s);
        if (m.find()) {
            return buildInferYear(base, i(m, 1), i(m, 2), 0, 0);
        }
        return 0;
    }

    private static int i(Matcher m, int g) {
        try {
            String v = m.group(g);
            return v == null || v.length() == 0 ? 0 : Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long build(Calendar base, int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(base.getTimeInMillis());
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, Math.max(0, mo - 1));
        c.set(Calendar.DAY_OF_MONTH, Math.max(1, d));
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, mi);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 没有年份时：若推断出的时间比接收时间晚太多，说明是去年的 */
    private static long buildInferYear(Calendar base, int mo, int d, int h, int mi) {
        int year = base.get(Calendar.YEAR);
        long t = build(base, year, mo, d, h, mi);
        if (t > base.getTimeInMillis() + 2L * 24 * 3600 * 1000) {
            t = build(base, year - 1, mo, d, h, mi);
        }
        return t;
    }
}
