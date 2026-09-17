package com.jizhang.assistant.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.jizhang.assistant.App;
import com.jizhang.assistant.R;
import com.jizhang.assistant.capture.KeepAliveService;
import com.jizhang.assistant.capture.NotifyListenerService;
import com.jizhang.assistant.db.SqliteStore;
import com.jizhang.assistant.util.Prefs;

/** 权限引导页 */
public class PermissionGuideActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        findViewById(R.id.btnNotifyAccess).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.openNotificationAccess(PermissionGuideActivity.this); }
        });
        findViewById(R.id.btnBattery).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.requestIgnoreBattery(PermissionGuideActivity.this); }
        });
        findViewById(R.id.btnAppSettings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.openAppSettings(PermissionGuideActivity.this); }
        });
        findViewById(R.id.done).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnSms).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (android.os.Build.VERSION.SDK_INT >= 23
                        && checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            android.Manifest.permission.RECEIVE_SMS}, 200);
                } else {
                    Ui.toast(PermissionGuideActivity.this, "短信权限已开启");
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 200) {
            boolean ok = results.length > 0
                    && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Ui.toast(this, ok ? "短信权限已开启" : "未授予短信权限");
            onResume();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SqliteStore store = App.db();
        boolean access = NotifyListenerService.isEnabled(this);
        TextView s1 = (TextView) findViewById(R.id.state1);
        if (access) {
            s1.setText("当前状态：已开启 ✓");
            s1.setTextColor(0xFF2E7D5B);
            ((TextView) findViewById(R.id.btnNotifyAccess)).setText("已开启，点此可再次进入");
            store.setBool(Prefs.GUIDE_DONE, true);
            KeepAliveService.start(this);
        } else {
            s1.setText("当前状态：未开启");
            s1.setTextColor(0xFFD84343);
        }

        TextView badge2 = (TextView) findViewById(R.id.badge2);
        if (Ui.isIgnoringBattery(this)) {
            badge2.setText("✓");
        }

        TextView s3 = (TextView) findViewById(R.id.state3);
        boolean sms = android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (sms) {
            s3.setText("当前状态：已开启 ✓");
            s3.setTextColor(0xFF2E7D5B);
        } else {
            s3.setText("当前状态：未开启（不影响主要功能）");
            s3.setTextColor(0xFF8A9099);
        }
    }
}
