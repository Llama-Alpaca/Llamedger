package com.jizhang.assistant.capture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.parse.Lexicon;

/**
 * 短信采集（次要通道）。
 * 招行的交易提醒走 App 推送，但如果用户同时开通了短信提醒，这里也能兜住。
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) return;

        String format = bundle.getString("format");
        StringBuilder body = new StringBuilder();
        String sender = null;

        for (Object pdu : pdus) {
            try {
                SmsMessage msg;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    msg = SmsMessage.createFromPdu((byte[]) pdu);
                }
                if (msg == null) continue;
                if (sender == null) sender = msg.getOriginatingAddress();
                String t = msg.getMessageBody();
                if (t != null) body.append(t);
            } catch (Throwable ignored) {
            }
        }

        if (body.length() == 0) return;
        // 只处理银行短信，避免把私人短信也读进来
        if (!Lexicon.isBankSender(sender)) return;

        RawEvent e = new RawEvent();
        e.kind = RawEvent.KIND_SMS;
        e.sender = sender;
        e.pkg = "com.android.mms";
        e.title = "银行短信";
        e.body = body.toString();
        e.receivedAt = System.currentTimeMillis();
        e.dedupeKey = "sms#" + sender + "#" + e.body.hashCode() + "#" + (e.receivedAt / 60000);

        Monitor.handleAsync(context, e);
    }
}
