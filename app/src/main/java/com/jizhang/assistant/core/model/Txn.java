package com.jizhang.assistant.core.model;

/**
 * 一条记账流水。
 * 金额一律以「分」为单位存 long，避免浮点误差。
 */
public class Txn {

    public static final int DIR_OUT = 0;   // 支出
    public static final int DIR_IN = 1;    // 收入

    public static final int STATUS_ACTIVE = 0;  // 有效
    public static final int STATUS_VOIDED = 1;  // 已撤销(被退款冲掉/手工删除)
    public static final int STATUS_PENDING = 2; // 待确认(退款匹配不确定)

    public static final int SRC_MANUAL = 0;
    public static final int SRC_SMS = 1;
    public static final int SRC_NOTIFICATION = 2;
    public static final int SRC_IMPORT = 3;

    public long id;
    public long occurredAt;       // 交易发生时间 epoch millis
    public long amountCents;      // 金额(分)，恒为正
    public int direction;         // DIR_OUT / DIR_IN
    public String merchant;       // 商户/对手方
    public String category;       // 分类名
    public String account;        // 账户(如 招行尾号1234)
    public String note;           // 备注
    public int source;            // SRC_*
    public String dedupeKey;      // 去重指纹
    public int status;            // STATUS_*
    public long refundedCents;    // 已被退款冲抵的金额(分)
    public long originalTxnId;    // 若本条是退款，指向原支出 id；否则 0
    public long rawId;            // 来源原始事件 id
    public int confidence;        // 解析置信度 0-100
    public long createdAt;
    public long updatedAt;
    public boolean autoCreated;   // 是否由自动识别生成
    /** 是否为内部转账（自己的账户之间搬钱），不计入收支统计 */
    public boolean isTransfer;
    /** 内部转账的另一半流水 id（微信提现 ↔ 银行入账），0 表示未配对 */
    public long pairTxnId;

    public Txn() {
        this.status = STATUS_ACTIVE;
        this.direction = DIR_OUT;
        this.source = SRC_MANUAL;
        this.confidence = 100;
        this.autoCreated = false;
    }

    public boolean isOut() {
        return direction == DIR_OUT;
    }

    public boolean isActive() {
        return status == STATUS_ACTIVE;
    }

    /** 尚未被退款的净支出金额 */
    public long remainingCents() {
        long left = amountCents - refundedCents;
        return left < 0 ? 0 : left;
    }

    /** 带符号金额：支出为负、收入为正，用于统计求和 */
    public long signedCents() {
        return direction == DIR_IN ? amountCents : -amountCents;
    }

    public Txn copy() {
        Txn t = new Txn();
        t.id = id;
        t.occurredAt = occurredAt;
        t.amountCents = amountCents;
        t.direction = direction;
        t.merchant = merchant;
        t.category = category;
        t.account = account;
        t.note = note;
        t.source = source;
        t.dedupeKey = dedupeKey;
        t.status = status;
        t.refundedCents = refundedCents;
        t.originalTxnId = originalTxnId;
        t.rawId = rawId;
        t.confidence = confidence;
        t.createdAt = createdAt;
        t.updatedAt = updatedAt;
        t.autoCreated = autoCreated;
        t.isTransfer = isTransfer;
        t.pairTxnId = pairTxnId;
        return t;
    }

    @Override
    public String toString() {
        return "Txn{id=" + id + ", at=" + occurredAt + ", amt=" + amountCents
                + ", dir=" + (direction == DIR_IN ? "IN" : "OUT")
                + ", merchant=" + merchant + ", cat=" + category
                + ", status=" + status + ", refunded=" + refundedCents + "}";
    }
}
