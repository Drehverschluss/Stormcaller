package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.spell.StaticShield;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.CustomParticleStatusEffect;

/**
 * Static Shield on the client: small, short arcs that crawl over the body of every entity with the effect.
 * <p>
 * Spell Engine synchronizes the effect to all tracking clients and calls the spawner every client tick for each entity
 * that has it. The arcs move with the entity, so they also look right while it runs. Costs nothing on the server and
 * no packets.
 * <p>
 * Own player in first person: only low arcs (hips and legs) with less strength, so they stay out of the view.
 */
public final class ShieldAura {
    /** One arc every this many ticks, sometimes a second one in between. */
    private static final int INTERVAL = 6;
    private static final RandomSource RANDOM = RandomSource.create();

    private ShieldAura() {}

    public static void register() {
        CustomParticleStatusEffect.register(StaticShield.effect(), ShieldAura::spawn);
    }

    private static void spawn(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() || entity.isInvisible()) {
            return;
        }
        int phase = (entity.tickCount + entity.getId()) % INTERVAL;
        boolean main = phase == 0;
        boolean extra = phase == INTERVAL / 2 && RANDOM.nextFloat() < 0.4F;
        if (!main && !extra) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean firstPersonSelf = entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (firstPersonSelf) {
            FxClient.addBodyArc(entity, 0.1F, 0.45F, 0.55F, RANDOM);
        } else {
            FxClient.addBodyArc(entity, 0.15F, 0.85F, 1.0F, RANDOM);
        }
    }
}
