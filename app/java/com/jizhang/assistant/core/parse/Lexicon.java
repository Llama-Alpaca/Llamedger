package com.jizhang.assistant.core.parse;

/**
 * 中文记账关键词词典 + 文本归一化工具。
 * 全部为纯 Java，方便在电脑上直接跑单元测试。
 */
public final class Lexicon {

    private Lexicon() {}

    /** 支出方向关键词 */
    public static final String[] OUT_WORDS = {
            "支出", "消费", "支付", "付款", "扣款", "扣费", "转出", "取出", "取现",
            "消费金额", "已付", "付款成功", "支付成功", "代扣", "缴费", "购买",
            "刷卡", "透支", "支取", "转账汇款", "汇出", "收单", "交易金额", "借",
            "支出人民币", "支付人民币", "消费人民币",
            // 提现是从「零钱/余额」转到银行卡，方向上属于转出
            "提现", "零钱提现", "余额提现", "提现到银行卡", "提现到银行"
    };

    /** 收入方向关键词 */
    public static final String[] IN_WORDS = {
            "收入", "入账", "转入", "存入", "汇入", "收到", "到账", "贷记", "存入金额",
            "转入金额", "收入人民币", "入账人民币", "退款", "退回", "返还", "冲正",
            "退还", "报销", "工资", "薪", "奖金", "红包", "利息", "收益", "贷",
            // 「收款」是银行入账最常见的措辞，之前漏了会导致整条通知被丢弃
            "收款", "收讫", "入款", "到账金额"
    };

    /**
     * 退款/退回关键词 —— 本软件的核心识别目标。
     * 命中即认为是「把之前的支出冲掉」的事件。
     */
    public static final String[] REFUND_WORDS = {
            "退款成功", "已退款", "退款到账", "原路退回", "原路返还", "资金退回",
            "交易撤销", "已撤销", "撤销交易", "冲正", "逆冲", "退货", "退款",
            "退回", "退还", "已返还", "返还成功", "超时退回", "自动退回",
            "转账退回", "过期退回", "未接收已退回", "refund", "reversal",
            "reversed", "已退", "退单", "退款处理中"
    };

    /**
     * 内部转账关键词 —— 钱只是在「你自己的账户之间」搬家，不是消费也不是收入。
     *
     * 典型：微信零钱提现到银行卡、银行卡充值到零钱、信用卡还款。
     * 这类流水如果记成支出/收入，会同时虚增两边的金额，必须单独标记并从收支统计里排除。
     */
    public static final String[] INTERNAL_TRANSFER_WORDS = {
            "提现", "零钱提现", "余额提现", "提现到银行卡", "提现到银行", "提现成功",
            "充值到零钱", "零钱充值", "零钱通", "余额宝", "转入零钱", "转出零钱",
            "信用卡还款", "还款成功", "自动还款", "最低还款", "账单还款",
            "本人转账", "自己转账", "同一身份证"
    };

    /**
     * 转账类关键词：微信/支付宝 24 小时未领取会自动退回，
     * 这类退回需要放宽匹配时间窗并优先匹配「转账」分类。
     */
    public static final String[] TRANSFER_WORDS = {
            "转账", "红包", "转账汇款", "发给", "向你转账", "收款"
    };

    /** 银行短信发件人（含常见银行服务号） */
    public static final String[] BANK_SENDERS = {
            "95555",   // 招商银行 ★
            "95588",   // 工商银行
            "95533",   // 建设银行
            "95566",   // 中国银行
            "95599",   // 农业银行
            "95511",   // 平安银行
            "95559",   // 交通银行
            "95561",   // 兴业银行
            "95558",   // 中信银行
            "95577",   // 华夏银行
            "95568",   // 民生银行
            "95595",   // 光大银行
            "95528",   // 浦发银行
            "95508",   // 广发银行
            "95313",   // 广州银行
            "95312",   // 紫金农商
            "1069", "1068", "1065"  // 银行营销/通知通道
    };

