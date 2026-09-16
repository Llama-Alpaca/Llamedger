package com.jizhang.assistant.capture;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.parse.BankParser;
import com.jizhang.assistant.core.parse.EventParser;
import com.jizhang.assistant.core.parse.Lexicon;

/**
 * 通知监听服务 —— 自动记账的主力取数通道。
 *
 * 用户授予「通知使用权」后，系统会把所有应用的通知回调到这里。
 * 我们只关心：招商银行 App（主力）、微信、支付宝、云闪付。
 */
public class NotifyListenerService extends NotificationListenerService {

    private static final String TAG = "JZNotify";

    /** 监听服务是否处于连接状态（诊断界面用） */
    public static volatile boolean connected = false;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        Log.i(TAG, "通知监听已连接");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected = false;
        Log.w(TAG, "通知监听已断开");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        try {
            handle(sbn);
        } catch (Throwable t) {
            Log.e(TAG, "处理通知异常", t);
        }
    }

    private void handle(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null) return;
        // 忽略自己发的通知
        if (pkg.equals(getPackageName())) return;

        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle ex = n.extras;
        if (ex == null) return;

        String title = text(ex, Notification.EXTRA_TITLE);
        String body = pickBody(ex);
        if ((title == null || title.length() == 0) && (body == null || body.length() == 0)) {
            return;
        }

        // 判断是否关心这条通知：精确名单 -> 模糊包名特征 -> 文本兜底。
        // 以前这里不匹配就直接 return 且不留记录，一旦银行 App 改了包名，
        // 就会表现为「消费完全没有记录」，而且完全无从排查。
        boolean watched = EventParser.isWatchedPkg(pkg, title + " " + body);
        Monitor.recordSource(this, pkg, title, watched);
        if (!watched) {
            Log.i(TAG, "未识别的通知来源，已忽略: " + pkg);
            return;
        }

        RawEvent e = new RawEvent();
        e.kind = RawEvent.KIND_NOTIFICATION;
        e.pkg = pkg;
        e.title = title;
        e.body = body;
        e.receivedAt = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        e.dedupeKey = sbn.getKey() + "#" + sbn.getPostTime();

        Log.i(TAG, "捕获通知 [" + pkg + "] " + title + " | " + body);
        Monitor.handleAsync(this, e);
    }

    /** 正文优先级：大文本 > 多行文本 > 普通文本 > 副文本 */
    private String pickBody(Bundle ex) {
        String big = text(ex, Notification.EXTRA_BIG_TEXT);
        if (big != null && big.trim().length() > 0) return big;

        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence cs : lines) {
                if (cs == null) continue;
                String s = cs.toString().trim();
                if (s.length() == 0) continue;
                // 微信的 InboxStyle 首行常是重复的应用名，去掉
                if (sb.length() == 0 && (s.contains("微信") || s.contains("支付宝"))) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(s);
            }
            if (sb.length() > 0) return sb.toString();
        }

        String txt = text(ex, Notification.EXTRA_TEXT);
        if (txt != null && txt.trim().length() > 0) return txt;
        return text(ex, Notification.EXTRA_SUB_TEXT);
    }

    private static String text(Bundle ex, String key) {
        CharSequence cs = ex.getCharSequence(key);
        return cs == null ? null : cs.toString();
    }

    /**
     * 请求系统重新绑定监听服务。
     *
     * 适用场景：通知使用权明明是开着的，但服务处于「未连接」状态
     *（系统回收进程、ROM 更新、应用重装后都可能出现）。
     * 这是官方 API，比让用户手动去系统设置里关掉再打开更省事。
     *
     * @return 是否成功发出请求
     */
    public static boolean requestReconnect(android.content.Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;   // API 24 才有
        try {
            requestRebind(new android.content.ComponentName(c, NotifyListenerService.class));
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "请求重新绑定失败", t);
            return false;
        }
    }

    /**
     * 尝试唤醒监听服务：若已授权但未连接，就请求系统重新绑定。
     * 在打开应用时调用，起到自愈作用。
     */
    public static void ensureConnected(android.content.Context c) {
        if (connected) return;
        if (!isEnabled(c)) return;
        requestReconnect(c);
    }

    /** 供界面显示：当前是否已获得通知使用权 */
    public static boolean isEnabled(android.content.Context c) {
        try {
            String flat = android.provider.Settings.Secure.getString(
                    c.getContentResolver(), "enabled_notification_listeners");
            if (flat == null) return false;
            return flat.contains(c.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }
}
