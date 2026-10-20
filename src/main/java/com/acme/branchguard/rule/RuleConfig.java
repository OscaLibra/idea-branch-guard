package com.acme.branchguard.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 纯 POJO 规则配置，不依赖任何 IDE 类型，便于单元测试与跨端复用。
 */
public class RuleConfig {

    public enum Mode {WHITELIST, BLACKLIST}

    public boolean enabled = true;
    public Mode mode = Mode.WHITELIST;
    public List<String> patterns = defaultPatterns();
    public boolean allowMergeCommits = true;
    public boolean blockDetachedHead = true;
    public String blockMessage = "";

    public static List<String> defaultPatterns() {
        return new ArrayList<>(Arrays.asList("dev", "develop", "feature/*", "hotfix/*"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleConfig)) return false;
        RuleConfig other = (RuleConfig) o;
        return enabled == other.enabled
                && allowMergeCommits == other.allowMergeCommits
                && blockDetachedHead == other.blockDetachedHead
                && mode == other.mode
                && Objects.equals(patterns, other.patterns)
                && Objects.equals(blockMessage, other.blockMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, mode, patterns, allowMergeCommits, blockDetachedHead, blockMessage);
    }
}
