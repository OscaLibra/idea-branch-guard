package com.acme.branchguard.rule;

import java.util.List;

/**
 * 决策表实现（见需求文档 §7），纯函数，无 IDE 依赖。
 */
public final class RuleEvaluator {

    public enum Verdict {ALLOW, BLOCK}

    public enum Reason {
        OK, DISABLED, MERGE_COMMIT, DETACHED_HEAD,
        BRANCH_ALLOWED, BRANCH_NOT_ALLOWED, PROTECTED_BRANCH, NO_BRANCH_RULES
    }

    public record Result(Verdict verdict, Reason reason) {
        static Result allow(Reason reason) {
            return new Result(Verdict.ALLOW, reason);
        }

        static Result block(Reason reason) {
            return new Result(Verdict.BLOCK, reason);
        }
    }

    private RuleEvaluator() {
    }

    public static Result evaluate(RuleConfig config, RepoSnapshot repo) {
        if (!config.enabled) {
            return Result.allow(Reason.DISABLED);
        }
        if (config.allowMergeCommits && repo.merging()) {
            return Result.allow(Reason.MERGE_COMMIT);
        }

        String branch = repo.branchName();
        if (branch == null || branch.isBlank()) {
            return config.blockDetachedHead
                    ? Result.block(Reason.DETACHED_HEAD)
                    : Result.allow(Reason.OK);
        }

        List<String> patterns = config.patterns;
        if (patterns == null || patterns.isEmpty()) {
            return Result.allow(Reason.NO_BRANCH_RULES);
        }

        boolean matched = false;
        for (String pattern : patterns) {
            if (BranchPattern.matches(branch, pattern)) {
                matched = true;
                break;
            }
        }

        if (config.mode == RuleConfig.Mode.WHITELIST) {
            return matched ? Result.allow(Reason.BRANCH_ALLOWED)
                    : Result.block(Reason.BRANCH_NOT_ALLOWED);
        }
        return matched ? Result.block(Reason.PROTECTED_BRANCH)
                : Result.allow(Reason.OK);
    }
}
