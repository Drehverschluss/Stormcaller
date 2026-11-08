package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.api.spell.event.SpellHandlers;
import org.jetbrains.annotations.Nullable;

/**
 * The shock of Stormcaller.
 * <ul>
 *     <li>Every hit with the damage type {@code spell_power:lightning} rolls the shock chance of the caster (or of the
 *     owner of a summon). This works for lightning spells of other mods too.</li>
 *     <li>Lightning Nova always shocks, through the custom impact {@code stormcaller:nova_shock}.</li>
 * </ul>
 * One rule for both: immune targets get nothing, resistant targets (bosses) get "Shocked", normal targets are stunned.
 */
public final class ShockHandler {
    public static final ResourceKey<DamageType> LIGHTNING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("spell_power", "lightning"));
    public static final TagKey<EntityType<?>> SHOCK_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Stormcaller.id("shock_immune"));
    public static final TagKey<EntityType<?>> SHOCK_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, Stormcaller.id("shock_resistant"));
    public static final ResourceLocation NOVA_IMPACT = Stormcaller.id("nova_shock");

    /** Persistent data key: game time from which the target can be slowed again. */
    private static final String SLOW_READY_KEY = "stormcaller.shock_slow_ready";

    /** What a shock did to its target. */
    public enum Result { NONE, STUNNED, SHOCKED }

    private ShockHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, ShockHandler::onDamage);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, ShockHandler::onIncomingDamage);
        SpellHandlers.registerCustomImpact(NOVA_IMPACT, (spell, power, caster, target, context) -> {
            if (!(target instanceof LivingEntity living) || living.level().isClientSide()) {
                return new SpellHandlers.ImpactResult(false, false);
            }
            int ticks = seconds(StormcallerConfig.get(StormcallerConfig.NOVA_STUN_SECONDS));
            return new SpellHandlers.ImpactResult(shock(living, caster, ticks) != Result.NONE, false);
        });
    }

    /** Server: after lightning damage was dealt, roll the shock chance. */
    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getNewDamage() <= 0.0F || target.isDeadOrDying()) {
            return;
        }
        DamageSource source = event.getSource();
        if (!source.is(LIGHTNING_DAMAGE)) {
            return;
        }
        LivingEntity caster = shockSource(source.getEntity());
        if (caster == null || caster == target) {
            return;
        }
        double chance = StormcallerAttributes.shockChance(caster);
        if (chance <= 0.0 || target.getRandom().nextDouble() >= chance) {
            return;
        }
        shock(target, caster, seconds(StormcallerConfig.get(StormcallerConfig.SHOCK_STUN_SECONDS)));
    }

    /**
     * Extra lightning damage on "Shocked" targets. Runs in {@link LivingIncomingDamageEvent}, before armor, enchantments
     * and absorption, and only for {@code spell_power:lightning}. Other damage types are not touched.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(LIGHTNING_DAMAGE)) {
            return;
        }
        MobEffectInstance shocked = event.getEntity().getEffect(StormcallerEffects.SHOCKED);
        if (shocked == null) {
            return;
        }
        double perStage = StormcallerConfig.get(StormcallerConfig.RESISTANT_DAMAGE_PER_STAGE);
        event.setAmount((float) (event.getAmount() * (1.0 + perStage * (shocked.getAmplifier() + 1))));
    }

    /** The entity whose shock chance counts: the attacker itself, or the player owning a summon. */
    @Nullable
    public static LivingEntity shockSource(@Nullable Entity attacker) {
        if (attacker instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player owner) {
            return owner;
        }
        return attacker instanceof LivingEntity living ? living : null;
    }

    /**
     * Applies a shock to a target.
     *
     * @param stunTicks stun duration for normal targets
     */
    public static Result shock(LivingEntity target, @Nullable Entity source, int stunTicks) {
        if (target.getType().is(SHOCK_IMMUNE)) {
            return Result.NONE;
        }
        boolean resistant = target.getType().is(SHOCK_RESISTANT)
                || (target instanceof Player && !StormcallerConfig.get(StormcallerConfig.SHOCK_STUN_PLAYERS));
        if (resistant) {
            addShockedStage(target, source);
            return Result.SHOCKED;
        }
        if (stunTicks <= 0) {
            return Result.NONE;
        }
        target.addEffect(new MobEffectInstance(SpellEngineEffects.STUN.entry, stunTicks), source);
        return Result.STUNNED;
    }

    private static void addShockedStage(LivingEntity target, @Nullable Entity source) {
        int maxStages = StormcallerConfig.get(StormcallerConfig.RESISTANT_MAX_STAGES);
        int duration = seconds(StormcallerConfig.get(StormcallerConfig.RESISTANT_STAGE_SECONDS));
        MobEffectInstance current = target.getEffect(StormcallerEffects.SHOCKED);
        int amplifier = current == null ? 0 : Math.min(current.getAmplifier() + 1, maxStages - 1);
        if (current != null && current.getAmplifier() >= amplifier) {
            // already at the cap: only refresh the duration
            target.removeEffect(StormcallerEffects.SHOCKED);
        }
        target.addEffect(new MobEffectInstance(StormcallerEffects.SHOCKED, duration, amplifier), source);

        if (StormcallerConfig.get(StormcallerConfig.SLOW_ENABLED)) {
            long now = target.level().getGameTime();
            long ready = target.getPersistentData().getLong(SLOW_READY_KEY);
            if (now >= ready) {
                int slowTicks = seconds(StormcallerConfig.get(StormcallerConfig.SLOW_SECONDS));
                int cooldownTicks = seconds(StormcallerConfig.get(StormcallerConfig.SLOW_COOLDOWN_SECONDS));
                if (slowTicks > 0) {
                    target.addEffect(new MobEffectInstance(StormcallerEffects.SHOCK_SLOW, slowTicks, 0, false, false, false), source);
                }
                target.getPersistentData().putLong(SLOW_READY_KEY, now + slowTicks + cooldownTicks);
            }
        }
    }

    private static int seconds(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
