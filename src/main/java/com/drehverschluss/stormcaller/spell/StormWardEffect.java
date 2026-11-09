package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Storm Ward (skill tree, passive 3 side B): absorbs damage like the vanilla absorption effect, {@link #PER_STAGE}
 * absorption per stage (the stage comes from the Lightning Spell Power, {@code amplifier_power_multiplier} of the stash).
 * The effect ends as soon as the absorption is used up. While it holds, the stash trigger of the spell shocks melee
 * attackers ({@code data/stormcaller/spell/storm_ward.json}).
 */
public class StormWardEffect extends MobEffect {
    /** Absorption per stage (1 heart). */
    public static final float PER_STAGE = 2.0F;

    public StormWardEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        // absorption is capped by this attribute
        addAttributeModifier(Attributes.MAX_ABSORPTION, Stormcaller.id("storm_ward"), PER_STAGE,
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), PER_STAGE * (amplifier + 1)));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /** Ends the effect when the absorption is gone (server; the client keeps it until the server removes it). */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return entity.level().isClientSide() || entity.getAbsorptionAmount() > 0.0F;
    }
}
