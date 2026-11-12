package com.drehverschluss.stormcaller.trinket;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.compat.curios.CuriosCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.effect.CustomStatusEffect;
import net.spell_engine.api.effect.Effects;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.EffectConfig;
import net.spell_power.api.SpellPowerMechanics;
import net.spell_power.api.SpellSchools;
import net.tiny_config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Rings, amulets and relics (phase 6B). Only registered when Curios is loaded ({@link CuriosCompat}); this class itself
 * has no Curios reference and is always loaded (ids, config, status effects).
 * <ul>
 *   <li>Stormheart Signet / Amulet (haste) and Thunderstrike Band / Pendant (spell critical damage): unique jewelry
 *   (slots {@code ring} / {@code necklace}), attributes from {@link #CONFIG}.</li>
 *   <li>Static Lodestone, Storm Orb, Caged Ball Lightning: relics (slot {@code spell_trinket}), each with one spell in its
 *   spell container ({@code data/stormcaller/spell/<spell>.json}). The status effects of two of them are registered here
 *   in any case, their modifiers come from {@link #CONFIG}.</li>
 * </ul>
 * All of them are loot only (tags {@code rpg_series:loot_tier/*}), no recipes.
 */
public final class StormcallerTrinkets {
    public static final ResourceLocation STORMHEART_RING = Stormcaller.id("stormheart_ring");
    public static final ResourceLocation STORMHEART_NECKLACE = Stormcaller.id("stormheart_necklace");
    public static final ResourceLocation THUNDERSTRIKE_RING = Stormcaller.id("thunderstrike_ring");
    public static final ResourceLocation THUNDERSTRIKE_NECKLACE = Stormcaller.id("thunderstrike_necklace");
    public static final ResourceLocation STATIC_LODESTONE = Stormcaller.id("static_lodestone");
    public static final ResourceLocation STORM_ORB = Stormcaller.id("storm_orb");
    public static final ResourceLocation CAGED_BALL_LIGHTNING = Stormcaller.id("caged_ball_lightning");

    /** Spells in the containers of the relics. */
    public static final ResourceLocation STATIC_LODESTONE_SPELL = Stormcaller.id("static_lodestone");
    public static final ResourceLocation STORM_ORB_SPELL = Stormcaller.id("storm_orb");
    public static final ResourceLocation BALL_LIGHTNING_SPELL = Stormcaller.id("ball_lightning");

    // Defaults of trinkets.json
    private static final float UNIQUE_LIGHTNING = 0.08F;      // like the school uniques of Jewelry
    private static final float UNIQUE_SHOCK_CHANCE = 0.03F;
    private static final float UNIQUE_HASTE = 0.03F;           // Stormheart: speed
    private static final float UNIQUE_CRIT_DAMAGE = 0.1F;      // Thunderstrike: force, like the Fire/Frost uniques of Jewelry
    private static final float STATIC_CHARGE_LIGHTNING = 0.15F; // like the lesser procs of Relics
    private static final float STATIC_CHARGE_SHOCK_CHANCE = 0.05F;
    private static final float STORM_POWER_LIGHTNING = 0.2F;    // like the orbs of Relics

    public enum Kind {
        /** Ring or amulet: attributes, no spell. */
        JEWELRY,
        /** Relic: one spell in the container, cannot be taken off while that spell is on cooldown. */
        RELIC
    }

    /**
     * One trinket item.
     * @param slot Curios slot (only for the record; the slot comes from the tags {@code curios:<slot>})
     * @param spell spell of the container, null for jewelry
     */
    public record Def(ResourceLocation id, Kind kind, String slot, Rarity rarity, boolean fireResistant,
                      ResourceLocation spell, EffectConfig defaults) {
    }

    public static final List<Def> ITEMS = List.of(
            new Def(STORMHEART_RING, Kind.JEWELRY, "ring", Rarity.RARE, true, null, uniqueDefaults()),
            new Def(STORMHEART_NECKLACE, Kind.JEWELRY, "necklace", Rarity.RARE, true, null, uniqueDefaults()),
            new Def(THUNDERSTRIKE_RING, Kind.JEWELRY, "ring", Rarity.RARE, true, null, thunderstrikeDefaults()),
            new Def(THUNDERSTRIKE_NECKLACE, Kind.JEWELRY, "necklace", Rarity.RARE, true, null, thunderstrikeDefaults()),
            new Def(STATIC_LODESTONE, Kind.RELIC, "spell_trinket", Rarity.COMMON, false, STATIC_LODESTONE_SPELL, EffectConfig.EMPTY),
            new Def(STORM_ORB, Kind.RELIC, "spell_trinket", Rarity.UNCOMMON, false, STORM_ORB_SPELL, EffectConfig.EMPTY),
            new Def(CAGED_BALL_LIGHTNING, Kind.RELIC, "spell_trinket", Rarity.RARE, false, BALL_LIGHTNING_SPELL, EffectConfig.EMPTY));

    /** Static Lodestone: lightning spell power and shock chance for a few seconds. */
    public static final Effects.Entry STATIC_CHARGE = new Effects.Entry(Stormcaller.id("static_charge"), "Static Charge",
            "Increases lightning spell power and shock chance",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0xFFE066),
            new EffectConfig(List.of(
                    AttributeModifier.multiply(SpellSchools.LIGHTNING.id, STATIC_CHARGE_LIGHTNING),
                    AttributeModifier.multiply(StormcallerAttributes.SHOCK_CHANCE.getId(), STATIC_CHARGE_SHOCK_CHANCE))));

    /** Storm Orb: lightning spell power for a few seconds. */
    public static final Effects.Entry STORM_POWER = new Effects.Entry(Stormcaller.id("storm_power"), "Storm Power",
            "Increases lightning spell power",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0x8FB8FF),
            new EffectConfig(List.of(AttributeModifier.multiply(SpellSchools.LIGHTNING.id, STORM_POWER_LIGHTNING))));

    /**
     * {@code config/stormcaller/trinkets.json}: attributes of the jewelry ({@code items}, keyed by item id; relics may get
     * attributes here too), the modifiers of the relic effects ({@code effects}) and the movement, lifetime, pulse, range
     * and target count of the ball lightning ({@code ball_lightning}, {@link BallLightning.Config}). Chances, durations,
     * cooldowns, damage and shock chances are in the spell files. Read once at startup, changes need a restart. Keep server and client equal (the tooltip reads
     * the client file).
     */
    public static class Config {
        public LinkedHashMap<String, EffectConfig> items = new LinkedHashMap<>();
        public LinkedHashMap<String, EffectConfig> effects = new LinkedHashMap<>();
        public BallLightning.Config ball_lightning = new BallLightning.Config();
    }

    public static final ConfigManager<Config> CONFIG = new ConfigManager<>("trinkets", new Config())
            .builder()
            .setDirectory(Stormcaller.MODID)
            .sanitize(true)
            .build();

    private StormcallerTrinkets() {}

    /** Second unique pair: lightning and shock chance like Stormheart, spell critical damage instead of haste. */
    private static EffectConfig thunderstrikeDefaults() {
        return new EffectConfig(List.of(
                AttributeModifier.multiply(SpellSchools.LIGHTNING.id, UNIQUE_LIGHTNING),
                AttributeModifier.multiply(StormcallerAttributes.SHOCK_CHANCE.getId(), UNIQUE_SHOCK_CHANCE),
                AttributeModifier.multiply(SpellPowerMechanics.CRITICAL_DAMAGE.id, UNIQUE_CRIT_DAMAGE)));
    }

    private static EffectConfig uniqueDefaults() {
        return new EffectConfig(List.of(
                AttributeModifier.multiply(SpellSchools.LIGHTNING.id, UNIQUE_LIGHTNING),
                AttributeModifier.multiply(StormcallerAttributes.SHOCK_CHANCE.getId(), UNIQUE_SHOCK_CHANCE),
                AttributeModifier.multiply(SpellPowerMechanics.HASTE.id, UNIQUE_HASTE)));
    }

    /** Mod constructor: reads trinkets.json and adds missing item entries (jewelry only). */
    public static void init() {
        CONFIG.refresh();
        if (CONFIG.value.ball_lightning == null) {
            CONFIG.value.ball_lightning = new BallLightning.Config();
        }
        for (Def def : ITEMS) {
            if (def.kind() == Kind.JEWELRY) {
                CONFIG.value.items.putIfAbsent(def.id().toString(), def.defaults());
            }
        }
        CONFIG.save();
    }

    /** Attributes of a trinket from the config (empty if none). */
    public static List<AttributeModifier> attributes(Def def) {
        EffectConfig config = CONFIG.value.items.get(def.id().toString());
        return config != null ? config.selectedAttributes() : List.of();
    }

    /** Mod bus: the two relic effects, always (harmless without Curios), values from {@link #CONFIG}. */
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.MOB_EFFECT)) {
            return;
        }
        Effects.register(List.of(STATIC_CHARGE, STORM_POWER), CONFIG.value.effects);
        CONFIG.save();
    }

    /** Called while the items are registered: only with Curios. */
    public static void registerItems() {
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.registerItems();
        }
    }
}
