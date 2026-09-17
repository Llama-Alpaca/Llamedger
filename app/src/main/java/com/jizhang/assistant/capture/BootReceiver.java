package com.jizhang.assistant.capture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jizhang.assistant.App;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Prefs;

/** 开机自启：把常驻服务重新拉起来。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            return;
        }
        SqliteStore store = App.db();
        if (store == null) return;
        if (!Prefs.autoStart(store)) return;
        KeepAliveService.start(context);
    }
}
