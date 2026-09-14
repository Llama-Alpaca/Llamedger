package com.jizhang.assistant.core.engine;

import com.jizhang.assistant.core.model.Txn;
import com.jizhang.assistant.core.parse.Lexicon;

/**
 * 按商户名/关键词自动归类。
 */
public final class CategoryGuesser {

    private CategoryGuesser() {}

    public static String guess(String merchant, String extraText, int direction) {
        String probe = ((merchant == null ? "" : merchant) + " "
                + (extraText == null ? "" : extraText)).trim();
        if (probe.length() == 0) {
            return direction == Txn.DIR_IN ? "收入" : "其他";
        }
        for (String[] rule : Lexicon.CATEGORY_RULES) {
            String category = rule[0];
            for (int i = 1; i < rule.length; i++) {
                if (probe.contains(rule[i])) {
                    return category;
                }
            }
        }
        return direction == Txn.DIR_IN ? "收入" : "其他";
    }
}
