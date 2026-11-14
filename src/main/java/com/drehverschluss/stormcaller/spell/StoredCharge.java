package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import com.drehverschluss.stormcaller.shock.StormcallerEffects;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellExecution;
import org.jetbrains.annotations.Nullable;

/**
 * Stored Charge, the passive of the Amber Lightning Staff (phase 10).
 * <ul>
 *     <li><b>Charging:</b> the passive spell {@code stormcaller:stored_charge} (in the staff's container next to Lightning
 *     Bolt, like Skyfall on the Stormbringer) triggers on every hit of an active lightning spell with damage, only with the
 *     staff in the main hand, at most once per tick ({@code cap_per_tick 1}). Passives (Skyfall, relic ball, Static Trap)
 *     are filtered out by {@code type ACTIVE}. Its custom delivery {@code stormcaller:stored_charge} adds one stage to
 *     the effect {@code stormcaller:stored_charge} (stage = charges, up to {@code [stored_charge] max_charges}) and sets
 *     its duration to {@code decay_seconds}: when no hit charges the staff for that long, the effect and with it the whole
 *     charge runs out. A hit at full charge only refreshes the duration.</li>
 *     <li><b>Discharge:</b> when Lightning Bolt is launched ({@code SpellEvents.PROJECTILE_SHOOT}) by a player with the
 *     staff in the main hand and a full charge, the projectile gets {@code extra_ricochets} more ricochets (on top of
 *     the spell and all modifiers, e.g. of the skill tab) and is marked. Every hit of a marked projectile shocks with the
 *     base chance {@code discharge_shock_chance} (1.0 = always; see {@code mixin.SpellProjectileMixin} and
 *     {@code shock.ShockImpacts}). The charge is used up at once, so the hits of that bolt charge the staff anew. Other
 *     spells never discharge.</li>
 *     <li><b>The discharge does not charge</b> (phase 10 addendum, {@code [stored_charge] discharge_hits_charge = false}):
 *     Spell Engine fires the POST triggers of an impact later in the tick ({@code WorldScheduler.schedule(0, ...)} in
 *     {@code SpellImpacts.performImpact}), outside the impacts of the projectile. A mixin wraps the scheduled task when it
 *     is created during the impacts of a discharging bolt ({@code mixin.SpellImpactsMixin}); while that task runs, the
 *     entity predicate {@code stormcaller:not_discharge_hit} (a {@code caster_conditions} entry of the trigger in
 *     {@code stored_charge.json}) is false. Spell Engine checks caster conditions before it counts {@code cap_per_tick},
 *     so another hit in the same tick still charges.</li>
 * </ul>
 * Why this way: the stages are a normal status effect (HUD, inventory, decay for free, synced by Minecraft). Spell
 * Engine's modifier spells cannot depend on an effect (they are cached per player and spell), so the extra ricochets go
 * straight into the mutable perks of the launched projectile, which is where Spell Engine adds the modifier perks too.
 */
public final class StoredCharge {
    public static final ResourceLocation DELIVERY = Stormcaller.id("stored_charge");
    /** Entity predicate for {@code caster_conditions}: false while a trigger of a discharge hit runs. */
    public static final ResourceLocation NOT_DISCHARGE_HIT = Stormcaller.id("not_discharge_hit");
    private static final ResourceLocation LIGHTNING_BOLT = Stormcaller.id("lightning_bolt");
    /** Persistent tag on a discharging Lightning Bolt projectile. */
    private static final String DISCHARGE_TAG = Stormcaller.MODID + ":stored_discharge";

    /** Set while the impacts of a discharging projectile run (server thread). */
    private static int dischargeDepth;
    /** Set while the scheduled triggers of a discharge hit run (server thread). */
    private static int dischargeTriggerDepth;

    private StoredCharge() {}

    public static void init() {
        SpellHandlers.registerCustomDelivery(DELIVERY, StoredCharge::charge);
        SpellEvents.PROJECTILE_SHOOT.register(StoredCharge::onProjectileShoot);
        SpellEntityPredicates.register(NOT_DISCHARGE_HIT, args -> dischargeTriggerDepth == 0 || dischargeHitsCharge());
    }

    // ------------------------------------------------------------------------------------------------ values

    public static int maxCharges() {
        return StormcallerConfig.get(StormcallerConfig.STORED_CHARGE_MAX);
    }

    public static int extraRicochets() {
        return StormcallerConfig.get(StormcallerConfig.STORED_CHARGE_EXTRA_RICOCHETS);
    }

    public static double decaySeconds() {
        return StormcallerConfig.get(StormcallerConfig.STORED_CHARGE_DECAY_SECONDS);
    }

    public static double dischargeShockChance() {
        return StormcallerConfig.get(StormcallerConfig.STORED_CHARGE_SHOCK_CHANCE);
    }

