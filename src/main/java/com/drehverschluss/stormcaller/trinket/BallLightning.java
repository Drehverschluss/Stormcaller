package com.drehverschluss.stormcaller.trinket;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import com.drehverschluss.stormcaller.entity.BallLightningEntity;
import com.drehverschluss.stormcaller.entity.StormcallerEntities;
import com.drehverschluss.stormcaller.fx.BallFxPayload;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import org.jetbrains.annotations.Nullable;

/**
 * Ball Lightning: the passive of the Caged Ball Lightning relic (phase 6B.4) and the active spell of the Stormbringer
 * Staff (phase 10). Both make the same ball ({@link BallLightningEntity}); see "Staff" below for what differs.
 * <ul>
 *     <li>{@code data/stormcaller/spell/ball_lightning.json}: trigger (chance per hit of an active lightning spell) and
 *     cooldown; its delivery {@code CUSTOM} with handler {@code stormcaller:ball_lightning} creates the ball.</li>
 *     <li>{@code ball_lightning_zap.json} and {@code ball_lightning_discharge.json}: passive spells without triggers that
 *     only carry the impacts (damage coefficient, shock chance, sounds) of the lightning to nearby enemies and of the
 *     discharge into an enemy the ball passes through. They are never in a container, and being passive they do not
 *     trigger relics or passives that filter on {@code type ACTIVE} (Static Lodestone, Skyfall, the ball itself).</li>
 *     <li>Movement, lifetime, pulse, range and target count: {@code config/stormcaller/trinkets.json}, section
 *     {@code ball_lightning} ({@link Config}), the same for relic and staff.</li>
 * </ul>
 * Relic: never more than one ball per player. Spell Engine puts the passive on cooldown in the same call that creates
 * the ball (cost without batching), so a second trigger in the same tick already sees the cooldown. On top of that the
 * trigger has {@code cap_per_tick 1} and this class refuses a new relic ball while the player still has one.
 * <p>
 * Staff ({@code ball_lightning_staff.json}, active, target {@code CASTER} so the cast always costs mana or the rune and
 * sets the cooldown, also without an enemy in sight): delivery {@code stormcaller:ball_lightning_staff}, the ball drifts
 * towards the point the player looks at. No limit of one: every staff ball lives its full time and a new one replaces
 * none, up to {@code [ball_lightning] max_staff_balls} of the server config; at that limit the oldest staff ball ends
 * (with its pop). Its hits run through {@code ball_lightning_staff_zap} and {@code ball_lightning_staff_discharge},
 * which are <b>active</b> lightning spells without a cost item: they trigger what reacts to hits of an active lightning
 * spell (Skyfall, Static Lodestone, the relic ball, set bonuses, skill passives). The relic ball still triggers nothing,
 * so there is no loop: a staff ball can trigger at most one relic ball, and that one triggers nothing. Staff and relic
 * balls are counted apart (up to 3 staff balls plus 1 relic ball).
 */
public final class BallLightning {
    public static final ResourceLocation DELIVERY = Stormcaller.id("ball_lightning");
    public static final ResourceLocation ZAP_SPELL = Stormcaller.id("ball_lightning_zap");
    public static final ResourceLocation DISCHARGE_SPELL = Stormcaller.id("ball_lightning_discharge");
    public static final ResourceLocation STAFF_DELIVERY = Stormcaller.id("ball_lightning_staff");
    public static final ResourceLocation STAFF_SPELL = Stormcaller.id("ball_lightning_staff");
    public static final ResourceLocation STAFF_ZAP_SPELL = Stormcaller.id("ball_lightning_staff_zap");
    public static final ResourceLocation STAFF_DISCHARGE_SPELL = Stormcaller.id("ball_lightning_staff_discharge");
    /** The staff ball drifts towards the block the player looks at, up to this far (else straight along the view). */
    private static final double STAFF_AIM_DISTANCE = 48.0;

