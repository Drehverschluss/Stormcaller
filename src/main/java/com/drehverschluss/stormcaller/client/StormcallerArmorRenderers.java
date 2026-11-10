package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import net.minecraft.resources.ResourceLocation;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.spell_engine.rpg_series.item.Armor;

/**
 * Armor looks of the lightning robes, each a model with a texture and a glow mask ({@code <texture>_glowmask.png}).
 * <p>
 * Tier 2 uses {@code stormcaller:geo/lightning_robe.geo.json}. Tier 3 has its own shape
 * ({@code netherite_lightning_robe.geo.json}: the tier 2 robe with netherite shoulder plates, bracers and a long hood
 * back) on the same UV layout. Fulgurite (tier 4) has its own model ({@code fulgurite_robe.geo.json}, glass plates with
 * glowing cracks, crystal prisms). Stormweave (tier 4) has its own model ({@code stormweave_robe.geo.json}, grey cloth
 * with a cloud mantle, silver edges and blue glowing bolts and threads).
 */
public final class StormcallerArmorRenderers {
    private static final ResourceLocation ROBE_MODEL = Stormcaller.id("geo/lightning_robe.geo.json");
    private static final ResourceLocation NETHERITE_ROBE_MODEL = Stormcaller.id("geo/netherite_lightning_robe.geo.json");
    private static final ResourceLocation FULGURITE_ROBE_MODEL = Stormcaller.id("geo/fulgurite_robe.geo.json");
    private static final ResourceLocation STORMWEAVE_ROBE_MODEL = Stormcaller.id("geo/stormweave_robe.geo.json");

    private StormcallerArmorRenderers() {}

    public static void register() {
        register(StormcallerItems.lightningRobe, ROBE_MODEL, "lightning_robe");
        register(StormcallerItems.netheriteLightningRobe, NETHERITE_ROBE_MODEL, "netherite_lightning_robe");
        register(StormcallerItems.fulguriteRobe, FULGURITE_ROBE_MODEL, "fulgurite_robe");
        register(StormcallerItems.stormweaveRobe, STORMWEAVE_ROBE_MODEL, "stormweave_robe");
    }

    private static void register(Armor.Set<?> set, ResourceLocation model, String texture) {
        if (set == null) {
            Stormcaller.LOGGER.error("Armor set {} is not registered, no armor renderer", texture);
            return;
        }
        GeoArmorRenderer renderer = GeoArmorRenderer.of(model, Stormcaller.id("textures/armor/" + texture + ".png")).glow();
        ArmorRenderers.register(renderer, set.head, set.chest, set.legs, set.feet);
    }
}
