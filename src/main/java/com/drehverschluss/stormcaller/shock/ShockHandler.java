package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import java.util.ArrayList;
import java.util.List;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.spell_engine.api.effect.SpellEngineEffects;
import org.jetbrains.annotations.Nullable;

/**
 * The shock of Stormcaller.
 * <p>
 * <b>Chance:</b> one roll per hit.
 * <ul>
 *     <li>Hits of spells with a shock impact ({@link ShockImpacts}, e.g. {@code stormcaller:shock/15}) roll
 *     base chance of the spell + shock chance of the caster, at most 100 %.</li>
 *     <li>Any other {@code spell_power:lightning} damage (other mods, commands) rolls only the shock chance of the
 *     attacker. This roll is deferred to the end of the server tick: if a shock impact of the same attacker on the same
 *     target runs in that tick, it takes the hit over and the deferred roll is dropped. So a spell hit never rolls
 *     twice, no matter in which order the spell lists its impacts.</li>
 *     <li>For summons the shock chance of the owner counts ({@link #shockSource}).</li>
 * </ul>
 * <b>Effect of a shock</b> ({@link #shock}): immune targets get nothing. Every other target gets one stage of
 * "Shocked" (more lightning damage taken). On top, normal targets are stunned briefly, resistant targets (bosses, and
 * players unless {@code stun_players}) are slowed briefly instead, with a cooldown.
 */
