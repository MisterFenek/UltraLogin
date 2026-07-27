package com.ultralogin;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.ultralogin.config.UltraLoginConfig;
import net.neoforged.fml.config.IConfigSpec;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Loads {@link UltraLoginConfig#SPEC} with default values (outside a running
 * game) so config-dependent classes can be unit tested.
 * <p>
 * {@code IConfigSpec.ILoadedConfig} is sealed with a single package-private
 * record implementation ({@code net.neoforged.fml.config.LoadedConfig}), so we
 * instantiate it reflectively with a null path/modConfig (never saved in tests).
 */
public final class TestConfig {

    private static CommentedConfig backing;

    private TestConfig() {
    }

    public static synchronized void load() {
        if (UltraLoginConfig.SPEC.isLoaded()) {
            return;
        }
        try {
            backing = CommentedConfig.inMemory();
            UltraLoginConfig.SPEC.correct(backing);

            Class<?> loadedConfigCls = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> ctor = loadedConfigCls.getDeclaredConstructor(
                    CommentedConfig.class, Path.class, Class.forName("net.neoforged.fml.config.ModConfig"));
            ctor.setAccessible(true);
            IConfigSpec.ILoadedConfig loaded =
                    (IConfigSpec.ILoadedConfig) ctor.newInstance(backing, null, null);
            UltraLoginConfig.SPEC.acceptConfig(loaded);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load test config", e);
        }
        // Fast BCrypt in tests; production default stays 12.
        set("general.bcryptCost", 4);
    }

    /** Overrides a config path (e.g. "session.enabled") and clears value caches. */
    public static synchronized void set(String path, Object value) {
        backing.set(Arrays.asList(path.split("\\.")), value);
        UltraLoginConfig.SPEC.afterReload();
    }
}
