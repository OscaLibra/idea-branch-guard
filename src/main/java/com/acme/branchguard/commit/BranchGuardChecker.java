package com.acme.branchguard.commit;

import com.acme.branchguard.rule.RepoSnapshot;
import com.acme.branchguard.rule.RuleConfig;
import com.acme.branchguard.rule.RuleEvaluator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把待提交文件映射到 Git 仓库，并逐个仓库执行规则判定。
 * <p>注意：{@code CheckinProjectPanel#getFiles()} 返回的是 {@link java.io.File}，
 * 需要先转成 {@link VirtualFile} 才能定位 VCS 根。</p>
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

        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();

        Set<GitRepository> repositories = new LinkedHashSet<>();
        for (File file : files) {
            VirtualFile virtualFile = fileSystem.findFileByIoFile(file);
            if (virtualFile == null && file.getParentFile() != null) {
                // 待提交文件可能是已删除状态，退一步用父目录定位仓库
                virtualFile = fileSystem.findFileByIoFile(file.getParentFile());
            }
            if (virtualFile == null) {
                continue;
            }
            VirtualFile root = vcsManager.getVcsRootFor(virtualFile);
            if (root == null) {
                continue;                       // 非 VCS 文件：忽略
            }
            GitRepository repo = repoManager.getRepositoryForRoot(root);
            if (repo != null) {
                repositories.add(repo);         // 非 Git 仓库：忽略
            }
        }

        List<Violation> violations = new ArrayList<>();
        for (GitRepository repo : repositories) {
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
        return new CheckResult(violations, repositories.size());
    }
}
