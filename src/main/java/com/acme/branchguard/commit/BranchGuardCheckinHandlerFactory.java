package com.acme.branchguard.commit;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;

public final class BranchGuardCheckinHandlerFactory extends CheckinHandlerFactory {

    @Override
    public @NotNull CheckinHandler createHandler(@NotNull CheckinProjectPanel panel,
                                                 @NotNull CommitContext commitContext) {
        Project project = panel.getProject();
        if (project == null || project.isDisposed()) {
            return CheckinHandler.DUMMY;
        }

        // 注意：不要在这里判断 GitRepositoryManager.getRepositories() 是否为空。
        // createHandler 可能在项目刚打开、Git 仓库尚未初始化时被调用，
        // 此时返回 DUMMY 会让整个会话都不再校验（重启后拦截失效的根因）。
        // 仓库判定放到 beforeCheckin 里，每次提交实时取。
        return new BranchGuardCheckinHandler(project, panel);
    }
}
