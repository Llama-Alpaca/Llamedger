package com.jizhang.assistant.util;

import com.jizhang.assistant.core.parse.AmountParser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 格式化工具 */
public final class Fmt {

    private Fmt() {}

    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private static final SimpleDateFormat DATETIME =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private static final SimpleDateFormat MD = new SimpleDateFormat("MM-dd", Locale.CHINA);
    private static final SimpleDateFormat MONTH = new SimpleDateFormat("yyyy年MM月", Locale.CHINA);

    public static String money(long cents) {
        return "¥" + AmountParser.yuan(cents);
    }

    /** 带符号金额：支出显示 -，收入显示 + */
    public static String signed(int direction, long cents) {
        return (direction == 0 ? "-" : "+") + "¥" + AmountParser.yuan(cents);
    }

    public static String date(long t) {
        return DATE.format(new Date(t));
    }

    public static String time(long t) {
        return TIME.format(new Date(t));
    }

    public static String dateTime(long t) {
        return DATETIME.format(new Date(t));
    }

    public static String monthDay(long t) {
        return MD.format(new Date(t));
    }

    public static String month(long t) {
        return MONTH.format(new Date(t));
    }

    public static String hm(long t) {
        return TIME.format(new Date(t));
    }

    public static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfDay(long t) {
        return startOfDay(t) + 24L * 3600 * 1000 - 1;
    }

    public static long startOfMonth(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfMonth(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }

    public static long addMonths(long t, int delta) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.add(Calendar.MONTH, delta);
        return c.getTimeInMillis();
    }

    /** 相对时间描述：刚刚 / 5分钟前 / 3小时前 / 2天前 */
    public static String relative(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < 60_000L) return "刚刚";
        if (d < 3600_000L) return (d / 60_000L) + "分钟前";
        if (d < 24 * 3600_000L) return (d / 3600_000L) + "小时前";
        if (d < 30L * 24 * 3600_000L) return (d / (24 * 3600_000L)) + "天前";
        return date(t);
    }
}
