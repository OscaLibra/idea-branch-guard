package com.acme.branchguard.settings;

import com.acme.branchguard.BranchGuardBundle;
import com.acme.branchguard.rule.RuleConfig;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class BranchGuardConfigurable implements Configurable {

    private final Project myProject;

    private JPanel myPanel;
    private JBCheckBox myEnabled;
    private JComboBox<RuleConfig.Mode> myMode;
    private JBTextArea myPatterns;
    private JBCheckBox myAllowMerge;
    private JBCheckBox myBlockDetached;
    private JBTextArea myMessage;
    private JComboBox<BranchGuardSettings.MessageLanguage> myLanguage;
    private JBLabel myWarning;

    public BranchGuardConfigurable(@NotNull Project project) {
        myProject = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return BranchGuardBundle.message("settings.display.name");
    }

    @Override
    public @Nullable JComponent createComponent() {
        myEnabled = new JBCheckBox(BranchGuardBundle.message("settings.enabled"));
        myWarning = new JBLabel(BranchGuardBundle.message("settings.disabled.warning"));
        myWarning.setForeground(JBColor.RED);

        myMode = new ComboBox<>(RuleConfig.Mode.values());
        myMode.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value == RuleConfig.Mode.WHITELIST) {
                    setText(BranchGuardBundle.message("settings.mode.whitelist"));
                } else if (value == RuleConfig.Mode.BLACKLIST) {
                    setText(BranchGuardBundle.message("settings.mode.blacklist"));
                }
                return this;
            }
        });

        myPatterns = new JBTextArea(8, 40);
        myPatterns.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        myAllowMerge = new JBCheckBox(BranchGuardBundle.message("settings.allow.merge"));
        myBlockDetached = new JBCheckBox(BranchGuardBundle.message("settings.block.detached"));
        myMessage = new JBTextArea(3, 40);

        myLanguage = new ComboBox<>(BranchGuardSettings.MessageLanguage.values());
        myLanguage.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value == BranchGuardSettings.MessageLanguage.FOLLOW_IDE) {
                    setText(BranchGuardBundle.message("settings.message.language.follow"));
                } else if (value == BranchGuardSettings.MessageLanguage.ZH_CN) {
                    setText(BranchGuardBundle.message("settings.message.language.zh"));
                } else if (value == BranchGuardSettings.MessageLanguage.EN) {
                    setText(BranchGuardBundle.message("settings.message.language.en"));
                }
                return this;
            }
        });

        myPanel = FormBuilder.createFormBuilder()
                .addComponent(myEnabled)
                .addComponent(myWarning)
                .addComponent(new TitledSeparator(BranchGuardBundle.message("settings.section.rules")))
                .addLabeledComponent(new JBLabel(BranchGuardBundle.message("settings.mode")), myMode, 1, false)
                .addLabeledComponent(new JBLabel(BranchGuardBundle.message("settings.patterns")),
                        new JBScrollPane(myPatterns), 1, true)
                .addComponent(myAllowMerge)
                .addComponent(myBlockDetached)
                .addComponent(new TitledSeparator(BranchGuardBundle.message("settings.section.message")))
                .addLabeledComponent(new JBLabel(BranchGuardBundle.message("settings.custom.message")),
                        new JBScrollPane(myMessage), 1, true)
                .addLabeledComponent(new JBLabel(BranchGuardBundle.message("settings.message.language")),
                        myLanguage, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        myEnabled.addActionListener(e -> updateWarningVisibility());
        return myPanel;
    }

    private void updateWarningVisibility() {
        if (myWarning != null) {
            myWarning.setVisible(!myEnabled.isSelected());
        }
    }

    @Override
    public boolean isModified() {
        BranchGuardSettings.State state = BranchGuardSettings.getInstance(myProject).getState();
        return state == null || !collectFromUi().equals(state);
    }

    @Override
    public void apply() {
        BranchGuardSettings.State state = BranchGuardSettings.getInstance(myProject).getState();
        if (state == null || myEnabled == null) {
            return;
        }
        BranchGuardSettings.State ui = collectFromUi();
        state.enabled = ui.enabled;
        state.mode = ui.mode;
        state.patterns = ui.patterns;
        state.allowMergeCommits = ui.allowMergeCommits;
        state.blockDetachedHead = ui.blockDetachedHead;
        state.blockMessage = ui.blockMessage;
        state.messageLanguage = ui.messageLanguage;
    }

    @Override
    public void reset() {
        BranchGuardSettings.State state = BranchGuardSettings.getInstance(myProject).getState();
        if (state == null || myEnabled == null) {
            return;
        }
        myEnabled.setSelected(state.enabled);
        myMode.setSelectedItem(state.mode);
        myPatterns.setText(state.patterns == null ? "" : String.join("\n", state.patterns));
        myAllowMerge.setSelected(state.allowMergeCommits);
        myBlockDetached.setSelected(state.blockDetachedHead);
        myMessage.setText(state.blockMessage == null ? "" : state.blockMessage);
        myLanguage.setSelectedItem(state.messageLanguage == null
                ? BranchGuardSettings.MessageLanguage.FOLLOW_IDE : state.messageLanguage);
        updateWarningVisibility();
    }

    @Override
    public void disposeUIResources() {
        myPanel = null;
        myEnabled = null;
        myMode = null;
        myPatterns = null;
        myAllowMerge = null;
        myBlockDetached = null;
        myMessage = null;
        myLanguage = null;
        myWarning = null;
    }

    private BranchGuardSettings.State collectFromUi() {
        BranchGuardSettings.State state = new BranchGuardSettings.State();
        state.enabled = myEnabled.isSelected();
        state.mode = (RuleConfig.Mode) myMode.getSelectedItem();
        state.patterns = parsePatterns(myPatterns.getText());
        state.allowMergeCommits = myAllowMerge.isSelected();
        state.blockDetachedHead = myBlockDetached.isSelected();
        state.blockMessage = myMessage.getText().trim();
        state.messageLanguage = (BranchGuardSettings.MessageLanguage) myLanguage.getSelectedItem();
        return state;
    }

    private static List<String> parsePatterns(String text) {
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toList());
    }
}
