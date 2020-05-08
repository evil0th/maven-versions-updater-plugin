package com.sahara.intellij.plugin.mvn;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * localization utils.
 *
 * @author liao
 * Create on 2020/5/7 14:30
 */
public class MavenVersionUpdateBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.MavenVersionUpdateBundle";
    private static final MavenVersionUpdateBundle INSTANCE = new MavenVersionUpdateBundle();

    private MavenVersionUpdateBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    @NotNull
    public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
