package com.drehverschluss.stormcaller.entity;

import com.drehverschluss.stormcaller.fx.BallFxPayload;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import com.drehverschluss.stormcaller.trinket.BallLightning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

/**
 * Ball lightning (phase 6B.4, staff since phase 10): a slow ball of lightning that drifts in a straight line, passes
 * through enemies and stops at the first solid block.
 * <ul>
 *     <li>Created by the passive {@code stormcaller:ball_lightning} of the Caged Ball Lightning relic (one ball per
 *     player) or by the active spell {@code stormcaller:ball_lightning_staff} of the Stormbringer Staff (several per
 *     player), both through {@link BallLightning}. The {@link Source} only decides which helper spells carry the hits:
 *     passive ones for the relic (they trigger nothing), active ones for the staff (they count as hits of an active
 *     lightning spell of the player). Everything else is the same.</li>
 *     <li>Moves on both sides with the same synced direction and speed, so the client needs few position updates; the
 *     server decides when it gets stuck at a wall.</li>
 *     <li>Server, every pulse: lightning to the nearest valid enemies in range (impacts of {@code ball_lightning_zap}).
 *     Every tick: enemies whose hitbox the ball touches get the discharge once per ball (impacts of
 *     {@code ball_lightning_discharge}). Damage, crits and shock come from Spell Engine; targets follow Spell Engine's
 *     rule for harmful area effects ({@link #valid}), nearest first, never through walls.</li>
 *     <li>Ends after its lifetime with a short fade and a pop, or right away when the owner dies, logs out or changes
 *     the dimension. Never saved with the world.</li>
 * </ul>
 * The client only draws it ({@code fx.client.BallLightningFx}); the entity renderer is a no-op.
 */
public class BallLightningEntity extends Entity {
    /** Where a ball comes from. */
    public enum Source {
        /** Passive of the Caged Ball Lightning relic: hits through passive helper spells, trigger nothing. */
        RELIC,
        /** Active spell of the Stormbringer Staff: hits through active helper spells, trigger like an active lightning spell. */
        STAFF
    }

