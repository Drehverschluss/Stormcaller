package com.drehverschluss.stormcaller.spell;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Storm Ward (skill tree, passive 3 side B): absorbs damage like the vanilla absorption effect, {@link #PER_STAGE}
 * absorption per stage (the stage comes from the Lightning Spell Power, {@code amplifier_power_multiplier} of the stash).
 * The effect ends as soon as the absorption is used up. While it holds, the stash trigger of the spell shocks melee
 * attackers ({@code data/stormcaller/spell/storm_ward.json}).
 * <p>
 * Forge 1.20.1 (no {@code MAX_ABSORPTION} attribute, no {@code onEffectStarted}, {@code applyEffectTick} returns
 * nothing): works like the vanilla absorption effect of 1.20.1. The absorption is given when the effect is applied and
 * taken back when it ends. An effect whose absorption is used up is removed at the end of the server tick (removing it
 * inside its own tick would change the effect map while Minecraft iterates over it).
 */
public class StormWardEffect extends MobEffect {
    /** Absorption per stage (1 heart). */
    public static final float PER_STAGE = 2.0F;

    /** Server: entities whose Storm Ward has no absorption left, removed at the end of the tick. */
    private static final List<LivingEntity> USED_UP = new ArrayList<>();
    private static boolean listening;

    public StormWardEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, event -> {
                if (event.phase == TickEvent.Phase.END && !USED_UP.isEmpty()) {
                    List<LivingEntity> entities = new ArrayList<>(USED_UP);
                    USED_UP.clear();
                    for (LivingEntity entity : entities) {
                        if (entity.isAlive() && entity.getAbsorptionAmount() <= 0.0F && entity.hasEffect(this)) {
                            entity.removeEffect(this);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(entity, attributeMap, amplifier);
        entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), PER_STAGE * (amplifier + 1)));
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        // like the vanilla absorption effect of 1.20.1: take back what this effect gave (at most what is left)
        entity.setAbsorptionAmount(Math.max(0.0F, entity.getAbsorptionAmount() - PER_STAGE * (amplifier + 1)));
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    /** Server: remembers the entity when the absorption is gone; the effect ends at the end of the tick. */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.getAbsorptionAmount() <= 0.0F && !USED_UP.contains(entity)) {
            USED_UP.add(entity);
        }
    }
}