    /** 银行 App 包名（招行 App 推送通知为主要取数通道） */
    public static final String[] BANK_PKGS = {
            "cmb.pb",                                  // 招商银行 ★主力
            "com.cmbchina.ccd.pluto.cmbActivity",      // 招商银行(新版)
            "com.icbc",                                // 工商银行
            "com.chinamworld.main",                    // 建设银行
            "com.bankcomm.Bankcomm",                   // 交通银行
            "com.android.bankabc",                     // 农业银行
            "com.chinamworld.bocmbci",                 // 中国银行
            "com.pingan.paces.ccms",                   // 平安银行
            "com.cmbchina.mobilebank",                 // 招商银行(旧版)
            "com.unionpay"                             // 云闪付
    };

    /**
     * 包名「特征片段」—— 命中任意一个就当做银行类 App。
     *
     * 为什么需要它：各家银行 App 的包名经常随版本变化（招商银行就至少有 cmb.pb、
     * com.cmbchina.* 等多种写法），靠精确匹配的名单极容易漏。这里改成模糊匹配，
     * 只要包名里带银行特征片段就认。
     */
    public static final String[] BANK_PKG_HINTS = {
            "cmb", "icbc", "ccb", "abchina", "boc", "bocom", "psbc", "spdb",
            "cib", "cebbank", "citic", "cmbc", "hxb", "cgb", "nbcb", "bank",
            "unionpay", "pingan", "sdb", "hsbank", "czbank", "hzbank", "bjbank"
    };

    /** 支付类 App 的包名特征片段 */
    public static final String[] PAY_PKG_HINTS = {
            "tencent.mm", "alipay", "tenpay", "wechat", "wxpay", "unionpay"
    };

    /** 银行交易通知的文本特征（包名认不出时用来兜底判断） */
    public static final String[] BANK_TEXT_MARKERS = {
            "账户", "账号", "尾号", "卡号", "储蓄卡", "信用卡", "一卡通",
            "人民币", "余额", "快捷支付", "网银", "手机银行", "活期"
    };

    /** 交易行为词（用于判断这段文字是不是一笔交易） */
    public static final String[] TXN_TEXT_MARKERS = {
            "支出", "消费", "支付", "付款", "扣款", "扣费", "转出", "取出", "取现",
            "收入", "入账", "转入", "存入", "收款", "到账", "退款", "退回",
            "提现", "充值", "还款", "缴费", "代扣", "汇入", "汇出"
    };

    /** 支付类 App 包名 */
    public static final String[] PAY_PKGS = {
            "com.tencent.mm",                          // 微信
            "com.eg.android.AlipayGphone",             // 支付宝
            "com.tenpay.android",                      // 财付通
            "com.unionpay"                             // 云闪付
    };

