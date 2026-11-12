package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.entity.BallLightningEntity;
import com.drehverschluss.stormcaller.fx.ArcStyle;
import com.drehverschluss.stormcaller.fx.BallFxPayload;
import com.drehverschluss.stormcaller.fx.FxPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client visuals of the ball lightning ({@link BallLightningEntity}), all additive ({@link FxRenderTypes#ARC}, no
 * translucent surfaces, so shader packs draw it like the other lightning):
 * <ul>
 *     <li>the ball itself, drawn every frame: soft yellow halo, bright white-yellow core, many short arcs on the surface
 *     that change every tick, now and then a longer arc reaching out, and a few storm blue glints;</li>
 *     <li>storm blue sparks flying off (client tick) and a quiet crackling sound that follows the ball;</li>
 *     <li>from the server ({@link BallFxPayload}): an arc from the ball to every enemy it strikes (follows both), a strong
 *     discharge into an enemy it passes through, a small flash when it appears and a burst with a small lightning ring
 *     when it ends.</li>
 * </ul>
 * Follows the client config like all lightning visuals (arcs_enabled, strength, max_arcs, "Hide Lightning Flashes").
 */
public final class BallLightningFx {
    /** Storm blue of the sparks (0x5FA8FF). */
    static final int BLUE_R = 95, BLUE_G = 168, BLUE_B = 255;

    private static final double MAX_DISTANCE = 96.0;
    private static final float RADIUS = (float) BallLightningEntity.RADIUS;
    private static final int SURFACE_ARCS = 10;
    private static final int GLINTS = 4;
    private static final int GROW_TICKS = 4;
    private static final float SPARK_RATE = 0.45F;
    private static final ResourceLocation CRACKLE = ResourceLocation.parse("spell_engine:generic_lightning_casting");

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Set<Integer> SOUNDING = new HashSet<>();

    private BallLightningFx() {}

    static void clear() {
        SOUNDING.clear();
    }

    // ------------------------------------------------------------------------------------------------ packets

    static void accept(BallFxPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = RandomSource.create(payload.seed());
        Vec3 pos = payload.pos();
        Entity target = payload.targetId() >= 0 ? level.getEntity(payload.targetId()) : null;
        Vec3 targetPoint = target != null ? target.position().add(0.0, target.getBbHeight() * 0.6, 0.0) : pos;
        switch (payload.kind()) {
            case ZAP -> {
                // as wide as the bolts from the Thunderstorm cloud (style STRIKE at 0.5)
                FxClient.addEntityArc(payload.ballId(), pos, payload.targetId(), targetPoint, 0.6F, 0.075F, 0.38F, 6, 1, payload.seed());
                FxClient.addFlashAt(targetPoint, 0.55F, random);
                for (int i = 0; i < 2; i++) {
                    FxClient.addFreeArc(targetPoint, targetPoint.add(randomDir(random).scale(0.5 + random.nextDouble() * 0.4)),
                            ArcStyle.CHAIN, 0.8F, random.nextInt());
                }
            }
            case DISCHARGE -> {
                FxClient.addEntityArc(payload.ballId(), pos, payload.targetId(), targetPoint, 0.5F, 0.1F, 0.55F, 8, 2, payload.seed());
                FxClient.addFlashAt(targetPoint, 1.5F, random);
                for (int i = 0; i < 5; i++) {
                    FxClient.addFreeArc(targetPoint, targetPoint.add(randomDir(random).scale(0.8 + random.nextDouble() * 0.7)),
                            ArcStyle.STRONG, 0.7F, random.nextInt());
                }
                for (int i = 0; i < 10; i++) {
                    FxClient.addSpark(targetPoint, randomDir(random).scale(0.12 + random.nextDouble() * 0.15), 7, 0.22F);
                }
            }
            case SPAWN -> {
                FxClient.addFlashAt(pos, 0.8F, random);
                for (int i = 0; i < 3; i++) {
                    FxClient.addFreeArc(pos, pos.add(randomDir(random).scale(0.6 + random.nextDouble() * 0.4)), ArcStyle.CHAIN, 0.8F,
                            random.nextInt());
                }
            }
            case POP -> {
                FxClient.addFlashAt(pos, 1.3F, random);
                if (StormcallerClientConfig.arcsEnabled()) {
                    FxClient.local(new FxPayload(FxPayload.Kind.RING, pos, pos, 0, 1.1F, 0, random.nextInt()));
                }
                for (int i = 0; i < 8; i++) {
                    Vec3 dir = randomDir(random);
                    FxClient.addFreeArc(pos.add(dir.scale(RADIUS * 0.5)), pos.add(dir.scale(0.7 + random.nextDouble() * 0.6)),
                            ArcStyle.CHAIN, 0.9F, random.nextInt());
                }
                for (int i = 0; i < 12; i++) {
                    FxClient.addSpark(pos, randomDir(random).scale(0.1 + random.nextDouble() * 0.12), 8, 0.18F);
                }
            }
        }
    }

    private static Vec3 randomDir(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    // ------------------------------------------------------------------------------------------------ tick

    /** Client tick for every ball in the level (from {@link SummonFx}). */
    static void tick(BallLightningEntity ball, double distanceToCamera) {
        if (ball.isRemoved()) {
            return;
        }
        if (SOUNDING.add(ball.getId())) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(CRACKLE);
            if (sound != null) {
                Minecraft.getInstance().getSoundManager().play(new CrackleSound(ball, sound));
            }
        }
        if (distanceToCamera > MAX_DISTANCE || !StormcallerClientConfig.arcsEnabled()) {
            return;
        }
        float rate = SPARK_RATE * (StormcallerClientConfig.reduced() ? 0.5F : 1.0F) * (ball.fadeAge() >= 0 ? 2.0F : 1.0F);
        while (rate > 0.0F) {
            if (RANDOM.nextFloat() < rate) {
                Vec3 dir = randomDir(RANDOM);
                Vec3 center = ball.center();
                FxClient.addSpark(center.add(dir.scale(RADIUS * 1.05)), dir.scale(0.05 + RANDOM.nextDouble() * 0.07), 5 + RANDOM.nextInt(3),
                        0.12F);
            }
            rate -= 1.0F;
        }
    }

    /** End of the client tick: forget sounds of balls that are gone. */
    static void endTick(ClientLevel level) {
        if (!SOUNDING.isEmpty()) {
            SOUNDING.removeIf(id -> !(level.getEntity(id) instanceof BallLightningEntity ball) || ball.isRemoved());
        }
    }

    // ------------------------------------------------------------------------------------------------ render

    static boolean visible(BallLightningEntity ball, Vec3 cam) {
        return !ball.isRemoved() && ball.position().distanceToSqr(cam) < MAX_DISTANCE * MAX_DISTANCE;
    }

    /** The ball itself, every frame (from {@link FxClient}). */
    static void render(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vector3f left, Vector3f up, BallLightningEntity ball,
                       float partialTick) {
        Vec3 center = new Vec3(Mth.lerp(partialTick, ball.xo, ball.getX()),
                Mth.lerp(partialTick, ball.yo, ball.getY()) + ball.getBbHeight() * 0.5,
                Mth.lerp(partialTick, ball.zo, ball.getZ()));
        float age = ball.tickCount + partialTick;
        float size = Mth.clamp(age / GROW_TICKS, 0.3F, 1.0F);
        float flare = 0.0F;
        int fade = ball.fadeAge();
        if (fade >= 0) {
            float t = Mth.clamp((fade + partialTick) / BallLightningEntity.FADE_TICKS, 0.0F, 1.0F);
            size *= 1.0F - 0.75F * t;
            flare = 1.0F - t;
        }
        // close to the camera (first person, just released) it must not fill the screen
        double distance = center.distanceTo(cam);
        float alpha = (float) Mth.clamp((distance - 0.5) / 1.2, 0.25, 1.0);
        boolean reduced = StormcallerClientConfig.reduced();
        boolean hideFlash = Minecraft.getInstance().options.hideLightningFlash().get();
        float r = RADIUS * size;

        // same shapes for the whole tick, new ones every tick
        RandomSource random = RandomSource.create(ball.getId() * 7919L + ball.tickCount);
        float flicker = 0.88F + random.nextFloat() * 0.24F;
        float halo = (hideFlash ? 0.25F : 0.4F) * alpha * (1.0F + flare);
        ArcGeometry.flash(vc, matrix, cam, center, left, up, r * 1.7F * flicker, halo);
        ArcGeometry.flash(vc, matrix, cam, center, left, up, r * 0.85F * flicker, alpha * (0.95F + 0.3F * flare));

        int arcs = reduced ? SURFACE_ARCS / 2 : SURFACE_ARCS;
        float widthScale = reduced ? 0.7F : 1.0F;
        for (int i = 0; i < arcs; i++) {
            List<Vec3> points = surfaceArc(center, r, random);
            ArcGeometry.strip(vc, matrix, cam, points, 0.022F * widthScale, 0.11F * widthScale, alpha * (0.65F + random.nextFloat() * 0.35F), false);
        }
        // now and then one arc reaches out of the ball
        if (random.nextFloat() < (reduced ? 0.3F : 0.55F)) {
            Vec3 dir = randomDir(random);
            Vec3 from = center.add(dir.scale(r * 0.9));
            Vec3 to = center.add(dir.add(randomDir(random).scale(0.5)).normalize().scale(r + 0.35 + random.nextDouble() * 0.45));
            List<Vec3> points = ArcGeometry.jagged(from, to, 0.25F, 0.12F, 0.1F, 8, random);
            ArcGeometry.strip(vc, matrix, cam, points, 0.025F * widthScale, 0.13F * widthScale, alpha * 0.9F, true);
        }
        for (int i = 0; i < GLINTS; i++) {
            Vec3 dir = randomDir(random);
            Vec3 at = center.add(dir.scale(r * (1.02 + random.nextDouble() * 0.3)));
            Vec3 along = dir.cross(randomDir(random)).normalize().scale(0.05 + random.nextDouble() * 0.07);
            ArcGeometry.strip(vc, matrix, cam, List.of(at.subtract(along), at.add(along)), 0.016F, 0.09F, alpha * 0.9F, false,
                    BLUE_R, BLUE_G, BLUE_B);
        }
    }

    /** Short jagged arc along the surface of the ball (part of a great circle). */
    private static List<Vec3> surfaceArc(Vec3 center, float radius, RandomSource random) {
        Vec3 start = randomDir(random);
        Vec3 axis = start.cross(randomDir(random));
        if (axis.lengthSqr() < 1.0E-6) {
            axis = start.cross(new Vec3(0.0, 1.0, 0.0));
        }
        axis = axis.normalize();
        Vec3 side = axis.cross(start);
        double span = Math.toRadians(40.0 + random.nextDouble() * 70.0);
        int n = 6;
        List<Vec3> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double angle = span * i / (n - 1);
            Vec3 dir = start.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
            double jitter = (i == 0 || i == n - 1) ? 0.0 : 1.0;
            double rr = radius * (0.95 + random.nextDouble() * 0.17 * jitter);
            Vec3 wobble = axis.scale((random.nextDouble() * 2.0 - 1.0) * radius * 0.12 * jitter);
            points.add(center.add(dir.scale(rr)).add(wobble));
        }
        return points;
    }

    // ------------------------------------------------------------------------------------------------ sound

    /** Quiet crackling that follows the ball while it lives (looped Spell Engine casting sound). */
    private static final class CrackleSound extends AbstractTickableSoundInstance {
        private final BallLightningEntity ball;

        CrackleSound(BallLightningEntity ball, SoundEvent sound) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.ball = ball;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.22F;
            this.pitch = 1.35F;
            this.x = ball.getX();
            this.y = ball.getY() + ball.getBbHeight() * 0.5;
            this.z = ball.getZ();
        }

        @Override
        public void tick() {
            if (ball.isRemoved()) {
                stop();
                return;
            }
            this.x = ball.getX();
            this.y = ball.getY() + ball.getBbHeight() * 0.5;
            this.z = ball.getZ();
            int fade = ball.fadeAge();
            if (fade >= 0) {
                this.volume = 0.22F * Mth.clamp(1.0F - (float) fade / BallLightningEntity.FADE_TICKS, 0.0F, 1.0F);
            }
        }
    }
}
