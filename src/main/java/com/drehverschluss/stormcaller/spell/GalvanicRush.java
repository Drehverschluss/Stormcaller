package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.spell_engine.api.effect.CustomStatusEffect;
import net.spell_engine.api.effect.Effects;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.EffectConfig;

/**
 * Galvanic Rush (skill tree, passive 1 side B): status effect {@code stormcaller:galvanic_rush}, more movement speed
 * per stage. The passive spell ({@code data/stormcaller/spell/galvanic_rush.json}) adds one stage per proc, up to its
 * {@code amplifier_cap}. The speed per stage comes from {@code config/stormcaller/effects.json} (registered together
 * with Static Shield, see {@link StaticShield#onRegister}); vanilla multiplies it with the stage.
 */
public final class GalvanicRush {
    public static final ResourceLocation EFFECT_ID = Stormcaller.id("galvanic_rush");
    /** Default speed per stage: +5 % of the base speed (ADD_MULTIPLIED_BASE, Spell Engine shows it as "+5%"). */
    public static final float SPEED_PER_STAGE = 0.05F;

    public static final Effects.Entry EFFECT = new Effects.Entry(EFFECT_ID, "Galvanic Rush",
            "Moving faster, charged by your own lightning",
            new CustomStatusEffect(MobEffectCategory.BENEFICIAL, StaticShield.COLOR),
            new EffectConfig(List.of(new AttributeModifier("minecraft:generic.movement_speed", SPEED_PER_STAGE,
                    Operation.ADD_MULTIPLIED_BASE))));

    private GalvanicRush() {}
}
