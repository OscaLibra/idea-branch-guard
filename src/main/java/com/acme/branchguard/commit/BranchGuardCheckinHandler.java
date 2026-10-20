package com.acme.branchguard.commit;

import com.acme.branchguard.BranchGuardBundle;
import com.acme.branchguard.rule.RuleConfig;
import com.acme.branchguard.rule.RuleEvaluator;
import com.acme.branchguard.settings.BranchGuardConfigurable;
import com.acme.branchguard.settings.BranchGuardSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class BranchGuardCheckinHandler extends CheckinHandler {

    private static final Logger LOG = Logger.getInstance(BranchGuardCheckinHandler.class);

    /** 分支名过长时截断，避免把弹窗撑出横向滚动条。 */
    private static final int MAX_BRANCH_CHARS = 24;

    private final Project myProject;
    private final CheckinProjectPanel myPanel;

    public BranchGuardCheckinHandler(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
        myProject = project;
        myPanel = panel;
    }

    @Override
    public ReturnResult beforeCheckin() {
        long startNs = System.nanoTime();
        try {
            BranchGuardSettings settings = BranchGuardSettings.getInstance(myProject);
            BranchGuardSettings.State state = settings.getState();
            if (state == null || !state.enabled) {
                // 可诊断性：规则被关闭时也留一行日志，避免"拦截失效"无从查起
                LOG.info("Branch Guard: check disabled (enabled="
                        + (state != null && state.enabled) + "), commit is not checked. project="
                        + myProject.getName());
                return ReturnResult.COMMIT;
            }

            RuleConfig config = state.toRuleConfig();
            Collection<File> files = myPanel.getFiles();
            BranchGuardChecker.CheckResult result = BranchGuardChecker.check(myProject, files, config);
            List<BranchGuardChecker.Violation> violations = result.violations();

            if (result.repositoryCount() == 0 && !files.isEmpty()) {
                // 文件没映射到任何 Git 仓库：说明 VCS 映射或仓库状态异常，这次提交不会校验
                LOG.warn("Branch Guard: " + files.size() + " file(s) committed but 0 Git repository resolved, "
                        + "commit is NOT checked. project=" + myProject.getName());
            } else {
                LOG.debug("Branch Guard: files=" + files.size() + ", repos=" + result.repositoryCount()
                        + ", violations=" + violations.size());
            }

            if (violations.isEmpty()) {
                return ReturnResult.COMMIT;
            }

            LOG.info("Branch Guard blocked commit: files=" + files.size()
                    + ", repos=" + result.repositoryCount() + ", violations=" + violations.size());

            Locale locale = resolveLocale(state);
            UIUtil.invokeAndWaitIfNeeded(() -> showBlockedDialog(violations, config, locale));
            return ReturnResult.CANCEL;
        } catch (Exception e) {
            // fail-open：插件故障绝不阻塞用户提交
            LOG.warn("Branch Guard 执行失败，已放行本次提交", e);
            return ReturnResult.COMMIT;
        } finally {
            LOG.debug("Branch Guard 耗时 " + (System.nanoTime() - startNs) / 1_000_000 + " ms");
        }
    }

    /**
     * @return null 表示跟随 IDE 界面语言
     */
    private static @Nullable Locale resolveLocale(@NotNull BranchGuardSettings.State state) {
        BranchGuardSettings.MessageLanguage language = state.messageLanguage;
        if (language == null) {
            return null;
        }
        return switch (language) {
            case ZH_CN -> Locale.SIMPLIFIED_CHINESE;
            case EN -> Locale.ENGLISH;
            case FOLLOW_IDE -> null;
        };
    }

    private void showBlockedDialog(@NotNull List<BranchGuardChecker.Violation> violations,
                                   @NotNull RuleConfig config,
                                   @Nullable Locale locale) {
        String[] options = {
                BranchGuardBundle.messageFor(locale, "dialog.open.settings"),
                BranchGuardBundle.messageFor(locale, "dialog.cancel.commit")
        };
        int choice = Messages.showDialog(
                myProject,
                buildMessage(violations, config, locale),
                BranchGuardBundle.messageFor(locale, "dialog.title"),
                options,
                1,
                Messages.getErrorIcon());

        if (choice == 0) {
            ShowSettingsUtil.getInstance().showSettingsDialog(myProject, BranchGuardConfigurable.class);
        }
    }

    private String buildMessage(@NotNull List<BranchGuardChecker.Violation> violations,
                                @NotNull RuleConfig config,
                                @Nullable Locale locale) {
        StringBuilder sb = new StringBuilder("<html><body style='width:480px'>");
        sb.append("<b>").append(escape(BranchGuardBundle.messageFor(locale, "dialog.blocked.header")))
                .append("</b><br/><br/>");

        String custom = config.blockMessage == null ? "" : config.blockMessage.trim();
        if (!custom.isEmpty()) {
            sb.append(escape(custom).replace("\n", "<br/>")).append("<br/><br/>");
        }

        String branchLabel = BranchGuardBundle.messageFor(locale, "dialog.branch.label");
        sb.append(escape(BranchGuardBundle.messageFor(locale, "dialog.blocked.intro"))).append("<br/>");
        for (BranchGuardChecker.Violation violation : violations) {
            String item = BranchGuardBundle.messageFor(locale, "dialog.item.format",
                    escape(repoDirName(violation.rootPath())),
                    escape(branchLabel),
                    escape(shortenBranch(violation.branchName())),
                    escape(reasonText(violation.reason(), locale)));
            sb.append("• ").append(item).append("<br/>");
        }
        sb.append("<br/>")
                .append(escape(BranchGuardBundle.messageFor(locale, "dialog.blocked.footer")))
                .append("</body></html>");
        return sb.toString();
    }

    /** 只显示仓库目录名（最后一段路径），避免长绝对路径撑坏弹窗。 */
    private static String repoDirName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static String shortenBranch(String branch) {
        if (branch == null) {
            return "";
        }
        return StringUtil.shortenTextWithEllipsis(branch, MAX_BRANCH_CHARS, 6);
    }

    private static String reasonText(@NotNull RuleEvaluator.Reason reason, @Nullable Locale locale) {
        return switch (reason) {
            case PROTECTED_BRANCH -> BranchGuardBundle.messageFor(locale, "reason.protected");
            case BRANCH_NOT_ALLOWED -> BranchGuardBundle.messageFor(locale, "reason.not.allowed");
            case DETACHED_HEAD -> BranchGuardBundle.messageFor(locale, "reason.detached");
            default -> BranchGuardBundle.messageFor(locale, "reason.other");
        };
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
