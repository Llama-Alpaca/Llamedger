package com.jizhang.assistant.core.model;

/**
 * 原始捕获事件：短信或通知的原文，先落库再解析，保证不丢数据、可回溯重解析。
 */
public class RawEvent {

    public static final int KIND_SMS = 1;
    public static final int KIND_NOTIFICATION = 2;

    public long id;
    public int kind;            // KIND_*
    public String pkg;          // 通知来源包名，如 com.tencent.mm
    public String sender;       // 短信发件人，如 95555
    public String title;        // 通知标题
    public String body;         // 正文
    public long receivedAt;
    public int parsed;          // 0未解析 1解析成功 2解析失败 3已忽略
    public String dedupeKey;

    public RawEvent() {
        this.parsed = 0;
    }

    public String fullText() {
        StringBuilder sb = new StringBuilder();
        if (title != null && title.length() > 0) {
            sb.append(title).append(' ');
        }
        if (body != null) {
            sb.append(body);
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "RawEvent{kind=" + kind + ", pkg=" + pkg + ", sender=" + sender
                + ", text=" + fullText() + "}";
    }
}
