package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Short slow of a shocked target. The amount comes from the server config when the effect starts, so it is not a fixed
 * modifier of the effect.
 */
public class ShockSlowEffect extends MobEffect {
    private static final ResourceLocation MODIFIER_ID = Stormcaller.id("shock_slow");

    public ShockSlowEffect() {
        super(MobEffectCategory.HARMFUL, ShockedEffect.COLOR);
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(attributeMap, amplifier);
        AttributeInstance speed = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER_ID);
            double amount = StormcallerConfig.get(StormcallerConfig.SLOW_AMOUNT);
            speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        AttributeInstance speed = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER_ID);
        }
    }
}
