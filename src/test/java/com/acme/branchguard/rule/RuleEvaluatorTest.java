package com.acme.branchguard.rule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleEvaluatorTest {

    private static RuleConfig whitelist(String... patterns) {
        RuleConfig config = new RuleConfig();
        config.mode = RuleConfig.Mode.WHITELIST;
        config.patterns = new ArrayList<>(List.of(patterns));
        return config;
    }

    private static RuleConfig blacklist(String... patterns) {
        RuleConfig config = new RuleConfig();
        config.mode = RuleConfig.Mode.BLACKLIST;
        config.patterns = new ArrayList<>(List.of(patterns));
        return config;
    }

    @Test
    void disabledAlwaysAllows() {
        RuleConfig config = blacklist("main");
        config.enabled = false;
        assertEquals(RuleEvaluator.Verdict.ALLOW,
                RuleEvaluator.evaluate(config, new RepoSnapshot("/r", "main", false)).verdict());
    }

    @Test
    void whitelistAllowed() {
        assertEquals(RuleEvaluator.Verdict.ALLOW,
                RuleEvaluator.evaluate(whitelist("dev", "feature/*"),
                        new RepoSnapshot("/r", "feature/login", false)).verdict());
    }

    @Test
    void whitelistBlocked() {
        RuleEvaluator.Result result = RuleEvaluator.evaluate(whitelist("dev"),
                new RepoSnapshot("/r", "main", false));
        assertEquals(RuleEvaluator.Verdict.BLOCK, result.verdict());
        assertEquals(RuleEvaluator.Reason.BRANCH_NOT_ALLOWED, result.reason());
    }

    @Test
    void blacklistProtected() {
        RuleEvaluator.Result result = RuleEvaluator.evaluate(blacklist("main", "release/*"),
                new RepoSnapshot("/r", "release/2.3", false));
        assertEquals(RuleEvaluator.Verdict.BLOCK, result.verdict());
        assertEquals(RuleEvaluator.Reason.PROTECTED_BRANCH, result.reason());
    }

    @Test
    void blacklistAllowsOthers() {
        assertEquals(RuleEvaluator.Verdict.ALLOW,
                RuleEvaluator.evaluate(blacklist("main"),
                        new RepoSnapshot("/r", "dev", false)).verdict());
    }

    @Test
    void mergeCommitAllowed() {
        RuleEvaluator.Result result = RuleEvaluator.evaluate(blacklist("main"),
                new RepoSnapshot("/r", "main", true));
        assertEquals(RuleEvaluator.Verdict.ALLOW, result.verdict());
        assertEquals(RuleEvaluator.Reason.MERGE_COMMIT, result.reason());
    }

    @Test
    void mergeCommitBlockedWhenExemptionDisabled() {
        RuleConfig config = blacklist("main");
        config.allowMergeCommits = false;
        assertEquals(RuleEvaluator.Verdict.BLOCK,
                RuleEvaluator.evaluate(config, new RepoSnapshot("/r", "main", true)).verdict());
    }

    @Test
    void detachedHeadBlocked() {
        RuleEvaluator.Result result = RuleEvaluator.evaluate(blacklist("main"),
                new RepoSnapshot("/r", null, false));
        assertEquals(RuleEvaluator.Verdict.BLOCK, result.verdict());
        assertEquals(RuleEvaluator.Reason.DETACHED_HEAD, result.reason());
    }

    @Test
    void detachedHeadAllowedWhenConfigured() {
        RuleConfig config = blacklist("main");
        config.blockDetachedHead = false;
        assertEquals(RuleEvaluator.Verdict.ALLOW,
                RuleEvaluator.evaluate(config, new RepoSnapshot("/r", null, false)).verdict());
    }

    @Test
    void emptyRulesAllowEverything() {
        RuleConfig config = blacklist();
        config.patterns = Collections.emptyList();
        RuleEvaluator.Result result = RuleEvaluator.evaluate(config, new RepoSnapshot("/r", "main", false));
        assertEquals(RuleEvaluator.Verdict.ALLOW, result.verdict());
        assertEquals(RuleEvaluator.Reason.NO_BRANCH_RULES, result.reason());
    }

    @Test
    void disabledBeatsMergeFlag() {
        RuleConfig config = blacklist("main");
        config.enabled = false;
        RuleEvaluator.Result result = RuleEvaluator.evaluate(config, new RepoSnapshot("/r", "main", true));
        assertEquals(RuleEvaluator.Reason.DISABLED, result.reason());
    }
}
