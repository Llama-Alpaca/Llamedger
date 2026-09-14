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

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "通知监听已连接");
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
        if (!isWatched(pkg)) return;

        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle ex = n.extras;
        if (ex == null) return;

        String title = text(ex, Notification.EXTRA_TITLE);
        String body = pickBody(ex);
        if ((title == null || title.length() == 0) && (body == null || body.length() == 0)) {
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

    /** 是否为我们关心的来源 */
    private static boolean isWatched(String pkg) {
        for (String p : Lexicon.BANK_PKGS) if (p.equals(pkg)) return true;
        for (String p : Lexicon.PAY_PKGS) if (p.equals(pkg)) return true;
        return false;
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