    /** 分类规则：分类名 -> 关键词，顺序即优先级（越靠前越优先匹配） */
    public static final String[][] CATEGORY_RULES = {
            {"退款", "退款", "退回", "退货", "返还", "冲正", "退还"},
            {"餐饮", "餐饮", "餐厅", "饭店", "外卖", "美团", "饿了么", "肯德基", "麦当劳",
                    "星巴克", "咖啡", "奶茶", "火锅", "烧烤", "食堂", "小吃", "面馆",
                    "快餐", "汉堡", "必胜客", "海底捞", "瑞幸", "喜茶", "蜜雪"},
            {"交通", "地铁", "公交", "出租", "滴滴", "高德", "打车", "加油", "中石化",
                    "中石油", "停车", "高速", "ETC", "火车", "高铁", "机票", "航空",
                    "共享单车", "哈啰", "青桔", "曹操", "充电桩", "车费", "12306"},
            {"购物", "淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "商场", "唯品会",
                    "苏宁", "国美", "盒马", "沃尔玛", "屈臣氏", "永辉", "山姆", "购物",
                    "得物", "小红书", "抖音商城", "1688"},
            {"居住", "房租", "租金", "物业", "水费", "电费", "燃气", "取暖", "宽带",
                    "住房", "中介", "维修", "家政"},
            {"通讯", "话费", "流量", "中国移动", "中国联通", "中国电信", "手机充值",
                    "固话", "上网费"},
            {"娱乐", "电影", "游戏", "会员", "视频", "音乐", "KTV", "网吧", "演出",
                    "门票", "腾讯视频", "爱奇艺", "网易云", "steam", "哔哩哔哩", "b站",
                    "密室", "剧本杀", "健身", "游泳"},
            {"医疗", "医院", "药房", "药店", "诊所", "体检", "挂号", "医疗", "门诊"},
            {"教育", "学费", "培训", "教育", "课程", "书店", "图书", "文具", "考试"},
            {"人情", "红包", "礼金", "随礼", "份子", "孝敬", "赠与"},
            {"旅行", "酒店", "民宿", "携程", "飞猪", "去哪儿", "旅行", "景区", "度假",
                    "airbnb", "住宿"},
            {"金融", "还款", "信用卡", "手续费", "利息", "保险", "理财", "基金",
                    "贷款", "年费", "管理费"},
            {"收入", "工资", "薪", "奖金", "报销", "绩效", "补贴", "分红", "收益",
                    "利息收入"},
            {"转账", "转账", "红包", "收款", "付款给", "转账汇款"}
    };

    /** 通知来源包名 -> 展示名 */
    public static final String[][] PKG_NAMES = {
            {"com.tencent.mm", "微信"},
            {"com.eg.android.AlipayGphone", "支付宝"},
            {"cmb.pb", "招商银行"},
            {"com.cmbchina.ccd.pluto.cmbActivity", "招商银行"},
            {"com.android.mms", "短信"},
            {"com.unionpay", "云闪付"},
            {"com.tenpay.android", "财付通"}
    };

    /** 需要监听的支付相关包名 */
    public static final String[] WATCH_PKGS = {
            "com.tencent.mm",                            // 微信
            "com.eg.android.AlipayGphone",               // 支付宝
            "cmb.pb",                                    // 招商银行
            "com.cmbchina.ccd.pluto.cmbActivity",        // 招商银行(新版)
            "com.unionpay",                              // 云闪付
            "com.tenpay.android"                         // 财付通
    };

    public static boolean containsAny(String text, String[] words) {
        if (text == null || text.length() == 0) return false;
        String lower = text.toLowerCase();
        for (String w : words) {
            if (lower.contains(w.toLowerCase())) return true;
        }
        return false;
    }

    public static String firstHit(String text, String[] words) {
        if (text == null || text.length() == 0) return null;
        String lower = text.toLowerCase();
        for (String w : words) {
            if (lower.contains(w.toLowerCase())) return w;
        }
        return null;
    }

    /**
     * 文本归一化：全角转半角、去空白、统一符号，便于正则和关键词匹配。
     */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u3000') {                    // 全角空格
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {  // 全角 ASCII
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString();
        out = out.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        // 压缩连续空格
        StringBuilder sb2 = new StringBuilder(out.length());
        boolean prevSpace = false;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c == ' ') {
                if (!prevSpace) sb2.append(c);
                prevSpace = true;
            } else {
                sb2.append(c);
                prevSpace = false;
            }
        }
        return sb2.toString().trim();
    }

    /** 判断是否银行短信发件人 */
    public static boolean isBankSender(String sender) {
        if (sender == null) return false;
        String s = sender.replaceAll("[^0-9]", "");
        if (s.length() == 0) return false;
        for (String b : BANK_SENDERS) {
            if (s.startsWith(b) || s.contains(b)) return true;
        }
        return false;
    }

    public static String pkgLabel(String pkg) {
        if (pkg == null) return "";
        for (String[] row : PKG_NAMES) {
            if (row[0].equals(pkg)) return row[1];
        }
        return pkg;
    }
}
