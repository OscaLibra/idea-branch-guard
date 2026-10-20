package com.acme.branchguard.settings;

import com.acme.branchguard.rule.RuleConfig;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 项目级配置，持久化到 <code>&lt;project&gt;/.idea/branch-guard.xml</code>。
 */
@State(name = "BranchGuardSettings", storages = @Storage("branch-guard.xml"))
public final class BranchGuardSettings implements PersistentStateComponent<BranchGuardSettings.State> {

    public enum MessageLanguage {FOLLOW_IDE, ZH_CN, EN}

    public static final class State {

        public boolean enabled = true;
        public RuleConfig.Mode mode = RuleConfig.Mode.WHITELIST;
        public List<String> patterns = RuleConfig.defaultPatterns();
        public boolean allowMergeCommits = true;
        public boolean blockDetachedHead = true;
        public String blockMessage = "";
        /** 仅影响阻断弹窗文案，不影响设置页。 */
        public MessageLanguage messageLanguage = MessageLanguage.FOLLOW_IDE;

        public RuleConfig toRuleConfig() {
            RuleConfig config = new RuleConfig();
            config.enabled = enabled;
            config.mode = mode == null ? RuleConfig.Mode.WHITELIST : mode;
            config.patterns = patterns == null ? new ArrayList<>() : new ArrayList<>(patterns);
            config.allowMergeCommits = allowMergeCommits;
            config.blockDetachedHead = blockDetachedHead;
            config.blockMessage = blockMessage == null ? "" : blockMessage;
            return config;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof State)) return false;
            State other = (State) o;
            return enabled == other.enabled
                    && allowMergeCommits == other.allowMergeCommits
                    && blockDetachedHead == other.blockDetachedHead
                    && mode == other.mode
                    && messageLanguage == other.messageLanguage
                    && Objects.equals(patterns, other.patterns)
                    && Objects.equals(blockMessage, other.blockMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, mode, patterns, allowMergeCommits, blockDetachedHead,
                    blockMessage, messageLanguage);
        }
    }

    private State myState = new State();

    public static @NotNull BranchGuardSettings getInstance(@NotNull Project project) {
        return project.getService(BranchGuardSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }
}