    /** {@code trinkets.json} section {@code ball_lightning}. */
    public static class Config {
        /** Written into the file, explains the options (JSON has no comments). */
        public String _comment = "Ball Lightning of the Caged Ball Lightning relic and of the Stormbringer Staff. speed: blocks"
                + " per second (0 to 20). duration_seconds: lifetime (0.5 to 120). pulse_seconds: time between two lightning"
                + " pulses (0.05 to 60). range: enemies whose hitbox is this close to the ball center can be struck (0 to 16)."
                + " max_targets: enemies struck per pulse, nearest first (0 to 16)."
                + " spawn_distance: the ball appears this many blocks in front of the player (0 to 8)."
                + " Chance, cooldown, damage and shock chance are in the spell files (data pack),"
                + " the most staff balls at once in stormcaller-server.toml ([ball_lightning] max_staff_balls).";
        /** Blocks per second. */
        public float speed = 2.0F;
        /** Lifetime in seconds (then a short fade). */
        public float duration_seconds = 10.0F;
        /** Seconds between two lightning pulses to nearby enemies. */
        public float pulse_seconds = 0.5F;
        /** Enemies whose hitbox is within this many blocks of the ball center can be struck. */
        public float range = 4.0F;
        /** Enemies struck per pulse, nearest first. */
        public int max_targets = 3;
        /** The ball appears this many blocks in front of the player, at chest height. */
        public float spawn_distance = 1.5F;

        /** Keeps every value in its range (see {@link #_comment}). */
        void clamp() {
            speed = clamp(speed, 0.0F, 20.0F, 2.0F);
            duration_seconds = clamp(duration_seconds, 0.5F, 120.0F, 10.0F);
            pulse_seconds = clamp(pulse_seconds, 0.05F, 60.0F, 0.5F);
            range = clamp(range, 0.0F, 16.0F, 4.0F);
            max_targets = Math.max(0, Math.min(16, max_targets));
            spawn_distance = clamp(spawn_distance, 0.0F, 8.0F, 1.5F);
        }

        private static float clamp(float value, float min, float max, float fallback) {
            return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
        }
    }

    /** Live relic ball per player. */
    private static final Map<UUID, BallLightningEntity> ACTIVE = new HashMap<>();
    /** Live staff balls per player, oldest first. */
    private static final Map<UUID, Deque<BallLightningEntity>> STAFF = new HashMap<>();

    private BallLightning() {}

