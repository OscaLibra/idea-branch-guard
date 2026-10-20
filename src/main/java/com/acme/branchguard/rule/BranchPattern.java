package com.acme.branchguard.rule;

import java.util.regex.Pattern;

/**
 * 分支通配符匹配：{@code *} 匹配任意字符（含 '/'），{@code ?} 匹配单字符，其余按字面量。
 */
public final class BranchPattern {

    private BranchPattern() {
    }

    public static boolean matches(String branch, String glob) {
        if (branch == null || glob == null) {
            return false;
        }
        String trimmed = glob.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return toRegex(trimmed).matcher(branch).matches();
    }

    private static Pattern toRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%',
                     '{', '}', '[', ']', '\\' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return Pattern.compile(sb.append('$').toString());
    }
}
