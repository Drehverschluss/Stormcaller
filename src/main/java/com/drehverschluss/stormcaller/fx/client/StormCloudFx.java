package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.entity.StormCloudEntity;
import com.drehverschluss.stormcaller.fx.ArcStyle;
import com.drehverschluss.stormcaller.fx.FxPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Client visuals of the Thunderstorm cloud that need no packets:
 * <ul>
 *     <li>short arcs that flicker inside the cloud now and then,</li>
 *     <li>now and then a bolt from the cloud to the ground without a target (only visual),</li>
 *     <li>the glow of the cloud puffs around every arc that starts inside the cloud (also the bolts to the targets that
 *     come from the server), read by {@code client.StormCloudRenderer}.</li>
 * </ul>
 * Every player sees other random arcs, which is fine here. The rates grow with the cloud area, so a bigger cloud
 * (Stormweave, Skill Tree) does not look emptier. Arcs follow the client config like all others (arcs_enabled,
 * strength, max_arcs); REDUCED halves the rates, "Hide Lightning Flashes" dims the glow.
 */
public final class StormCloudFx {
    /** In-cloud arcs per tick for a cloud of radius 4. */
    private static final float CLOUD_ARC_RATE = 0.22F;
    /** Bolts to the ground without a target per tick for a cloud of radius 4 (about one every 1.7 s). */
    private static final float GROUND_BOLT_RATE = 1.0F / 35.0F;
    private static final double MAX_DISTANCE = 128.0;
    private static final int GLOW_TICKS = 5;
    private static final double GLOW_RANGE = 3.2;

    private record Glow(Vec3 pos, float strength, long tick) {}

    private static final Map<Integer, List<Glow>> GLOWS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long clientTick;

    private StormCloudFx() {}

    static void clear() {
        GLOWS.clear();
    }

    /** Called for every arc that is shown: a start point inside a storm cloud lights the cloud up there. */
    static void onArc(Vec3 from) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        AABB search = new AABB(from, from).inflate(10.0, 9.0, 10.0);
        for (StormCloudEntity cloud : level.getEntitiesOfClass(StormCloudEntity.class, search)) {
            if (inside(cloud, from, 1.0)) {
                GLOWS.computeIfAbsent(cloud.getId(), k -> new ArrayList<>()).add(new Glow(from, 1.0F, clientTick));
            }
        }
    }

    private static boolean inside(StormCloudEntity cloud, Vec3 p, double margin) {
        Vec3 ground = cloud.position();
        double dx = p.x - ground.x;
        double dz = p.z - ground.z;
        double r = cloud.radius() + margin;
        double y = p.y - ground.y;
        double base = cloud.cloudBase();
        return dx * dx + dz * dz <= r * r && y >= base - 0.8 && y <= base + StormCloudEntity.THICKNESS + 0.6;
    }

    /** Brightness added to a cloud puff at {@code pos} (0 = none, about 1 = right at a fresh flash). */
    public static float glowAt(StormCloudEntity cloud, Vec3 pos, float partialTick) {
        List<Glow> glows = GLOWS.get(cloud.getId());
        if (glows == null || glows.isEmpty()) {
            return 0.0F;
        }
        float total = 0.0F;
        for (Glow glow : glows) {
            float age = (clientTick - glow.tick()) + partialTick;
            float fade = Mth.clamp(1.0F - age / GLOW_TICKS, 0.0F, 1.0F);
            if (fade <= 0.0F) {
                continue;
            }
            double distance = glow.pos().distanceTo(pos);
            float near = (float) Mth.clamp(1.0 - distance / GLOW_RANGE, 0.0, 1.0);
            total += glow.strength() * fade * near * near;
        }
        float scale = Minecraft.getInstance().options.hideLightningFlash().get() ? 0.35F : 1.0F;
        return Math.min(1.0F, total) * scale;
    }

    /** Client tick, once for every storm cloud in the level ({@link SummonFx}). */
    static void tickCloud(StormCloudEntity cloud, double distanceToCamera) {
        if (distanceToCamera > MAX_DISTANCE || cloud.isRemoved()) {
            return;
        }
        float r = cloud.radius();
        if (r <= 0.1F) {
            return;
        }
        float rate = Math.max(0.5F, r * r / 16.0F) * (StormcallerClientConfig.reduced() ? 0.5F : 1.0F);
        if (cloud.isDespawning()) {
            rate *= 0.4F;
        }
        Vec3 ground = cloud.position();
        double base = cloud.cloudBase();
        if (RANDOM.nextFloat() < CLOUD_ARC_RATE * rate) {
            Vec3 a = pointInCloud(ground, r * 0.85, base);
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double length = 1.2 + RANDOM.nextDouble() * 1.8;
            Vec3 b = a.add(Math.cos(angle) * length, (RANDOM.nextDouble() - 0.5) * 0.6, Math.sin(angle) * length);
            FxClient.local(new FxPayload(FxPayload.Kind.ARC, a, b, ArcStyle.CHAIN.ordinal(), 0.9F, 0, RANDOM.nextInt()));
        }
        if (cloud.isActive() && RANDOM.nextFloat() < GROUND_BOLT_RATE * rate) {
            groundBolt(cloud, ground, r, base);
        }
    }

    private static void groundBolt(StormCloudEntity cloud, Vec3 ground, float r, double base) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(RANDOM.nextDouble()) * r * 0.85;
        Vec3 top = new Vec3(ground.x + Math.cos(angle) * distance, ground.y + base + 0.2, ground.z + Math.sin(angle) * distance);
        Vec3 bottom = new Vec3(top.x, ground.y - 3.0, top.z);
        BlockHitResult hit = cloud.level().clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        Vec3 end = hit.getLocation();
        if (top.y - end.y < 1.0) {
            return;
        }
        Vec3 start = top.add((RANDOM.nextDouble() - 0.5) * 0.8, 0.0, (RANDOM.nextDouble() - 0.5) * 0.8);
        FxClient.local(new FxPayload(FxPayload.Kind.ARC, start, end, ArcStyle.STRIKE.ordinal(), 0.42F, 0, RANDOM.nextInt()));
        FxClient.local(new FxPayload(FxPayload.Kind.FLASH, end.add(0.0, 0.15, 0.0), end, 0, 0.7F, 0, RANDOM.nextInt()));
    }

    private static Vec3 pointInCloud(Vec3 ground, double radius, double base) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(RANDOM.nextDouble()) * radius;
        double y = base + 0.2 + RANDOM.nextDouble() * (StormCloudEntity.THICKNESS * 0.6);
        return new Vec3(ground.x + Math.cos(angle) * distance, ground.y + y, ground.z + Math.sin(angle) * distance);
    }

    /** End of the client tick: forget old glows and clouds that are gone. */
    static void endTick(ClientLevel level) {
        clientTick++;
        Iterator<Map.Entry<Integer, List<Glow>>> it = GLOWS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Glow>> entry = it.next();
            entry.getValue().removeIf(glow -> clientTick - glow.tick() > GLOW_TICKS);
            if (entry.getValue().isEmpty() || level.getEntity(entry.getKey()) == null) {
                it.remove();
            }
        }
    }
}
