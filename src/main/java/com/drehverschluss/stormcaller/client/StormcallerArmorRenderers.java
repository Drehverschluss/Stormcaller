package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import net.minecraft.resources.ResourceLocation;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.spell_engine.rpg_series.item.Armor;

/**
 * Armor looks of the lightning robes: one own model ({@code stormcaller:geo/lightning_robe.geo.json}) for tier 2 and
 * tier 3, a texture per tier with a glow mask ({@code <texture>_glowmask.png}).
 * <p>
 * Tier 4 (Fulgurite, Stormweave) uses the same model with recolored textures for now (placeholder until the own
 * models of phase 6R1/6R2).
 */
public final class StormcallerArmorRenderers {
    private static final ResourceLocation ROBE_MODEL = Stormcaller.id("geo/lightning_robe.geo.json");

    private StormcallerArmorRenderers() {}

    public static void register() {
        register(StormcallerItems.lightningRobe, "lightning_robe");
        register(StormcallerItems.netheriteLightningRobe, "netherite_lightning_robe");
        register(StormcallerItems.fulguriteRobe, "fulgurite_robe");
        register(StormcallerItems.stormweaveRobe, "stormweave_robe");
    }

    private static void register(Armor.Set<?> set, String texture) {
        if (set == null) {
            Stormcaller.LOGGER.error("Armor set {} is not registered, no armor renderer", texture);
            return;
        }
        GeoArmorRenderer renderer = GeoArmorRenderer.of(ROBE_MODEL, Stormcaller.id("textures/armor/" + texture + ".png")).glow();
        ArmorRenderers.register(renderer, set.head, set.chest, set.legs, set.feet);
    }
}
