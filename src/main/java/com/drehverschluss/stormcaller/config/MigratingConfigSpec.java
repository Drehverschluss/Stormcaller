package com.drehverschluss.stormcaller.config;

import com.drehverschluss.stormcaller.Stormcaller;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import java.util.List;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * The server config spec of Stormcaller, with one step before NeoForge corrects an existing file: renamed keys keep their
 * old value.
 * <p>
 * NeoForge drops unknown keys and adds missing ones with their default when it corrects a file ({@link #correct}). A key
 * that was renamed would lose its value that way. So the old value is copied to the new key first, then the normal
 * correction runs (and removes the old key). Everything else goes straight to the {@link ModConfigSpec}.
 * <p>
 * Renamed so far: {@code [ricochet] fix_all_spells} → {@code fix_ricochet_all_spells} (0.3.0).
 */
final class MigratingConfigSpec implements IConfigSpec {
    private final ModConfigSpec spec;

    MigratingConfigSpec(ModConfigSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean isEmpty() {
        return spec.isEmpty();
    }

    @Override
    public void validateSpec(ModConfig config) {
        spec.validateSpec(config);
    }

    @Override
    public boolean isCorrect(UnmodifiableCommentedConfig config) {
        return spec.isCorrect(config);
    }

    @Override
    public void correct(CommentedConfig config) {
        rename(config, StormcallerConfig.RICOCHET_SECTION, StormcallerConfig.RICOCHET_FIX_ALL_SPELLS_OLD_KEY,
                StormcallerConfig.RICOCHET_FIX_ALL_SPELLS_KEY);
        spec.correct(config);
    }

    @Override
    public void acceptConfig(@Nullable ILoadedConfig config) {
        spec.acceptConfig(config);
    }

    /** Copies the value of an old key to its new name, unless the new key is already there. */
    private static void rename(CommentedConfig config, String section, String oldKey, String newKey) {
        List<String> oldPath = List.of(section, oldKey);
        List<String> newPath = List.of(section, newKey);
        Object old = config.get(oldPath);
        if (old == null) {
            return;
        }
        if (config.get(newPath) != null) {
            Stormcaller.LOGGER.info("Stormcaller config: [{}] {} is now called {}; both are set, keeping {} = {}",
                    section, oldKey, newKey, newKey, config.<Object>get(newPath));
            return;
        }
        config.set(newPath, old);
        Stormcaller.LOGGER.info("Stormcaller config: [{}] {} is now called {}, took over the old value {}", section, oldKey, newKey, old);
    }
}