    /** {@code [stored_charge] discharge_hits_charge}: do the hits of the discharging bolt charge the staff? */
    public static boolean dischargeHitsCharge() {
        return StormcallerConfig.get(StormcallerConfig.STORED_CHARGE_DISCHARGE_HITS_CHARGE);
    }

    /** Charges of a player (0 without the effect). */
    public static int charges(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(StormcallerEffects.STORED_CHARGE);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    // ------------------------------------------------------------------------------------------------ charging

    /** Custom delivery of {@code stored_charge}: one more charge, duration refreshed. */
    private static boolean charge(Level world, Holder<Spell> spell, LivingEntity caster, List<SpellExecution.DeliveryTarget> targets,
                                  SpellExecution.ImpactContext context, @Nullable Vec3 targetLocation) {
        if (!(world instanceof ServerLevel level) || !(caster instanceof ServerPlayer player) || !holdsStaff(player)) {
            return false;
        }
        int max = maxCharges();
        int before = Math.min(charges(player), max);
        int after = Math.min(before + 1, max);
        int ticks = Math.max(1, (int) Math.round(decaySeconds() * 20.0));
        player.addEffect(new MobEffectInstance(StormcallerEffects.STORED_CHARGE, ticks, after - 1, false, false, true));
        if (after > before) {
            Vec3 hand = handPosition(player);
            boolean full = after >= max;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, full ? 14 : 3, 0.12, 0.12, 0.12, full ? 0.25 : 0.08);
            if (full) {
                level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.4F);
                level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.35F, 1.9F);
            }
        }
        return true;
    }

    private static boolean holdsStaff(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        return StormcallerItems.staffAmberLightning != null && StormcallerItems.staffAmberLightning.item() != null
                && stack.is(StormcallerItems.staffAmberLightning.item());
    }

    /** Roughly where the staff tip is: right of the view, a bit forward. */
    private static Vec3 handPosition(LivingEntity entity) {
        Vec3 look = entity.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        if (right.lengthSqr() < 1.0E-4) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        return entity.getEyePosition().add(look.scale(0.6)).add(right.normalize().scale(0.35)).add(0.0, -0.25, 0.0);
    }

    // ------------------------------------------------------------------------------------------------ discharge

    private static void onProjectileShoot(SpellEvents.ProjectileLaunchEvent event) {
        LivingEntity caster = event.caster();
        SpellProjectile projectile = event.projectile();
        if (caster == null || projectile == null || caster.level().isClientSide()
                || !event.spellEntry().unwrapKey().map(key -> key.location().equals(LIGHTNING_BOLT)).orElse(false)
                || !holdsStaff(caster) || charges(caster) < maxCharges()) {
            return;
        }
        Spell.ProjectileData.Perks perks = projectile.mutablePerks();
        if (perks != null) {
            perks.ricochet += extraRicochets();
        }
        projectile.getPersistentData().putBoolean(DISCHARGE_TAG, true);
        caster.removeEffect(StormcallerEffects.STORED_CHARGE);
        if (caster.level() instanceof ServerLevel level) {
            Vec3 hand = handPosition(caster);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 20, 0.15, 0.15, 0.15, 0.35);
            level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.7F);
        }
    }

    /** Is this a discharging Lightning Bolt? */
    public static boolean isDischarge(@Nullable Entity projectile) {
        return projectile != null && projectile.getPersistentData().getBoolean(DISCHARGE_TAG);
    }

    /** Mixin: the impacts of a projectile hit begin ({@code discharge} = marked projectile). */
    public static void beginImpacts(boolean discharge) {
        if (discharge) {
            dischargeDepth++;
        }
    }

    /** Mixin: the impacts of a projectile hit ended. */
    public static void endImpacts(boolean discharge) {
        if (discharge && dischargeDepth > 0) {
            dischargeDepth--;
        }
    }

    /**
     * Mixin: a task Spell Engine schedules during an impact (the POST triggers). Created during the impacts of a
     * discharging bolt, it runs with {@link #NOT_DISCHARGE_HIT} false; other tasks stay as they are.
     */
    public static Runnable wrapScheduled(Runnable task) {
        if (dischargeDepth <= 0) {
            return task;
        }
        return () -> {
            dischargeTriggerDepth++;
            try {
                task.run();
            } finally {
                dischargeTriggerDepth--;
            }
        };
    }

    /**
     * Base shock chance for the shock impact running right now: {@code discharge_shock_chance} inside the impacts of a
     * discharging Lightning Bolt, otherwise {@code base} (the spell's own chance).
     */
    public static double shockBase(double base) {
        return dischargeDepth > 0 ? Math.max(base, dischargeShockChance()) : base;
    }
}
