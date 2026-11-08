package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Status effects of Stormcaller. */
public final class StormcallerEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Stormcaller.MODID);

    /** "Shocked": stages of extra lightning damage taken, for targets that are not stunned (bosses). */
    public static final DeferredHolder<MobEffect, MobEffect> SHOCKED = EFFECTS.register("shocked", ShockedEffect::new);

    /** Short slow of a shocked target, hidden (no icon, no particles). */
    public static final DeferredHolder<MobEffect, MobEffect> SHOCK_SLOW = EFFECTS.register("shock_slow", ShockSlowEffect::new);

    private StormcallerEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
