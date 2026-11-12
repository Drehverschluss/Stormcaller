package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.effect.CustomStatusEffect;
import net.spell_engine.api.effect.Effects;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.ConfigFile;
import net.spell_engine.rpg_series.config.EffectConfig;
import net.tiny_config.ConfigManager;

/**
 * Static Shield (tier 3): the status effect {@code stormcaller:static_shield} and the entity predicate that limits the
 * counter-strike to melee attackers.
 * <p>
 * The spell itself is data ({@code data/stormcaller/spell/static_shield.json}): it stashes this effect on the caster,
 * a {@code DAMAGE_TAKEN} trigger performs the spell impacts on the attacker. The trigger only fires for damage types
 * outside {@code #stormcaller:static_shield/ignored} (projectiles, explosions, magic, spells, thorns) and for attackers
 * that pass {@link #MELEE_RANGE}.
 */
public final class StaticShield {
    public static final ResourceLocation EFFECT_ID = Stormcaller.id("static_shield");
    /** Electric yellow (Spell Engine Color.ELECTRIC). */
    public static final int COLOR = 0xFFFF66;
    /** Default of the damage taken while the shield is up: -10 %. Packs change it in {@link #EFFECTS_CONFIG}. */
    public static final float DAMAGE_TAKEN = -0.1F;

    /**
     * Entity predicate {@code stormcaller:melee_range} for {@code target_conditions} of spell triggers: true if the
     * gap between the hitboxes of the tested entity and the other entity is at most the parameter (blocks, default 4).
     * Never true for the entity itself.
     */
    public static final ResourceLocation MELEE_RANGE = Stormcaller.id("melee_range");
    public static final double DEFAULT_MELEE_RANGE = 4.0;

    public static final Effects.Entry EFFECT = new Effects.Entry(EFFECT_ID, "Static Shield",
            "Strikes melee attackers with lightning and reduces damage taken",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, COLOR),
            new EffectConfig(List.of(new AttributeModifier("spell_engine:damage_taken", DAMAGE_TAKEN, Operation.ADD_MULTIPLIED_BASE))));

    /**
     * {@code config/stormcaller/effects.json}: attribute modifiers of the Stormcaller status effects, same format as
     * {@code config/wizards/effects.json}. Read once when the effects are registered, so changes need a restart.
     */
    public static final ConfigManager<ConfigFile.Effects> EFFECTS_CONFIG = new ConfigManager<>("effects", new ConfigFile.Effects())
            .builder()
            .setDirectory(Stormcaller.MODID)
            .sanitize(true)
            .build();

    private StaticShield() {}

    public static void init() {
        SpellEntityPredicates.register(MELEE_RANGE, args -> withinGap(args.entity(), args.other(), parseRange(args.param())));
    }

    /**
     * Mod bus: registers the configurable effects like Wizards does (Spell Engine {@link Effects#register}): Static
     * Shield and Galvanic Rush ({@link GalvanicRush}, skill tree), both in {@link #EFFECTS_CONFIG}.
     */
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.MOB_EFFECT)) {
            return;
        }
        // synchronized to all tracking clients, they draw the arcs around the body (fx.client.ShieldAura)
        Synchronized.configure(EFFECT.effect, true);
        EFFECTS_CONFIG.refresh();
        // takes the values from the file (or writes the defaults into it)
        Effects.register(List.of(EFFECT, GalvanicRush.EFFECT), EFFECTS_CONFIG.value.effects);
        EFFECTS_CONFIG.save();
    }

    public static MobEffect effect() {
        return EFFECT.effect;
    }

    static boolean withinGap(Entity tested, Entity other, double range) {
        if (tested == null || other == null || tested == other) {
            return false;
        }
        return gap(tested.getBoundingBox(), other.getBoundingBox()) <= range;
    }

    /** Shortest distance between two boxes, 0 if they touch or overlap. */
    static double gap(AABB a, AABB b) {
        double dx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dy = Math.max(0.0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double dz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double parseRange(String param) {
        if (param == null || param.isBlank()) {
            return DEFAULT_MELEE_RANGE;
        }
        try {
            return Double.parseDouble(param.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_MELEE_RANGE;
        }
    }
}
