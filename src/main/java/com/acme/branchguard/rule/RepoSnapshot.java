package com.acme.branchguard.rule;

/**
 * 单个仓库的判定输入。branchName 为 null 表示分离 HEAD。
 * 刻意只依赖 JDK，保证 rule 包可脱离 IDE 单测。
 */
public record RepoSnapshot(String rootPath, String branchName, boolean merging) {
}
