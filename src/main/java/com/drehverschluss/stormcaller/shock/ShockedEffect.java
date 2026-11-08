package com.drehverschluss.stormcaller.shock;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.spell_engine.api.effect.Synchronized;

/**
 * "Shocked" / "Geschockt". The amplifier is the stage minus one. The extra lightning damage is applied in
 * {@link ShockHandler}. The effect is synchronized to all tracking clients (Spell Engine), which draw small arcs on the
 * body of the target, more and stronger with each stage ({@code fx.client.ShockAura}).
 */
public class ShockedEffect extends MobEffect {
    /** Electric yellow (Spell Engine Color.ELECTRIC). */
    public static final int COLOR = 0xFFFF66;

    public ShockedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        Synchronized.configure(this, true);
    }
}
