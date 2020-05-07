package com.sahara.intellij.plugin.mvn;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * {@link java.util.ResourceBundle}/localization utils.
 *
 * @author liao
 * Create on 2020/5/7 14:30
 */
public class MessageBundle {
    private MessageBundle() {
    }

    /**
     * The {@link java.util.ResourceBundle} path.
     */
    @NonNls
    private static final String BUNDLE_NAME = "messages.bundle";

    /**
     * The {@link java.util.ResourceBundle} instance.
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    public static String message(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
        return AbstractBundle.message(BUNDLE, key, params);
    }
}
