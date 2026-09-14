package com.jizhang.assistant.capture;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Prefs;

/**
 * 后台常驻服务。
 *
 * 只做一件事：用前台通知把进程「钉住」，让通知监听服务尽可能长时间存活，
 * 从而在微信/支付宝/招行推送到达时第一时间记账。
 */
public class KeepAliveService extends Service {

    private static final String TAG = "JZKeepAlive";
    public static final String ACTION_START = "com.jizhang.assistant.START";
    public static final String ACTION_STOP = "com.jizhang.assistant.STOP";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }

        SqliteStore store = com.jizhang.assistant.App.db();
        String text = store != null && Prefs.monitorEnabled(store)
                ? "正在监听招行、微信、支付宝的支付通知"
                : "自动记账已暂停";

        try {
            startForeground(Notifier.ID_KEEPALIVE, Notifier.keepAlive(this, text));
        } catch (Throwable t) {
            Log.e(TAG, "启动前台服务失败", t);
        }
        // 被杀后自动重启
        return START_STICKY;
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(true);
        } else {
            stopForeground(true);
        }
    }

    /** 启动/停止常驻服务 */
    public static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            i.setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable t) {
            Log.e(TAG, "启动服务失败", t);
        }
    }

    public static void stop(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            i.setAction(ACTION_STOP);
            c.startService(i);
        } catch (Throwable t) {
            Log.e(TAG, "停止服务失败", t);
        }
    }
}