    /** Radius of the ball (visual size 0.8 blocks); also the distance it keeps from a wall. */
    public static final double RADIUS = 0.4;
    /** Short fade at the end, no lightning then. */
    public static final int FADE_TICKS = 6;

    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.FLOAT);
    /** Blocks per tick. */
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.BOOLEAN);
    /** Tick at which the fade began, -1 while active. */
    private static final EntityDataAccessor<Integer> FADE_START = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);

    // server only
    @Nullable
    private UUID ownerId;
    private int lifeTicks = 200;
    private int pulseTicks = 10;
    private double zapRange = 4.0;
    private int maxTargets = 3;
    private Source source = Source.RELIC;
    private final Set<Integer> discharged = new HashSet<>();
    /** Client: stuck as seen by the local movement, until the server says so too. */
    private boolean localStuck;

    public BallLightningEntity(EntityType<? extends BallLightningEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Server, right after creation. */
    public void setup(Player owner, Source source, Vec3 direction, double blocksPerTick, int lifeTicks, int pulseTicks,
                      double zapRange, int maxTargets, boolean stuck) {
        this.ownerId = owner.getUUID();
        this.source = source;
        Vec3 dir = direction.normalize();
        this.entityData.set(DIR_X, (float) dir.x);
        this.entityData.set(DIR_Y, (float) dir.y);
        this.entityData.set(DIR_Z, (float) dir.z);
        this.entityData.set(SPEED, (float) blocksPerTick);
        this.entityData.set(STUCK, stuck);
        this.lifeTicks = Math.max(1, lifeTicks);
        this.pulseTicks = Math.max(1, pulseTicks);
        this.zapRange = Math.max(0.0, zapRange);
        this.maxTargets = Math.max(0, maxTargets);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
        builder.define(SPEED, 0.0F);
        builder.define(STUCK, false);
        builder.define(FADE_START, -1);
    }

    // ------------------------------------------------------------------------------------------------ state

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    /** Server: where the ball comes from (relic or staff). */
    public Source source() {
        return source;
    }

    public Vec3 direction() {
        return new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
    }

    /** Blocks per tick. */
    public double speed() {
        return entityData.get(SPEED);
    }

    public boolean isStuck() {
        return entityData.get(STUCK) || localStuck;
    }

    /** Ticks since the fade began, -1 while active. */
    public int fadeAge() {
        int start = entityData.get(FADE_START);
        return start < 0 ? -1 : tickCount - start;
    }

    /** Center of the ball. */
    public Vec3 center() {
        return position().add(0.0, getBbHeight() * 0.5, 0.0);
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        Vec3 before = center();
        move();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        ServerPlayer owner = owner(server);
        if (owner == null) {
            end(server, false);
            return;
        }
        int fade = fadeAge();
        if (fade >= 0) {
            if (fade >= FADE_TICKS) {
                end(server, true);
            }
            return;
        }
        if (tickCount > lifeTicks) {
            entityData.set(FADE_START, tickCount);
            return;
        }
        discharge(server, owner, before, center());
        if (tickCount % pulseTicks == 0) {
            zap(server, owner);
        }
    }

    /** Straight ahead until the ball touches a solid block; both sides do the same. */
    private void move() {
        if (isStuck()) {
            return;
        }
        double speed = speed();
        if (speed <= 0.0) {
            return;
        }
        Vec3 dir = direction();
        Vec3 from = center();
        Vec3 step = dir.scale(speed);
        Vec3 probe = from.add(step).add(dir.scale(RADIUS));
        BlockHitResult hit = level().clip(new ClipContext(from, probe, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            return;
        }
        Vec3 stop = hit.getLocation().subtract(dir.scale(RADIUS));
        // never move backwards (spawned close to a wall)
        if (stop.subtract(from).dot(dir) > 0.0) {
            setPos(stop.x, stop.y - getBbHeight() * 0.5, stop.z);
        }
        localStuck = true;
        if (!level().isClientSide()) {
            entityData.set(STUCK, true);
        }
    }

    @Nullable
    private ServerPlayer owner(ServerLevel server) {
        if (ownerId == null) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null || !player.isAlive() || player.isRemoved() || player.level() != server || player.isSpectator()) {
            return null;
        }
        return player;
    }

    /** Server: lightning to the nearest valid enemies around the ball. */
    private void zap(ServerLevel server, ServerPlayer owner) {
        if (maxTargets <= 0 || zapRange <= 0.0) {
            return;
        }
        Holder<Spell> spell = BallLightning.zapSpell(server, source);
        if (spell == null) {
            return;
        }
        Vec3 center = center();
        double rangeSq = zapRange * zapRange;
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity entity : server.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(zapRange))) {
            if (distanceSqToBox(center, entity.getBoundingBox()) <= rangeSq && valid(entity, owner)) {
                candidates.add(entity);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(entity -> distanceSqToBox(center, entity.getBoundingBox())));
        int hits = 0;
        for (LivingEntity target : candidates) {
            if (hits >= maxTargets) {
                break;
            }
            if (!canSee(server, center, target)) {
                continue;
            }
            hits++;
            StormcallerFx.ball(server, center, new BallFxPayload(BallFxPayload.Kind.ZAP, getId(), target.getId(), center,
                    server.getRandom().nextInt()));
            impacts(server, owner, target, spell, center);
        }
    }

    /** Server: discharge into every valid enemy the ball touched since the last tick (once per enemy). */
    private void discharge(ServerLevel server, ServerPlayer owner, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, from).inflate(RADIUS).minmax(new AABB(to, to).inflate(RADIUS));
        List<LivingEntity> touched = server.getEntitiesOfClass(LivingEntity.class, box,
                entity -> !discharged.contains(entity.getId()) && valid(entity, owner));
        if (touched.isEmpty()) {
            return;
        }
        Holder<Spell> spell = BallLightning.dischargeSpell(server, source);
        for (LivingEntity target : touched) {
            discharged.add(target.getId());
            StormcallerFx.ball(server, to, new BallFxPayload(BallFxPayload.Kind.DISCHARGE, getId(), target.getId(), to,
                    server.getRandom().nextInt()));
            if (spell != null) {
                impacts(server, owner, target, spell, to);
            }
        }
    }

    private void impacts(ServerLevel server, ServerPlayer owner, LivingEntity target, Holder<Spell> spell, Vec3 center) {
        SpellExecution.ImpactContext context = new SpellExecution.ImpactContext(1.0F, 1.0F, center,
                SpellPower.getSpellPower(spell.value().school, owner), SpellTarget.FocusMode.AREA, 0);
        // no area lookup: the ball picks its targets itself
        SpellImpacts.performImpacts(server, owner, target, this, spell, spell.value().impacts, context, false, null);
    }

    /**
     * Alive, attackable, not the owner, and Spell Engine allows harmful area effects on it: the same rule as Lightning Nova
     * and Thunderstorm ({@code EntityRelations.actionAllowed(AREA, HARMFUL)}), so Spell Engine's config decides
     * ({@code player_relation_to_*}, {@code player_relations}). With the defaults: allies, own pets, team mates and
     * FRIENDLY entries (villagers, pack entries for citizens) are skipped, NEUTRAL animals are hit. Used for the lightning
     * to nearby entities and for the discharge (not aimed either).
     */
    public static boolean valid(Entity entity, LivingEntity owner) {
        return entity != owner && entity instanceof LivingEntity living && living.isAlive() && entity.isAttackable()
                && !entity.isSpectator() && !entity.isInvulnerable()
                && EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL, owner, entity);
    }

    /** No lightning through walls: free line to the center or the eyes of the target. */
    private static boolean canSee(ServerLevel server, Vec3 from, LivingEntity target) {
        return clear(server, from, target.getBoundingBox().getCenter()) || clear(server, from, target.getEyePosition());
    }

    private static boolean clear(ServerLevel server, Vec3 from, Vec3 to) {
        return server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    private static double distanceSqToBox(Vec3 p, AABB box) {
        double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Server: ends the ball right away (its owner died), with the small puff of a lost owner. */
    public void ownerDied() {
        if (level() instanceof ServerLevel server && !isRemoved()) {
            end(server, false);
        }
    }

    /** Server: ends the ball right away with its normal pop (a newer staff ball replaced it at the limit). */
    public void replaced() {
        if (level() instanceof ServerLevel server && !isRemoved()) {
            end(server, true);
        }
    }

    /** Server: pop (visual and sound) and remove. */
    private void end(ServerLevel server, boolean pop) {
        Vec3 center = center();
        StormcallerFx.ball(server, center, new BallFxPayload(BallFxPayload.Kind.POP, getId(), -1, center, server.getRandom().nextInt()));
        server.playSound(null, center.x, center.y, center.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, pop ? 0.35F : 0.25F, 1.8F);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        BallLightning.forget(this);
    }

    // ------------------------------------------------------------------------------------------------ entity basics

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // never saved (shouldBeSaved), a loaded one has no owner and ends on its first tick
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
