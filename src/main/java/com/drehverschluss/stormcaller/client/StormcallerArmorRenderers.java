package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import net.minecraft.resources.ResourceLocation;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.spell_engine.rpg_series.item.Armor;

/**
 * Armor looks of the lightning robes: the robe model of Wizards (referenced by ID, not shipped), own textures with a
 * glow mask ({@code <texture>_glowmask.png}) and the trim textures of Wizards (also by ID).
 */
public final class StormcallerArmorRenderers {
    private static final ResourceLocation ROBE_MODEL = ResourceLocation.fromNamespaceAndPath("wizards", "geo/wizard_robes.geo.json");
    private static final ResourceLocation TRIM_T2 = ResourceLocation.fromNamespaceAndPath("wizards", "armor/trim/spec_robe_generic");
    private static final ResourceLocation TRIM_T3 = ResourceLocation.fromNamespaceAndPath("wizards", "armor/trim/netherite_spec_robe_generic");

    private StormcallerArmorRenderers() {}

    public static void register() {
        register(StormcallerItems.lightningRobe, "lightning_robe", TRIM_T2);
        register(StormcallerItems.netheriteLightningRobe, "netherite_lightning_robe", TRIM_T3);
    }

    private static void register(Armor.Set<?> set, String texture, ResourceLocation trim) {
        if (set == null) {
            Stormcaller.LOGGER.error("Armor set {} is not registered, no armor renderer", texture);
            return;
        }
        GeoArmorRenderer renderer = GeoArmorRenderer.of(ROBE_MODEL, Stormcaller.id("textures/armor/" + texture + ".png"))
                .glow()
                .trim(trim, false);
        ArmorRenderers.register(renderer, set.head, set.chest, set.legs, set.feet);
    }
}
