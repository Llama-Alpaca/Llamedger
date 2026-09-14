import com.jizhang.assistant.core.engine.*;
import com.jizhang.assistant.core.model.*;
import com.jizhang.assistant.core.parse.*;

import java.text.SimpleDateFormat;
import java.util.Date;

/** 用用户实际收到的通知原文跑一遍完整流程 */
public class Demo {

    static SimpleDateFormat F = new SimpleDateFormat("MM月dd日HH:mm");

    public static void main(String[] args) {
        MemoryStore store = new MemoryStore();
        long base = System.currentTimeMillis() - 3600_000L;
        String bankAt = F.format(new Date(base + 60_000));

        banner("真实通知原文");
        System.out.println();
        System.out.println("  微信  标题：微信支付");
        System.out.println("        正文：[2条]微信支付: 零钱提现已到账");
        System.out.println("        （注意：正文里完全没有金额）");
        System.out.println();
        System.out.println("  招行  标题：账户变动通知");
        System.out.println("        正文：您尾号1234的账户于" + bankAt + "收款人民币3000.00元");
        System.out.println();

        // ---- 第 1 条：微信 ----
        RawEvent wx = notif("com.tencent.mm", "微信支付",
                "[2条]微信支付: 零钱提现已到账", base);
        ParseResult wxPr = EventParser.parse(wx);
        System.out.println("处理微信通知：");
        System.out.println("  解析结果：" + (wxPr.success ? "识别到金额 " + wxPr.amountCents : "未识别到金额（正确，通知里本就没有金额）"));
        boolean hinted = TransferEngine.noteHintIfInternalTransfer(wx, wxPr, store);
        System.out.println("  处理动作：" + (hinted
                ? "识别为「提现已到账」，记下转账线索等下一条入账"
                : "无动作"));
        System.out.println();

        // ---- 第 2 条：招行 ----
        RawEvent cmb = notif("cmb.pb", "账户变动通知",
                "您尾号1234的账户于" + bankAt + "收款人民币3000.00元", base + 60_000);
        IngestEngine.Result r = IngestEngine.ingest(cmb, store, RefundEngine.Policy.defaults());
        System.out.println("处理招行通知：");
        System.out.println("  " + r.message);
        System.out.println();

        banner("最终账本");
        System.out.println();
        System.out.printf("  %-12s %-12s %-8s %s%n", "时间", "渠道", "性质", "金额");
        System.out.println("  " + repeat('-', 52));
        for (Txn t : store.txns) {
            String kind = t.isTransfer ? "转账" : (t.direction == Txn.DIR_OUT ? "支出" : "收入");
            System.out.printf("  %-12s %-12s %-8s %s%n",
                    F.format(new Date(t.occurredAt)),
                    t.account == null ? "-" : t.account,
                    kind,
                    AmountParser.yuan(t.amountCents));
        }
        System.out.println();

        long in = 0, out = 0;
        for (Txn t : store.txns) {
            if (t.isTransfer) continue;
            if (t.direction == Txn.DIR_IN) in += t.amountCents;
            else out += t.remainingCents();
        }
        banner("本月收支统计");
        System.out.println();
        System.out.println("  支出  " + AmountParser.yuan(out));
        System.out.println("  收入  " + AmountParser.yuan(in));
        System.out.println();

        boolean ok = store.txns.size() == 1
                && store.txns.get(0).amountCents == 300000
                && store.txns.get(0).isTransfer
                && out == 0 && in == 0;
        System.out.println(ok
                ? "  ✔ 没有 2 元假账；3000 元提现被正确判为「转账」，不污染收支"
                : "  ✘ 结果不符合预期");
    }

    static RawEvent notif(String pkg, String title, String body, long at) {
        RawEvent e = new RawEvent();
        e.kind = RawEvent.KIND_NOTIFICATION;
        e.pkg = pkg;
        e.title = title;
        e.body = body;
        e.receivedAt = at;
        return e;
    }

    static void banner(String s) {
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println("  " + s);
        System.out.println("════════════════════════════════════════════════════════");
    }

    static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
