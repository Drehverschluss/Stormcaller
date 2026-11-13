package com.drehverschluss.stormcaller.entity;

import com.drehverschluss.stormcaller.fx.StormcallerFx;
import com.drehverschluss.stormcaller.spell.MaxTargets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellCloud;
import org.jetbrains.annotations.Nullable;

/**
 * Cloud of Thunderstorm: a Spell Engine {@link SpellCloud} (same data, timing and impacts) with three additions.
 * <ul>
 *     <li><b>Height:</b> the cloud floats {@link #PREFERRED_BASE} blocks above the ground point of the spell cloud, lower
 *     under a low ceiling (caves). Server and client compute it the same way from the blocks ({@link #cloudBase()}).</li>
 *     <li><b>Hit area:</b> a column from the ground up to the cloud base instead of Spell Engine's sphere around the
 *     ground point, so targets hovering above the ground (Blaze, Vex) are hit up to just below the cloud. Done by
 *     {@code mixin/SpellImpactsMixin} through {@link #columnTargets}. Without the mixin the cloud keeps the sphere.</li>
 *     <li><b>Target limit:</b> one hit pulse strikes at most the number of enemies given by the marker impact
 *     {@code stormcaller:max_targets/<n>} of the spell, picked at random ({@link MaxTargets}). Also done in
 *     {@link #columnTargets}.</li>
 *     <li><b>Visuals:</b> a lightning ring of the cloud radius on the ground when it appears. The dark cloud, the
 *     random arcs inside it and the bolts without target are drawn by the client ({@code client.StormCloudRenderer},
 *     {@code fx.client.StormCloudFx}); the bolt to every target per hit comes from the impact
 *     {@code stormcaller:fx_cloud_bolt}.</li>
 * </ul>
 * Radius and duration come from the spell and its modifiers like for every Spell Engine cloud; the visuals read the
 * radius from the synced bounding box (width = 2 × radius), so they grow with radius modifiers.
 */
public class StormCloudEntity extends SpellCloud {
    /** Height of the cloud base above the ground point, in blocks. */
    public static final double PREFERRED_BASE = 5.0;
    /** Lowest cloud base under a low ceiling. */
    public static final double MIN_BASE = 1.6;
    /** Thickness of the cloud body above its base (visual). */
    public static final double THICKNESS = 1.5;
    /** Targets are hit if their feet are below the cloud base minus this gap. */
    private static final double COLUMN_GAP = 0.0;
    /** Targets whose top is further below the ground point than this are not hit. */
    private static final double COLUMN_BELOW = 1.0;
    private static final int BASE_REFRESH_TICKS = 40;

    private double cachedBase = -1.0;
    private int cachedAt;
    private boolean ringSent;
    /** Client: tick at which the despawn phase was first seen (fade out), -1 before. */
    public int clientDespawnStart = -1;
    /** Server: number of hit pulses so far and the game time of the last one (Rolling Thunder). */
    private int pulses;
    private long lastPulseTime = Long.MIN_VALUE;

    public StormCloudEntity(EntityType<? extends SpellCloud> type, Level level) {
        super(type, level);
    }

    /** Radius of the cloud in blocks (Spell Engine syncs it as the bounding box width). */
    public float radius() {
        return getBbWidth() * 0.5F;
    }

    /**
     * Server: number of the current hit pulse of this cloud, counted from 1. All targets of one impact tick share a
     * pulse (they are hit in the same game tick).
     */
    public int pulse(long gameTime) {
        if (gameTime != lastPulseTime) {
            lastPulseTime = gameTime;
            pulses++;
        }
        return pulses;
    }

    /** Height of the cloud base above {@link #position()} (the ground point), cached and refreshed every 2 s. */
    public double cloudBase() {
        if (cachedBase < 0.0 || tickCount - cachedAt >= BASE_REFRESH_TICKS) {
            cachedBase = computeBase(level(), position(), radius());
            cachedAt = tickCount;
        }
        return cachedBase;
    }

    /**
     * Cloud base above {@code ground}: {@link #PREFERRED_BASE}, or lower so the cloud body ({@link #THICKNESS}) stays
     * below a ceiling. Looks straight up from the center and from four points at 60 % of the radius (points inside a
     * solid block are skipped, e.g. next to a wall).
     */
    public static double computeBase(Level level, Vec3 ground, double radius) {
        double free = PREFERRED_BASE + THICKNESS + 0.3;
        double r = radius * 0.6;
        double[][] offsets = {{0.0, 0.0}, {r, 0.0}, {-r, 0.0}, {0.0, r}, {0.0, -r}};
        for (double[] offset : offsets) {
            Vec3 from = ground.add(offset[0], 0.2, offset[1]);
            BlockPos start = BlockPos.containing(from);
            if (!level.getBlockState(start).getCollisionShape(level, start).isEmpty()) {
                continue;
            }
            Vec3 to = from.add(0.0, free, 0.0);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                free = Math.min(free, hit.getLocation().y - ground.y);
            }
        }
        return Math.max(MIN_BASE, Math.min(PREFERRED_BASE, free - THICKNESS));
    }

    /**
     * Targets of one impact tick in the column below the cloud, limited to the target count of the spell.
     *
     * @param origin ground point of the cloud (impact position)
     * @param radius current cloud radius
     * @param lookup Spell Engine's own area lookup for a given range (line of sight, spectators)
     * @param spell  the cloud's spell (target limit {@code stormcaller:max_targets/<n>}), may be null
     * @param caster the caster (relations for the limit), may be null
     */
    public List<Entity> columnTargets(Vec3 origin, float radius, Function<Float, List<Entity>> lookup,
                                      @Nullable Holder<Spell> spell, @Nullable LivingEntity caster) {
        double top = cloudBase() - COLUMN_GAP;
        float range = (float) Math.sqrt(radius * radius + (top + 1.0) * (top + 1.0)) + 0.5F;
        List<Entity> result = new ArrayList<>();
        for (Entity entity : lookup.apply(range)) {
            double dx = entity.getX() - origin.x;
            double dz = entity.getZ() - origin.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            double feet = entity.getY() - origin.y;
            double head = entity.getBoundingBox().maxY - origin.y;
            if (feet < top && head > -COLUMN_BELOW) {
                result.add(entity);
            }
        }
        return MaxTargets.limit(result, caster, MaxTargets.of(spell, caster), getRandom());
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            if (!ringSent) {
                ringSent = true;
                refreshDimensions();
                StormcallerFx.ring(server, position().add(0.0, 0.15, 0.0), radius());
            }
        } else if (isDespawning() && clientDespawnStart < 0) {
            clientDespawnStart = tickCount;
        }
    }

    /** The cloud floats above the entity box: keep it rendered when only the cloud is in view. */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = getBoundingBox();
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + PREFERRED_BASE + THICKNESS + 1.0, box.maxZ)
                .inflate(1.5, 0.0, 1.5);
    }
}