    public static void init() {
        SpellHandlers.registerCustomDelivery(DELIVERY, BallLightning::deliver);
        SpellHandlers.registerCustomDelivery(STAFF_DELIVERY, BallLightning::deliverStaff);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> {
            ACTIVE.clear();
            STAFF.clear();
        });
        // with doImmediateRespawn the ball never sees its owner dead, so end it on the death itself
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                endAll(player.getUUID());
            }
        });
        // the balls notice a missing owner on their next tick anyway; these end them in the same tick
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> endAll(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> endAll(event.getEntity().getUUID()));
    }

    public static Config config() {
        Config config = StormcallerTrinkets.CONFIG.value.ball_lightning;
        return config != null ? config : new Config();
    }

    /** Most staff balls per player at once ({@code [ball_lightning] max_staff_balls}). */
    public static int maxStaffBalls() {
        return StormcallerConfig.get(StormcallerConfig.BALL_MAX_STAFF_BALLS);
    }

    /** Server: the live relic ball of a player, or null. */
    @Nullable
    public static BallLightningEntity activeBall(UUID player) {
        BallLightningEntity ball = ACTIVE.get(player);
        return ball != null && !ball.isRemoved() ? ball : null;
    }

    /** Server: the live staff balls of a player, oldest first (a copy). */
    public static List<BallLightningEntity> staffBalls(UUID player) {
        Deque<BallLightningEntity> balls = STAFF.get(player);
        if (balls == null) {
            return List.of();
        }
        balls.removeIf(BallLightningEntity::isRemoved);
        return List.copyOf(balls);
    }

    /** Server: ends every ball of a player right away (relic and staff). */
    private static void endAll(UUID player) {
        BallLightningEntity ball = activeBall(player);
        if (ball != null) {
            ball.ownerDied();
        }
        for (BallLightningEntity staffBall : staffBalls(player)) {
            staffBall.ownerDied();
        }
    }

    /** Server, the ball was removed. */
    public static void forget(BallLightningEntity ball) {
        UUID owner = ball.ownerId();
        if (owner == null) {
            return;
        }
        ACTIVE.remove(owner, ball);
        Deque<BallLightningEntity> balls = STAFF.get(owner);
        if (balls != null) {
            balls.remove(ball);
            if (balls.isEmpty()) {
                STAFF.remove(owner);
            }
        }
    }

    /** Helper spell of the lightning to nearby enemies: passive for the relic, active for the staff. */
    @Nullable
    public static Holder<Spell> zapSpell(Level level, BallLightningEntity.Source source) {
        return spell(level, source == BallLightningEntity.Source.STAFF ? STAFF_ZAP_SPELL : ZAP_SPELL);
    }

    /** Helper spell of the discharge into an enemy the ball passes through: passive for the relic, active for the staff. */
    @Nullable
    public static Holder<Spell> dischargeSpell(Level level, BallLightningEntity.Source source) {
        return spell(level, source == BallLightningEntity.Source.STAFF ? STAFF_DISCHARGE_SPELL : DISCHARGE_SPELL);
    }

    @Nullable
    private static Holder<Spell> spell(Level level, ResourceLocation id) {
        return SpellRegistry.from(level).getHolder(id).map(holder -> (Holder<Spell>) holder).orElse(null);
    }

    /** Custom delivery of {@code ball_lightning} (relic): creates the ball. False (no cooldown) if the player still has one. */
    private static boolean deliver(Level world, Holder<Spell> spell, LivingEntity caster, List<SpellExecution.DeliveryTarget> targets,
                                   SpellExecution.ImpactContext context, @Nullable Vec3 targetLocation) {
        if (!(world instanceof ServerLevel level) || !(caster instanceof Player player) || activeBall(player.getUUID()) != null) {
            return false;
        }
        Entity target = targets.isEmpty() ? null : targets.get(0).entity();
        Vec3 aim = target != null ? target.getBoundingBox().getCenter() : targetLocation;
        BallLightningEntity ball = spawn(level, player, BallLightningEntity.Source.RELIC, aim);
        if (ball == null) {
            return false;
        }
        ACTIVE.put(player.getUUID(), ball);
        return true;
    }

    /**
     * Custom delivery of {@code ball_lightning_staff}: a ball towards the point the player looks at. Succeeds whenever the
     * ball exists (no target needed), so Spell Engine takes the cost and sets the cooldown. At the limit of staff balls
     * the oldest one ends first.
     */
    private static boolean deliverStaff(Level world, Holder<Spell> spell, LivingEntity caster, List<SpellExecution.DeliveryTarget> targets,
                                        SpellExecution.ImpactContext context, @Nullable Vec3 targetLocation) {
        if (!(world instanceof ServerLevel level) || !(caster instanceof Player player)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(STAFF_AIM_DISTANCE));
        BlockHitResult look = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 aim = look.getType() == HitResult.Type.MISS ? far : look.getLocation();
        List<BallLightningEntity> live = staffBalls(player.getUUID());
        int max = maxStaffBalls();
        for (int i = 0; i <= live.size() - max; i++) {
            live.get(i).replaced(); // oldest first, makes room for the new one
        }
        BallLightningEntity ball = spawn(level, player, BallLightningEntity.Source.STAFF, aim);
        if (ball == null) {
            return false;
        }
        STAFF.computeIfAbsent(player.getUUID(), uuid -> new ArrayDeque<>()).addLast(ball);
        return true;
    }

    /**
     * Creates a ball in front of the player (chest height) that drifts from the chest towards {@code aim} (or along the
     * view without one); closer if a block is in the way.
     */
    @Nullable
    private static BallLightningEntity spawn(ServerLevel level, Player player, BallLightningEntity.Source source, @Nullable Vec3 aim) {
        Config config = config();
        Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
        Vec3 direction = aim != null ? aim.subtract(chest) : player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-4) {
            direction = player.getLookAngle();
        }
        direction = direction.normalize();
        Vec3 spawn = chest.add(direction.scale(Math.max(0.0, config.spawn_distance)));
        boolean stuck = false;
        BlockHitResult hit = level.clip(new ClipContext(chest, spawn.add(direction.scale(BallLightningEntity.RADIUS)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.MISS) {
            double free = Math.max(0.0, hit.getLocation().distanceTo(chest) - BallLightningEntity.RADIUS);
            spawn = chest.add(direction.scale(free));
            stuck = true;
        }
        BallLightningEntity ball = StormcallerEntities.BALL_LIGHTNING.create(level);
        if (ball == null) {
            return null;
        }
        ball.setPos(spawn.x, spawn.y - ball.getBbHeight() * 0.5, spawn.z);
        ball.setup(player, source, direction, Math.max(0.0F, config.speed) / 20.0, Math.round(config.duration_seconds * 20.0F),
                Math.round(config.pulse_seconds * 20.0F), config.range, config.max_targets, stuck);
        if (!level.addFreshEntity(ball)) {
            return null;
        }
        StormcallerFx.ball(level, spawn, new BallFxPayload(BallFxPayload.Kind.SPAWN, ball.getId(), -1, spawn, level.getRandom().nextInt()));
        SoundEvent release = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("spell_engine:generic_lightning_release"));
        if (release != null) {
            level.playSound(null, spawn.x, spawn.y, spawn.z, release, SoundSource.PLAYERS, 0.6F, 1.5F);
        }
        return ball;
    }
}
