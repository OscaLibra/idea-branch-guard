package com.acme.branchguard.commit;

import com.acme.branchguard.rule.RepoSnapshot;
import com.acme.branchguard.rule.RuleConfig;
import com.acme.branchguard.rule.RuleEvaluator;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 把待提交文件映射到 Git 仓库，并逐个仓库执行规则判定。
 *
 * <p><b>线程约束</b>：非模态提交流程的 {@code beforeCheckin()} 在 EDT 上执行。
 * {@code GitRepositoryManager.getRepositoryForRoot(Quick)} 在 EDT 可能触发
 * 「同步仓库更新」，平台会直接抛
 * {@code Throwable: Do not call synchronous repository update in EDT}。</p>
 *
 * <p>因此这里<b>只读</b> {@link GitRepositoryManager#getRepositories()}（纯内存），
 * 用「根路径最长前缀匹配」为每个文件定位其<b>最内层</b>仓库（语义与
 * {@code ProjectLevelVcsManager#getVcsRootFor} 一致），
 * 不做任何可能触发更新的调用。</p>
 *
 * <p>取最内层很关键：嵌套仓库 / submodule 场景下，子仓库里的文件同时也位于父仓库目录内，
 * 若把父仓库也算作命中，父仓库的分支状态会连带把提交误判为违规。</p>
 */
public final class BranchGuardChecker {

    public record Violation(@NotNull String rootPath,
                            String branchName,
                            @NotNull RuleEvaluator.Reason reason) {
    }

    /**
     * @param repositoryCount 实际解析到的 Git 仓库数，用于诊断「文件没映射到仓库」的情况
     */
    public record CheckResult(@NotNull List<Violation> violations, int repositoryCount) {
        static CheckResult empty() {
            return new CheckResult(Collections.emptyList(), 0);
        }
    }

    private BranchGuardChecker() {
    }

    public static @NotNull CheckResult check(@NotNull Project project,
                                             @NotNull Collection<File> files,
                                             @NotNull RuleConfig config) {
        if (!config.enabled || files.isEmpty()) {
            return CheckResult.empty();
        }

        List<GitRepository> registered = GitRepositoryManager.getInstance(project).getRepositories();

        // 每个文件归属到根路径最长的仓库（嵌套仓库 / submodule 取最内层），再对仓库去重
        List<Violation> violations = new ArrayList<>();
        List<GitRepository> hit = new ArrayList<>();
        for (File file : files) {
            GitRepository owner = locateOwner(registered, file);
            if (owner != null && !hit.contains(owner)) {
                hit.add(owner);
            }
        }

        for (GitRepository repo : hit) {
            String branchName = repo.getCurrentBranch() == null
                    ? null
                    : repo.getCurrentBranch().getName();
            boolean merging = repo.getState() == GitRepository.State.MERGING;

            RepoSnapshot snapshot = new RepoSnapshot(repo.getRoot().getPath(), branchName, merging);
            RuleEvaluator.Result result = RuleEvaluator.evaluate(config, snapshot);

            if (result.verdict() == RuleEvaluator.Verdict.BLOCK) {
                violations.add(new Violation(snapshot.rootPath(), branchName, result.reason()));
            }
        }
        return new CheckResult(violations, hit.size());
    }

    /**
     * 在已注册仓库中找出该文件所属的仓库：命中「根路径最长」的那个，即最内层仓库。
     * 找不到（例如该文件不在任何 Git 仓库内，或仓库尚未被平台注册）时返回 null。
     */
    private static @Nullable GitRepository locateOwner(@NotNull List<GitRepository> repositories,
                                                       @NotNull File file) {
        String path = normalize(file);
        if (path == null) {
            return null;
        }
        GitRepository best = null;
        int bestLength = -1;
        for (GitRepository repo : repositories) {
            String root = normalize(repo.getRoot().getPath());
            if (root == null) {
                continue;
            }
            if (under(path, root) && root.length() > bestLength) {
                best = repo;
                bestLength = root.length();
            }
        }
        return best;
    }

    /**
     * 前缀匹配：path 位于 root 之下（或就是 root）。路径均已统一为正斜杠。
     * 注意用 {@code root + "/"} 判断，避免 {@code /repo-x} 误匹配 {@code /repo}。
     */
    private static boolean under(@NotNull String path, @NotNull String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    /** 文件 → 归一化路径。已删除文件也用 canonical path，路径依然存在。 */
    private static @Nullable String normalize(@NotNull File file) {
        String p;
        try {
            p = file.getCanonicalPath();
        } catch (IOException e) {
            p = file.getAbsolutePath();
        }
        return normalize(p);
    }

    /** 归一化为 VFS 风格路径（正斜杠、去掉末尾斜杠），供与仓库根路径比较。 */
    private static @Nullable String normalize(@NotNull String p) {
        if (p.isEmpty()) {
            return null;
        }
        String r = p.replace('\\', '/');
        if (r.length() > 1 && r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
