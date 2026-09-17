package com.jizhang.assistant.core.parse;

/**
 * 解析结果。
 */
public class ParseResult {

    public boolean success;
    public long amountCents;      // 恒为正
    public int direction;         // Txn.DIR_OUT / DIR_IN
    public boolean refund;        // 是否判定为退款/退回
    public String merchant;       // 商户/对手方
    public String account;        // 账户尾号等
    public long occurredAt;       // 0 表示解析不到，调用方用接收时间兜底
    public String category;       // 建议分类，可为空
    public int confidence;        // 0-100
    public String reason;         // 判定说明，便于排错

    public static ParseResult fail(String reason) {
        ParseResult r = new ParseResult();
        r.success = false;
        r.confidence = 0;
        r.reason = reason;
        return r;
    }

    @Override
    public String toString() {
        return "ParseResult{ok=" + success + ", amount=" + amountCents
                + ", dir=" + (direction == 0 ? "OUT" : "IN")
                + ", refund=" + refund + ", merchant=" + merchant
                + ", account=" + account + ", cat=" + category
                + ", conf=" + confidence + ", reason=" + reason + "}";
    }
}
