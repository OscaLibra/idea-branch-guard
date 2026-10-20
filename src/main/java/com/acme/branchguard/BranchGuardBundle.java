package com.acme.branchguard;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.PropertyKey;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 文案入口。
 * <p>默认（locale 为 null）跟随 IDE 界面语言；传入 locale 可强制指定语言，
 * 用于「IDE 英文但弹窗要中文」这类场景。</p>
 */
public final class BranchGuardBundle {

    private static final String BUNDLE = "messages.BranchGuardBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(BranchGuardBundle.class, BUNDLE);

    private BranchGuardBundle() {
    }

    public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static String messageFor(Locale locale, @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        if (locale == null) {
            return message(key, params);
        }
        try {
            // 关闭「回退到系统默认语言」：否则在中文系统上请求 EN 会拿到 zh_CN 资源，
            // 因为 JDK 找不到 _en.properties 时会先回退到默认 Locale，再回退到 base bundle。
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale,
                    BranchGuardBundle.class.getClassLoader(),
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            String pattern = bundle.getString(key);
            return params.length == 0 ? pattern : MessageFormat.format(pattern, params);
        } catch (MissingResourceException e) {
            // 资源缺失时退回 IDE 语言，避免弹窗因取词失败而崩溃
            return message(key, params);
        }
    }
}
