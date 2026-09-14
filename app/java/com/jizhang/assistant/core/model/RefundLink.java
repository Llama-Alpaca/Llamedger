package com.jizhang.assistant.core.model;

/**
 * 退款与原支出的关联记录（审计用，可据此恢复被自动撤销的支出）。
 */
public class RefundLink {

    public static final int MODE_FULL = 0;      // 全额退款 -> 原支出被移除
    public static final int MODE_PARTIAL = 1;   // 部分退款 -> 冲抵金额

    public long id;
    public long refundTxnId;     // 退款流水 id
    public long originalTxnId;   // 原支出流水 id
    public long amountCents;     // 本次冲抵金额(分)
    public int mode;             // MODE_*
    public int score;            // 匹配置信分
    public String reason;        // 匹配理由，便于人工核对
    public boolean autoRemoved;  // 是否自动移除了原支出
    public long createdAt;

    @Override
    public String toString() {
        return "RefundLink{refund=" + refundTxnId + ", original=" + originalTxnId
                + ", amount=" + amountCents + ", mode=" + mode
                + ", score=" + score + ", reason=" + reason + "}";
    }
}
