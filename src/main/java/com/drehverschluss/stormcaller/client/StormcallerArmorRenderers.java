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
 * Default look since 0.3.0, in the style of the Wizards robes: a robe with a pointed wizard hat. Tier 2 and tier 3 share
 * one shape ({@code stormcaller:geo/lightning_robe.geo.json} and the identical {@code netherite_lightning_robe.geo.json})
 * and differ only by their texture (storm violet, tier 3 with netherite grey). Fulgurite (tier 4,
 * {@code fulgurite_robe.geo.json}) and Stormweave (tier 4, {@code stormweave_robe.geo.json}) have their own models with
 * a richer hat that bends backwards: Fulgurite in lightning yellow with a crystal crown, Stormweave in storm grey with a
 * silver brim, a cloud wreath and calmer shoulders. Only the lightning bolts, runes and crystals glow.
 * <p>
 * Every set has its own model id, so a resource pack can change each one on its own. The look of 0.1/0.2 comes as the
 * built-in client pack {@code stormcaller_original_armor} ({@link OriginalArmorPack}), which replaces the same model,
 * texture, glow mask and icon files.
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
