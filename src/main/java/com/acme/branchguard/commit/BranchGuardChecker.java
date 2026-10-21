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
 * 用「仓库根路径的最长前缀匹配」定位文件所属仓库，不做任何可能触发更新的调用。</p>
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

        // 记录命中的仓库（去重），文件归属用最长前缀匹配（支持嵌套仓库）
        List<Violation> violations = new ArrayList<>();
        List<GitRepository> hit = new ArrayList<>();
        for (GitRepository repo : registered) {
            if (containsFile(repo, files)) {
                hit.add(repo);
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

    /** 本仓库是否包含待提交文件中的任意一个。 */
    private static boolean containsFile(@NotNull GitRepository repo, @NotNull Collection<File> files) {
        String root = repo.getRoot().getPath();
        for (File file : files) {
            String path = normalize(file);
            if (path != null && under(path, root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 前缀匹配：path 位于 root 之下（或就是 root）。路径均已统一为正斜杠。
     * 注意用 {@code root + "/"} 判断，避免 {@code /repo-x} 误匹配 {@code /repo}。
     */
    private static boolean under(@NotNull String path, @NotNull String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    /** 归一化为 VFS 风格路径（正斜杠），供与 {@code repo.getRoot().getPath()} 比较。 */
    private static @Nullable String normalize(@NotNull File file) {
        String p;
        try {
            p = file.getCanonicalPath();
        } catch (IOException e) {
            p = file.getAbsolutePath();
        }
        p = p.replace('\\', '/');
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
