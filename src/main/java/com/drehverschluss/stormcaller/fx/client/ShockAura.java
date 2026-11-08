package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.shock.StormcallerEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.CustomParticleStatusEffect;

/**
 * "Shocked" on the client: small arcs on the body of the target, more often and stronger with each stage, so the shock
 * can be seen building up in a fight (1 stage: rare and thin, 5 stages: clear).
 * <p>
 * Spell Engine synchronizes the effect (with its amplifier) to all tracking clients and calls the spawner every client
 * tick. Limits for many shocked entities: nothing beyond {@link #MAX_DISTANCE} blocks, half the rate beyond
 * {@link #HALF_RATE_DISTANCE}, and at most {@link #budget()} shock arcs alive at once (they never push out the arcs of
 * spells). With arcs turned off in the client config, a few vanilla sparks show the effect instead.
 */
public final class ShockAura {
    /** Ticks between two arcs per stage (index = stage). */
    private static final int[] INTERVAL = {24, 24, 16, 11, 8, 5};
    private static final int MAX_STAGE = 5;
    private static final double MAX_DISTANCE = 48.0;
    private static final double HALF_RATE_DISTANCE = 24.0;
    private static final RandomSource RANDOM = RandomSource.create();

    private ShockAura() {}

    public static void register() {
        CustomParticleStatusEffect.register(StormcallerEffects.SHOCKED.get(), ShockAura::spawn);
    }

    /** Most shock arcs alive at once: half of max_arcs, between 6 and 40. */
    static int budget() {
        return Mth.clamp(StormcallerClientConfig.maxArcs() / 2, 6, 40);
    }

    private static void spawn(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() || entity.isInvisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return;
        }
        double distanceSqr = entity.distanceToSqr(camera);
        if (distanceSqr > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        int stage = Mth.clamp(amplifier + 1, 1, MAX_STAGE);
        int interval = INTERVAL[stage] * (distanceSqr > HALF_RATE_DISTANCE * HALF_RATE_DISTANCE ? 2 : 1);
        int phase = Math.floorMod(entity.tickCount + entity.getId() * 7, interval);
        boolean main = phase == 0;
        boolean extra = stage >= 4 && phase == interval / 2 && RANDOM.nextFloat() < (stage == MAX_STAGE ? 0.5F : 0.3F);
        if (!main && !extra) {
            return;
        }
        if (!StormcallerClientConfig.arcsEnabled()) {
            sparks(entity, stage);
            return;
        }
        if (FxClient.activeShockArcs() >= budget()) {
            return;
        }
        float t = (stage - 1) / (float) (MAX_STAGE - 1);
        float alpha = Mth.lerp(t, 0.5F, 1.0F);
        float width = Mth.lerp(t, 0.6F, 1.35F);
        boolean firstPersonSelf = entity == camera && mc.options.getCameraType().isFirstPerson();
        if (firstPersonSelf) {
            FxClient.addBodyArc(entity, 0.1F, 0.45F, alpha * 0.55F, width * 0.8F, true, RANDOM);
        } else {
            FxClient.addBodyArc(entity, 0.1F, 0.9F, alpha, width, true, RANDOM);
        }
    }

    /** Fallback without arcs: a few vanilla sparks, more with each stage. */
    private static void sparks(LivingEntity entity, int stage) {
        for (int i = 0; i < stage; i++) {
            double x = entity.getX() + (RANDOM.nextDouble() - 0.5) * entity.getBbWidth() * 1.2;
            double y = entity.getY() + entity.getBbHeight() * (0.15 + RANDOM.nextDouble() * 0.75);
            double z = entity.getZ() + (RANDOM.nextDouble() - 0.5) * entity.getBbWidth() * 1.2;
            entity.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (RANDOM.nextDouble() - 0.5) * 0.1, (RANDOM.nextDouble() - 0.5) * 0.1, (RANDOM.nextDouble() - 0.5) * 0.1);
        }
    }
}