public final class ShockHandler {
    public static final ResourceKey<DamageType> LIGHTNING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("spell_power", "lightning"));
    public static final TagKey<EntityType<?>> SHOCK_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Stormcaller.id("shock_immune"));
    public static final TagKey<EntityType<?>> SHOCK_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, Stormcaller.id("shock_resistant"));

    /** Persistent data key: game time from which the target can be slowed again. */
    private static final String SLOW_READY_KEY = "stormcaller.shock_slow_ready";

    /** What a shock did to its target. */
    public enum Result { NONE, STUNNED, SLOWED, SHOCKED_ONLY }

    /** One lightning hit: target and the entity whose shock chance counts. Entities compare by identity. */
    private record Hit(LivingEntity target, LivingEntity source) {}

    /** Lightning hits of this tick that no shock impact has taken over (yet). Server thread only. */
    private static final List<Hit> PENDING = new ArrayList<>();
    /** Shock impacts of this tick that ran before the damage of their hit arrived. Server thread only. */
    private static final List<Hit> CLAIMED = new ArrayList<>();
    /** Hits of this tick that a shock impact has shocked (for {@code shock_reroll}). Server thread only. */
    private static final List<Hit> SHOCKED_HITS = new ArrayList<>();

    private ShockHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, ShockHandler::onDamage);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, ShockHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> rollPending());
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> {
            PENDING.clear();
            CLAIMED.clear();
            SHOCKED_HITS.clear();
        });
        ShockImpacts.register();
    }

    // ------------------------------------------------------------------------------------------------ rolls

    /** Server: lightning damage was dealt. Remember the hit, the roll comes at the end of the tick (see class doc). */
    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getNewDamage() <= 0.0F) {
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
        Hit hit = new Hit(target, caster);
        if (!CLAIMED.remove(hit)) {
            PENDING.add(hit);
        }
    }

    /** End of the server tick: hits without a shock impact roll the plain shock chance. */
    private static void rollPending() {
        SHOCKED_HITS.clear();
        if (PENDING.isEmpty() && CLAIMED.isEmpty()) {
            return;
        }
        List<Hit> hits = new ArrayList<>(PENDING);
        PENDING.clear();
        CLAIMED.clear();
        for (Hit hit : hits) {
            LivingEntity target = hit.target();
            if (target.isRemoved() || target.isDeadOrDying()) {
                continue;
            }
            roll(target, hit.source(), 0.0, seconds(StormcallerConfig.get(StormcallerConfig.SHOCK_STUN_SECONDS)), 0);
        }
    }

    /**
     * Server: a shock impact hit {@code target}. Takes the hit over from the damage listener and rolls once with
     * {@code baseChance} + shock chance of the caster.
     *
     * @param extraSlowTicks extra slow duration for resistant targets (modifiers like Thunderclap)
     * @return what the shock did (NONE if the roll failed)
     */
    static Result onShockImpact(LivingEntity target, LivingEntity caster, double baseChance, int stunTicks, int extraSlowTicks) {
        LivingEntity source = shockSource(caster);
        if (source == null || source == target) {
            return Result.NONE;
        }
        Hit hit = new Hit(target, source);
        if (!PENDING.remove(hit)) {
            CLAIMED.add(hit);
        }
        if (target.isRemoved() || target.isDeadOrDying()) {
            return Result.NONE;
        }
        Result result = roll(target, source, baseChance, stunTicks, extraSlowTicks);
        if (result != Result.NONE) {
            SHOCKED_HITS.add(hit);
        }
        return result;
    }

    /**
     * Server: second roll of a hit ({@code stormcaller:shock_reroll}). Rolls like {@link #onShockImpact}, but only if no
     * shock impact has shocked this target for this attacker in this tick yet, so a hit shocks at most once.
     */
    static Result onShockReroll(LivingEntity target, LivingEntity caster, double baseChance, int stunTicks, int extraSlowTicks) {
        LivingEntity source = shockSource(caster);
        if (source == null || source == target || SHOCKED_HITS.contains(new Hit(target, source))) {
            return Result.NONE;
        }
        return onShockImpact(target, caster, baseChance, stunTicks, extraSlowTicks);
    }

    /**
     * One roll: base chance + shock chance of {@code source}, at most 100 %. With "Charged Roll" (skill tree) on the
     * source the roll always succeeds; the effect is used up by the first shock.
     */
    private static Result roll(LivingEntity target, LivingEntity source, double baseChance, int stunTicks, int extraSlowTicks) {
        boolean charged = source.hasEffect(StormcallerEffects.CHARGED_ROLL);
        double chance = charged ? 1.0 : Math.min(1.0, Math.max(0.0, baseChance) + StormcallerAttributes.shockChance(source));
        if (chance <= 0.0 || (chance < 1.0 && target.getRandom().nextDouble() >= chance)) {
            return Result.NONE;
        }
        Result result = shock(target, source, stunTicks, extraSlowTicks);
        if (charged && result != Result.NONE) {
            source.removeEffect(StormcallerEffects.CHARGED_ROLL);
        }
        return result;
    }

    /** Bosses ({@code #stormcaller:shock_resistant}) and players unless {@code stun_players}: slowed instead of stunned. */
    public static boolean isResistant(LivingEntity target) {
        return target.getType().is(SHOCK_RESISTANT)
                || (target instanceof Player && !StormcallerConfig.get(StormcallerConfig.SHOCK_STUN_PLAYERS));
    }

    // ------------------------------------------------------------------------------------------------ damage bonus

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
        double perStage = StormcallerConfig.get(StormcallerConfig.SHOCKED_DAMAGE_PER_STAGE);
        event.setAmount((float) (event.getAmount() * (1.0 + perStage * (shocked.getAmplifier() + 1))));
    }

    // ------------------------------------------------------------------------------------------------ the shock itself

    /** The entity whose shock chance counts: the attacker itself, or the owner of a summon. */
    @Nullable
    public static LivingEntity shockSource(@Nullable Entity attacker) {
        if (attacker instanceof OwnableEntity ownable && ownable.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        return attacker instanceof LivingEntity living ? living : null;
    }

    /**
     * Applies a shock to a target (no roll).
     *
     * @param stunTicks stun duration for normal targets
     */
    public static Result shock(LivingEntity target, @Nullable Entity source, int stunTicks) {
        return shock(target, source, stunTicks, 0);
    }

    /**
     * Applies a shock to a target (no roll).
     *
     * @param stunTicks      stun duration for normal targets
     * @param extraSlowTicks added to the slow duration of resistant targets
     */
    public static Result shock(LivingEntity target, @Nullable Entity source, int stunTicks, int extraSlowTicks) {
        if (target.getType().is(SHOCK_IMMUNE)) {
            return Result.NONE;
        }
        addShockedStage(target, source);
        if (isResistant(target)) {
            return slow(target, source, extraSlowTicks) ? Result.SLOWED : Result.SHOCKED_ONLY;
        }
        if (stunTicks <= 0) {
            return Result.SHOCKED_ONLY;
        }
        target.addEffect(new MobEffectInstance(SpellEngineEffects.STUN.entry, stunTicks), source);
        return Result.STUNNED;
    }

    /** One more stage of "Shocked" (up to the highest stage), the duration starts again. */
    private static void addShockedStage(LivingEntity target, @Nullable Entity source) {
        int maxStages = StormcallerConfig.get(StormcallerConfig.SHOCKED_MAX_STAGES);
        int duration = seconds(StormcallerConfig.get(StormcallerConfig.SHOCKED_SECONDS));
        if (duration <= 0) {
            return;
        }
        MobEffectInstance current = target.getEffect(StormcallerEffects.SHOCKED);
        int amplifier = current == null ? 0 : Math.min(current.getAmplifier() + 1, maxStages - 1);
        if (current != null && current.getAmplifier() >= amplifier) {
            // already at the cap: only refresh the duration
            target.removeEffect(StormcallerEffects.SHOCKED);
        }
        // no vanilla swirl particles: the arcs on the body come from the client (fx.client.ShockAura)
        target.addEffect(new MobEffectInstance(StormcallerEffects.SHOCKED, duration, amplifier, false, false, true), source);
    }

    /** Short slow for resistant targets, at most once per cooldown. */
    private static boolean slow(LivingEntity target, @Nullable Entity source, int extraTicks) {
        if (!StormcallerConfig.get(StormcallerConfig.SLOW_ENABLED)) {
            return false;
        }
        long now = target.level().getGameTime();
        long ready = target.getPersistentData().getLong(SLOW_READY_KEY);
        if (now < ready) {
            return false;
        }
        int slowTicks = seconds(StormcallerConfig.get(StormcallerConfig.SLOW_SECONDS)) + Math.max(0, extraTicks);
        int cooldownTicks = seconds(StormcallerConfig.get(StormcallerConfig.SLOW_COOLDOWN_SECONDS));
        if (slowTicks <= 0) {
            return false;
        }
        target.addEffect(new MobEffectInstance(StormcallerEffects.SHOCK_SLOW, slowTicks, 0, false, false, false), source);
        target.getPersistentData().putLong(SLOW_READY_KEY, now + slowTicks + cooldownTicks);
        return true;
    }

    static int seconds(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
