package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import java.util.UUID;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Short slow of a shocked target. The amount comes from the server config when the effect starts, so it is not a fixed
 * modifier of the effect.
 */
public class ShockSlowEffect extends MobEffect {
    /** Forge 1.20.1: modifiers are identified by UUID (1.21.1: id {@code stormcaller:shock_slow}). */
    private static final UUID MODIFIER_ID = Stormcaller.uuid("shock_slow");

    public ShockSlowEffect() {
        super(MobEffectCategory.HARMFUL, ShockedEffect.COLOR);
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(entity, attributeMap, amplifier);
        AttributeInstance speed = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER_ID);
            double amount = StormcallerConfig.get(StormcallerConfig.SLOW_AMOUNT);
            speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, "Shock slow", -amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        AttributeInstance speed = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER_ID);
        }
    }
}
