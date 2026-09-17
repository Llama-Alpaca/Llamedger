package com.jizhang.assistant.ui;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/** 界面小工具 */
public final class Ui {

    private Ui() {}

    public static void toast(Context c, String msg) {
        if (c == null || msg == null) return;
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 跳转到本应用的通知使用权设置页 */
    public static void openNotificationAccess(Context c) {
        try {
            Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Throwable t2) {
                toast(c, "无法打开系统设置，请手动进入");
            }
        }
    }

    /** 跳转到本应用的详情设置页（自启动、权限等） */
    public static void openAppSettings(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            toast(c, "无法打开应用设置");
        }
    }

    /** 申请加入电池优化白名单 */
    public static void requestIgnoreBattery(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(android.net.Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            toast(c, "请手动在 设置→电池 中允许后台运行");
        }
    }

    public static boolean isIgnoringBattery(Context c) {
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }
}
