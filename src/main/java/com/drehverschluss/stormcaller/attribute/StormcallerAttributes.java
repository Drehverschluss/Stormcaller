package com.drehverschluss.stormcaller.attribute;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Attributes of Stormcaller.
 * <p>
 * {@code stormcaller:shock_chance} follows the layout of Spell Power's critical chance: base 100, a value of 108 means
 * 8 % chance. Equipment uses {@code ADD_MULTIPLIED_BASE} ("multiply"), so 0.02 shows as "+2% Shock Chance".
 */
public final class StormcallerAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Stormcaller.MODID);

    public static final DeferredHolder<Attribute, Attribute> SHOCK_CHANCE = ATTRIBUTES.register("shock_chance",
            () -> new RangedAttribute("attribute.name.stormcaller.shock_chance", 100.0, 100.0, 1000.0).setSyncable(true));

    private StormcallerAttributes() {}

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(EntityAttributeModificationEvent.class, StormcallerAttributes::onAttributeModification);
    }

    private static void onAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, SHOCK_CHANCE)) {
            event.add(EntityType.PLAYER, SHOCK_CHANCE);
        }
    }

    /**
     * Shock chance of an entity as a fraction (0.08 = 8 %). Entities without the attribute have 0.
     */
    public static double shockChance(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(SHOCK_CHANCE);
        if (instance == null) {
            return 0.0;
        }
        return Math.max(0.0, instance.getValue() / 100.0 - 1.0);
    }
}
