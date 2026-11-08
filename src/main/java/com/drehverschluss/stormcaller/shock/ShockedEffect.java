package com.drehverschluss.stormcaller.shock;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Shocked" / "Geschockt". The amplifier is the stage minus one. The extra lightning damage is applied in
 * {@link ShockHandler#onIncomingDamage}, the effect itself only shows small sparks.
 */
public class ShockedEffect extends MobEffect {
    /** Electric yellow (Spell Engine Color.ELECTRIC). */
    public static final int COLOR = 0xFFFF66;

    public ShockedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 8 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            int count = 2 + amplifier;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    count, entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.35, entity.getBbWidth() * 0.5, 0.05);
        }
        return true;
    }
}
