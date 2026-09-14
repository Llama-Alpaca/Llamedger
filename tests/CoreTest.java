import com.jizhang.assistant.core.engine.*;
import com.jizhang.assistant.core.model.*;
import com.jizhang.assistant.core.parse.*;

import java.util.*;

/** 电脑端核心逻辑测试台（不依赖 Android，可直接 javac + java 运行） */
public class CoreTest {

    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        testBankPushBasic();
        testBankPushWithMerchantAndBalance();
        testBankPushFood();
        testWechatNotification();
        testAlipayNotification();
        testWechatRefund();
        testTransferExpiredReturn();
        testDedupeCrossChannel();
        testRefundAutoRemove();
        testPartialRefund();
        testNoFalsePositiveOnOldUnrelated();
        testRefundNoMatchBecomesIncome();
        // ---- 真实场景回归：微信零钱提现到招行卡 ----
        testCmbShoukuan();
        testWechatWithdrawAmountTrap();
        testWithdrawNoAmountNotRecorded();
        testInternalTransferPairing();
        testTransferNotRefundable();
        // ---- 用户实际通知原文回归 ----
        testWechatGroupedNotificationTrap();
        testRealWithdrawFlowNoAmountInWechat();
        testBankReceiveWithoutHintIsIncome();

        System.out.println();
        System.out.println("=====================================");
        System.out.println("  通过 " + pass + " 项，失败 " + fail + " 项");
        System.out.println("=====================================");
        if (fail > 0) System.exit(1);
    }

    static void testBankPushBasic() {
        RawEvent e = bank("账户变动通知", "您尾号1234的账户于08月29日14:30支付人民币100.00元", now());
        ParseResult r = EventParser.parse(e);
        check("招行App推送-基础支出", r.success && r.amountCents == 10000
                && r.direction == Txn.DIR_OUT && "1234".equals(r.account), r.toString());
    }

    static void testBankPushWithMerchantAndBalance() {
        RawEvent e = bank("招商银行",
                "您账户1234于08月29日14:30在支付宝消费人民币100.00元，余额12345.67元", now());
        ParseResult r = EventParser.parse(e);
        check("招行推送-取商户且不误取余额",
                r.success && r.amountCents == 10000
                        && "支付宝".equals(r.merchant) && !r.refund,
                r.toString());
    }

    static void testBankPushFood() {
        RawEvent e = bank("招商银行",
                "您账户1234于08月29日14:30在美团外卖消费人民币35.50元", now());
        ParseResult r = EventParser.parse(e);
        check("招行推送-餐饮金额", r.success && r.amountCents == 3550, r.toString());
    }

    static void testWechatNotification() {
        RawEvent e = pay("com.tencent.mm", "微信支付", "已支付¥25.50", now());
        ParseResult r = EventParser.parse(e);
        check("微信通知-支付", r.success && r.amountCents == 2550
                && r.direction == Txn.DIR_OUT, r.toString());
    }

    static void testAlipayNotification() {
        RawEvent e = pay("com.eg.android.AlipayGphone", "支付宝", "支付成功 100.00元", now());
        ParseResult r = EventParser.parse(e);
        check("支付宝通知-支付", r.success && r.amountCents == 10000
                && r.direction == Txn.DIR_OUT, r.toString());
    }

    static void testWechatRefund() {
        RawEvent e = pay("com.tencent.mm", "微信支付", "退款到账 ￥100.00", now());
        ParseResult r = EventParser.parse(e);
        check("微信通知-退款", r.success && r.refund && r.direction == Txn.DIR_IN
                && r.amountCents == 10000, r.toString());
    }

    static void testTransferExpiredReturn() {
        RawEvent e = pay("com.tencent.mm", "微信支付", "转账已退回 ￥200.00", now());
        ParseResult r = EventParser.parse(e);
        check("微信通知-转账过期退回", r.success && r.refund
                && r.amountCents == 20000, r.toString());
    }

    static void testDedupeCrossChannel() {
        MemoryStore store = new MemoryStore();
        long t = now() - 3600000L;
        IngestEngine.Result a = IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(t) + "支付人民币50.00元", t),
                store, RefundEngine.Policy.defaults());
        IngestEngine.Result b = IngestEngine.ingest(
                pay("com.tencent.mm", "微信支付", "已支付¥50.00", t + 120000L),
                store, RefundEngine.Policy.defaults());
        check("跨通道去重-同一笔只记一次",
                a.stored && b.duplicate && store.txns.size() == 1,
                "a=" + a + " b=" + b + " 条数=" + store.txns.size());
    }

    static void testRefundAutoRemove() {
        MemoryStore store = new MemoryStore();
        long t = now() - 3 * 3600000L;
        IngestEngine.Result out = IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(t) + "在美团外卖消费人民币100.00元", t),
                store, RefundEngine.Policy.defaults());
        IngestEngine.Result refund = IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(t + 3 * 3600000L) + "退款人民币100.00元", t + 3 * 3600000L),
                store, RefundEngine.Policy.defaults());

        Txn orig = store.txns.get(0);
        check("退款自动移除原支出",
                out.stored && refund.refundMatched && refund.originalRemoved
                        && orig.status == Txn.STATUS_VOIDED,
                "refund=" + refund + " 原支出状态=" + orig.status);
    }

    static void testPartialRefund() {
        MemoryStore store = new MemoryStore();
        long t = now() - 2 * 3600000L;
        IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(t) + "在淘宝消费人民币100.00元", t),
                store, RefundEngine.Policy.defaults());
        IngestEngine.Result refund = IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(t + 2 * 3600000L) + "退款人民币30.00元", t + 2 * 3600000L),
                store, RefundEngine.Policy.defaults());

        Txn orig = store.txns.get(0);
        check("部分退款冲减原支出",
                refund.refundMatched && !refund.originalRemoved
                        && orig.status == Txn.STATUS_ACTIVE
                        && orig.refundedCents == 3000
                        && orig.remainingCents() == 7000,
                "refund=" + refund + " 已退=" + orig.refundedCents
                        + " 剩余=" + orig.remainingCents());
    }

    static void testNoFalsePositiveOnOldUnrelated() {
        MemoryStore store = new MemoryStore();
        long old = now() - 60L * 24 * 3600000L;
        IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(old) + "支付人民币100.00元", old),
                store, RefundEngine.Policy.defaults());
        IngestEngine.Result refund = IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(now()) + "退款人民币100.00元", now()),
                store, RefundEngine.Policy.defaults());

        Txn orig = store.txns.get(0);
        check("防误删-纯金额巧合不冲销",
                !refund.refundMatched && orig.status == Txn.STATUS_ACTIVE,
                "refund=" + refund + " 原支出状态=" + orig.status);
    }

    static void testRefundNoMatchBecomesIncome() {
        MemoryStore store = new MemoryStore();
        IngestEngine.Result refund = IngestEngine.ingest(
                bank("招商银行", "您账户1234于" + stamp(now()) + "退款人民币88.00元", now()),
                store, RefundEngine.Policy.defaults());
        Txn t = store.txns.get(0);
        check("孤立退款-按收入记录不报错",
                refund.stored && !refund.refundMatched && t.direction == Txn.DIR_IN,
                "refund=" + refund + " txn=" + t);
    }

    // -------------------------------------------- 真实场景：微信提现到招行

    /** 用户上报：招行推送「收款人民币3000.00元」之前完全没被识别（词典里缺「收款」） */
    static void testCmbShoukuan() {
        RawEvent e = bank("账户变动通知",
                "您尾号1234的账户于" + stamp(now()) + "收款人民币3000.00元", now());
        ParseResult r = EventParser.parse(e);
        check("招行推送-收款(入账)能识别",
                r.success && r.amountCents == 300000
                        && r.direction == Txn.DIR_IN
                        && "1234".equals(r.account),
                r.toString());
    }

    /**
     * 用户上报：微信提现被记成 2 元支出。
     * 旧版正则会从标题「微信支付」一路跨到「预计2小时内到账」里的 2。
     */
    static void testWechatWithdrawAmountTrap() {
        RawEvent e = pay("com.tencent.mm", "微信支付",
                "零钱提现成功，预计2小时内到账，金额3000.00元", now());
        ParseResult r = EventParser.parse(e);
        check("微信提现-不被「2小时」里的2骗到",
                r.success && r.amountCents == 300000,
                r.toString());
    }

    /** 通知里压根没有金额时，宁可什么都不记，也不能记一笔假账 */
    static void testWithdrawNoAmountNotRecorded() {
        RawEvent e = pay("com.tencent.mm", "微信支付",
                "零钱提现成功，预计2小时内到账", now());
        ParseResult r = EventParser.parse(e);
        check("微信提现-无金额时拒绝入账", !r.success, r.toString());
    }

    /** 微信提现 + 招行收款 → 自动配对，双方都标记为转账 */
    static void testInternalTransferPairing() {
        MemoryStore store = new MemoryStore();
        long t = now() - 10 * 60 * 1000L;

        IngestEngine.ingest(
                pay("com.tencent.mm", "微信支付",
                        "零钱提现成功，预计2小时内到账，金额3000.00元", t),
                store, RefundEngine.Policy.defaults());
        IngestEngine.ingest(
                bank("账户变动通知",
                        "您尾号1234的账户于" + stamp(t + 60 * 1000L) + "收款人民币3000.00元",
                        t + 60 * 1000L),
                store, RefundEngine.Policy.defaults());

        Txn a = store.txns.get(0);
        Txn b = store.txns.get(1);
        boolean paired = a.isTransfer && b.isTransfer
                && a.pairTxnId == b.id && b.pairTxnId == a.id;
        check("提现↔银行入账 自动配对为转账", paired,
                "转出=" + a + " 转入=" + b
                        + " pairA=" + a.pairTxnId + " pairB=" + b.pairTxnId);
    }

    /** 转账不是消费，不该被退款引擎当成可冲销的支出 */
    static void testTransferNotRefundable() {
        MemoryStore store = new MemoryStore();
        long t = now() - 2 * 3600 * 1000L;

        IngestEngine.ingest(
                pay("com.tencent.mm", "微信支付", "零钱提现成功，金额500.00元", t),
                store, RefundEngine.Policy.defaults());
        IngestEngine.Result refund = IngestEngine.ingest(
                pay("com.tencent.mm", "微信支付", "退款到账 ￥500.00",
                        t + 2 * 3600 * 1000L),
                store, RefundEngine.Policy.defaults());

        Txn transfer = store.txns.get(0);
        check("转账不会被退款冲销",
                transfer.isTransfer && !refund.refundMatched
                        && transfer.status == Txn.STATUS_ACTIVE,
                "refund=" + refund + " 转账状态=" + transfer.status);
    }

    // ------------------------------ 用户实际通知原文（2026-09-13 反馈）

    /**
     * 用户实际收到的微信通知原文：
     *   微信支付 | [2条]微信支付: 零钱提现已到账
     *
     * 「[2条]」是通知分组计数，旧版正则把它里面的 2 当成了金额，
     * 于是提现被记成一笔 2 元支出。这里必须一个金额都识别不出来。
     */
    static void testWechatGroupedNotificationTrap() {
        RawEvent e = pay("com.tencent.mm", "微信支付",
                "[2条]微信支付: 零钱提现已到账", now());
        ParseResult r = EventParser.parse(e);
        check("微信提现-「[2条]」不被当成金额", !r.success, r.toString());
    }

    /**
     * 完整还原用户的真实场景：
     *   微信：只有「零钱提现已到账」，完全没有金额
     *   招行：您尾号1234的账户于xx收款人民币3000.00元
     *
     * 期望：只入账一条，且是「转账」而不是收入。
     */
    static void testRealWithdrawFlowNoAmountInWechat() {
        MemoryStore store = new MemoryStore();
        long t = now() - 5 * 60 * 1000L;

        // 第 1 条：微信，无金额，只能记下线索
        RawEvent wx = pay("com.tencent.mm", "微信支付",
                "[2条]微信支付: 零钱提现已到账", t);
        ParseResult wxPr = EventParser.parse(wx);
        boolean hinted = TransferEngine.noteHintIfInternalTransfer(wx, wxPr, store);

        // 第 2 条：招行收款
        IngestEngine.ingest(
                bank("账户变动通知",
                        "您尾号1234的账户于" + stamp(t + 60 * 1000L) + "收款人民币3000.00元",
                        t + 60 * 1000L),
                store, RefundEngine.Policy.defaults());

        boolean onlyOne = store.txns.size() == 1;
        Txn bankTxn = onlyOne ? store.txns.get(0) : store.txns.get(store.txns.size() - 1);
        boolean ok = hinted && onlyOne
                && bankTxn.amountCents == 300000
                && bankTxn.direction == Txn.DIR_IN
                && bankTxn.isTransfer
                && "转账".equals(bankTxn.category);
        check("真实场景-微信无金额时招行收款判为转账", ok,
                "hinted=" + hinted + " 条数=" + store.txns.size() + " " + bankTxn);
    }

    /** 反向验证：没有提现线索时，银行收款仍然老老实实算收入 */
    static void testBankReceiveWithoutHintIsIncome() {
        MemoryStore store = new MemoryStore();
        long t = now() - 5 * 60 * 1000L;
        IngestEngine.ingest(
                bank("账户变动通知",
                        "您尾号1234的账户于" + stamp(t) + "收款人民币500.00元", t),
                store, RefundEngine.Policy.defaults());
        Txn txn = store.txns.get(0);
        check("没有提现线索时-银行收款仍算收入",
                !txn.isTransfer && txn.direction == Txn.DIR_IN
                        && txn.amountCents == 50000,
                "txn=" + txn);
    }

    static RawEvent bank(String title, String body, long at) {
        RawEvent e = new RawEvent();
        e.kind = RawEvent.KIND_NOTIFICATION;
        e.pkg = "cmb.pb";
        e.title = title;
        e.body = body;
        e.receivedAt = at;
        return e;
    }

    static RawEvent pay(String pkg, String title, String body, long at) {
        RawEvent e = new RawEvent();
        e.kind = RawEvent.KIND_NOTIFICATION;
        e.pkg = pkg;
        e.title = title;
        e.body = body;
        e.receivedAt = at;
        return e;
    }

    static long now() { return System.currentTimeMillis(); }

    /** 把时间戳格式化成通知文案里的日期，保证测试数据贴近真实场景 */
    static String stamp(long t) {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("MM月dd日HH:mm");
        return f.format(new java.util.Date(t));
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  [PASS] " + name); }
        else {
            fail++;
            System.out.println("  [FAIL] " + name);
            System.out.println("         " + detail);
        }
    }
}
