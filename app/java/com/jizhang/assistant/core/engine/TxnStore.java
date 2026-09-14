package com.jizhang.assistant.core.engine;

import com.jizhang.assistant.core.model.RawEvent;
import com.jizhang.assistant.core.model.RefundLink;
import com.jizhang.assistant.core.model.Txn;

import java.util.List;

/**
 * 存储抽象。
 * Android 端由 SQLite 实现；电脑端单测用内存实现，从而让业务逻辑可脱离手机验证。
 */
public interface TxnStore {

    long insertTxn(Txn t);

    void updateTxn(Txn t);

    Txn findTxnById(long id);

    /** 查候选原支出：指定时间之前的支出流水 */
    List<Txn> findRefundCandidates(long beforeTime, long afterTime, int limit);

    /** 按时间去重查重：找同方向的近期流水 */
    List<Txn> findRecentByDirection(int direction, long fromTime, long toTime, int limit);

    long insertRefundLink(RefundLink l);

    List<RefundLink> refundLinksForOriginal(long originalId);

    void deleteRefundLinkByRefundTxn(long refundTxnId);

    long insertRawEvent(RawEvent e);

    void updateRawEvent(RawEvent e);

    boolean rawEventExists(String dedupeKey);

    List<Txn> recentTxns(int limit);

    String getSetting(String key, String def);

    void setSetting(String key, String value);

    // ---------------- 内部转账线索 ----------------
    // 场景：微信/支付宝只推「零钱提现已到账」而不给金额，
    // 我们先记下一条线索，等银行那条「收款」到了再据此判定为转账。

    /** 记一条转账线索，返回 id */
    long insertTransferHint(long at, String channel, String note);

    /** 查找时间窗内最近的一条未消费线索，没有返回 0 */
    long findTransferHint(long centerAt, long windowMs);

    /** 消费掉一条线索，避免它被重复套用到别的流水上 */
    void consumeTransferHint(long id);
}
