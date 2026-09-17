package com.jizhang.assistant.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jizhang.assistant.R;
import com.jizhang.assistant.ui.MainActivity;

/** 通知工具：负责通知渠道与「记账结果」提醒。 */
public final class Notifier {

    private Notifier() {}

    /** 记账结果通知（识别到消费/退款时提示） */
    public static final String CH_RECORD = "jz_record";
    /** 后台常驻通知 */
    public static final String CH_KEEP = "jz_keepalive";

    public static final int ID_KEEPALIVE = 1001;
    private static final int ID_RECORD = 2000;

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel record = new NotificationChannel(
                CH_RECORD, "记账提醒", NotificationManager.IMPORTANCE_DEFAULT);
        record.setDescription("自动识别到消费、退款时提示");
        nm.createNotificationChannel(record);

        NotificationChannel keep = new NotificationChannel(
                CH_KEEP, "后台运行", NotificationManager.IMPORTANCE_MIN);
        keep.setDescription("保持自动记账在后台运行");
        keep.setShowBadge(false);
        nm.createNotificationChannel(keep);
    }

    private static PendingIntent contentIntent(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, 0, i, flags);
    }

    /** 弹一条记账结果 */
    public static void record(Context c, String title, String text) {
        ensureChannels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(c, CH_RECORD);
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(R.drawable.ic_stat_jz)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent(c))
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setColor(0xFF2E7D5B);
        }
        int id = ID_RECORD + (int) (System.currentTimeMillis() % 1000);
        nm.notify(id, b.build());
    }

    /** 常驻通知（前台服务用） */
    public static Notification keepAlive(Context c, String text) {
        ensureChannels(c);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(c, CH_KEEP);
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(R.drawable.ic_stat_jz)
                .setContentTitle("自动记账运行中")
                .setContentText(text)
                .setContentIntent(contentIntent(c))
                .setOngoing(true);
        return b.build();
    }
}
