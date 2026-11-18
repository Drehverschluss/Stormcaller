package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.fx.client.StoredChargeHud;
import com.drehverschluss.stormcaller.spell.StormWardEffect;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.spell_engine.api.effect.CustomStatusEffect;

/** Status effects of Stormcaller. */
public final class StormcallerEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Stormcaller.MODID);

    /** "Shocked": stages of extra lightning damage taken, every shock adds one (all targets). */
    public static final RegistryObject<MobEffect> SHOCKED = EFFECTS.register("shocked", ShockedEffect::new);

    /** Short slow of a shocked target, hidden (no icon, no particles). */
    public static final RegistryObject<MobEffect> SHOCK_SLOW = EFFECTS.register("shock_slow", ShockSlowEffect::new);

    /** Skill tree, Charged Roll: the next shock roll of the bearer succeeds, then the effect is gone ({@link ShockHandler}). */
    public static final RegistryObject<MobEffect> CHARGED_ROLL = EFFECTS.register("charged_roll",
            () -> new CustomStatusEffect(MobEffectCategory.BENEFICIAL, ShockedEffect.COLOR));

    /**
     * Skill tree, Eye of the Storm: the next spell in {@code #stormcaller:eye_of_the_storm} is cast instantly (Spell Engine
     * {@code InstantCast}, registered in {@code spell.SkillMechanics}). A stash effect, used up by that cast.
     */
    public static final RegistryObject<MobEffect> EYE_OF_THE_STORM = EFFECTS.register("eye_of_the_storm",
            () -> new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0xC8E6FF));

    /** Skill tree, Storm Ward: absorbs damage, melee attackers may be shocked (stash of the spell). */
    public static final RegistryObject<MobEffect> STORM_WARD = EFFECTS.register("storm_ward",
            () -> new StormWardEffect(0x8FB8FF));

    /**
     * Skill tree, Static Rain: slow of the Thunderstorm hits, {@link #STATIC_RAIN_PER_STAGE} per stage
     * (normal targets stage 2 = -30 %, bosses stage 1 = -15 %).
     */
    public static final double STATIC_RAIN_PER_STAGE = 0.15;
    public static final RegistryObject<MobEffect> STATIC_RAIN = EFFECTS.register("static_rain",
            () -> new CustomStatusEffect(MobEffectCategory.HARMFUL, 0x7A8CB0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "5f1e2c41-7a3b-4c11-9b0e-2f6d8a4e7c01", -STATIC_RAIN_PER_STAGE,
                            AttributeModifier.Operation.MULTIPLY_BASE));

    /**
     * Amber Lightning Staff, Stored Charge: stage = charges (1 to {@code [stored_charge] max_charges}). At full charge the
     * next Lightning Bolt cast with the staff discharges ({@link com.drehverschluss.stormcaller.spell.StoredCharge}).
     */
    public static final RegistryObject<MobEffect> STORED_CHARGE = EFFECTS.register("stored_charge",
            () -> new CustomStatusEffect(MobEffectCategory.BENEFICIAL, 0xF2A93B) {
                /**
                 * Forge 1.20.1: client extensions come from the effect itself (1.21.1: RegisterClientExtensionsEvent).
                 * Forge calls this on the client only; the HUD class is loaded only then.
                 */
                @Override
                public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
                    consumer.accept(StoredChargeHud.extensions());
                }
            });

    private StormcallerEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
