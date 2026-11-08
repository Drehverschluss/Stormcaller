package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Lightning bodies of flying spell projectiles: a short flickering bolt drawn behind the projectile, plus a small glow
 * at its head. Spells listed here should have no {@code composite_model}, the bolt is the projectile.
 */
public final class ProjectileTrails {
    /**
     * @param length     length of the bolt in blocks
     * @param coreWidth  width of the white core
     * @param glowWidth  width of the yellow glow
     * @param jag        zigzag amplitude relative to the length
     * @param headGlow   radius of the glow at the head
     */
    public record Trail(float length, float coreWidth, float glowWidth, float jag, float headGlow) {}

    private static final Map<ResourceLocation, Trail> TRAILS = new HashMap<>();

    private ProjectileTrails() {}

    /** Client: gives the projectiles of a spell a lightning body. */
    public static void register(ResourceLocation spellId, Trail trail) {
        TRAILS.put(spellId, trail);
    }

    @Nullable
    static Trail get(ResourceLocation spellId) {
        return TRAILS.get(spellId);
    }

    static boolean isEmpty() {
        return TRAILS.isEmpty();
    }

    static void registerDefaults() {
        register(Stormcaller.id("spark"), new Trail(0.9F, 0.03F, 0.15F, 0.12F, 0.22F));
        register(Stormcaller.id("lightning_bolt"), new Trail(2.6F, 0.055F, 0.28F, 0.09F, 0.35F));
        register(Stormcaller.id("chain_lightning"), new Trail(1.8F, 0.045F, 0.24F, 0.11F, 0.3F));
    }
}
