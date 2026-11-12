package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Glow and lightning of the Storm Elemental on the client (the cloud puffs are drawn by
 * {@code client.StormElementalRenderer}):
 * <ul>
 *     <li><b>Core:</b> a yellow-white additive glow in the chest that pulses slowly and flares up when a spell is
 *     released. It is drawn a bit towards the camera, so it shines through the cloud body from every side.</li>
 *     <li><b>Eyes:</b> two narrow bright slits at the front of the head, turning with the head. Seen from behind, the
 *     head puffs hide them.</li>
 *     <li><b>Arcs inside the body:</b> every {@link #INTERVAL} ticks (sometimes one more in between) an arc from the core
 *     to the head, a hand, a shoulder or the tip of the vortex. The puffs around both ends light up
 *     ({@link PuffGlows}).</li>
 * </ul>
 * It also keeps the client state of the elemental up to date (arms forward while casting, core flare, start of the
 * despawn). Everything follows the client config: no arcs with {@code arcs_enabled = false}, REDUCED draws fewer and
 * thinner arcs and a weaker flare, "Hide Lightning Flashes" dims the flare. Nothing beyond {@link #MAX_DISTANCE} blocks.
 */
public final class ElementalFx {
    private static final int INTERVAL = 4;
    private static final int REDUCED_INTERVAL = 6;
    private static final double MAX_DISTANCE = 48.0;
    private static final double RENDER_DISTANCE = 96.0;
    private static final float CAST_SPEED = 0.3F;
    private static final float CORE_FADE = 0.12F;
    private static final float GLOW_RANGE = 0.45F;
    private static final ElementalPose.Anchor[] TARGETS = {
            ElementalPose.Anchor.HEAD, ElementalPose.Anchor.HAND_LEFT, ElementalPose.Anchor.HAND_RIGHT, ElementalPose.Anchor.TIP,
            ElementalPose.Anchor.TIP, ElementalPose.Anchor.SHOULDER_LEFT, ElementalPose.Anchor.SHOULDER_RIGHT,
            ElementalPose.Anchor.HAND_LEFT, ElementalPose.Anchor.HAND_RIGHT,
    };
    private static final RandomSource RANDOM = RandomSource.create();

    private ElementalFx() {}

    /** Client tick for one elemental ({@link SummonFx}). */
    static void tick(StormElementalEntity e, double distanceToCamera) {
        // arms forward while casting or releasing
        boolean casting = e.spellCastAnimationState.isStarted() || e.spellReleaseAnimationState.isStarted();
        e.clientCastPrev = e.clientCast;
        e.clientCast += ((casting ? 1.0F : 0.0F) - e.clientCast) * CAST_SPEED;
        // core flare on release
        e.clientCoreBoostPrev = e.clientCoreBoost;
        e.clientCoreBoost = Math.max(0.0F, e.clientCoreBoost - CORE_FADE);
        if (e.spellReleaseAnimationState.isStarted()) {
            if (!e.clientReleaseSeen) {
                e.clientReleaseSeen = true;
                e.clientCoreBoost = 1.0F;
                e.clientCoreBoostPrev = 1.0F;
            }
        } else {
            e.clientReleaseSeen = false;
        }
        if (e.isDespawning() && e.clientDespawnStart < 0) {
            e.clientDespawnStart = e.tickCount;
        }

        if (e.isInvisible() || e.isRemoved() || distanceToCamera > MAX_DISTANCE) {
            return;
        }
        ElementalPose pose = ElementalPose.of(e, 0.0F);
        if (pose.appear < 0.6F || pose.vanish > 0.5F) {
            return;
        }
        int interval = StormcallerClientConfig.reduced() ? REDUCED_INTERVAL : INTERVAL;
        int phase = Math.floorMod(e.tickCount + e.getId() * 3, interval);
        boolean main = phase == 0;
        boolean extra = phase == interval / 2 && RANDOM.nextFloat() < 0.35F;
        if (!main && !extra) {
            return;
        }
        ElementalPose.Anchor target = TARGETS[RANDOM.nextInt(TARGETS.length)];
        FxClient.addElementalArc(e, target, 1.0F, 1.1F, RANDOM);
        if (StormcallerClientConfig.arcsEnabled()) {
            PuffGlows.add(e.getId(), pose.anchor(ElementalPose.Anchor.CORE), 0.6F, GLOW_RANGE);
            PuffGlows.add(e.getId(), pose.anchor(target), 1.0F, GLOW_RANGE);
        }
    }

    /** Worth drawing core and eyes? */
    static boolean visible(StormElementalEntity e, Vec3 cam) {
        return !e.isInvisible() && !e.isRemoved() && e.position().distanceToSqr(cam) < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    /** Core and eyes (additive, after the translucent pass). */
    static void render(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vector3f left, Vector3f up, StormElementalEntity e,
                       float partialTick) {
        ElementalPose pose = ElementalPose.of(e, partialTick);
        float presence = pose.appear * (1.0F - pose.vanish);
        if (presence <= 0.01F) {
            return;
        }
        boolean reduced = StormcallerClientConfig.reduced();
        boolean hideFlash = Minecraft.getInstance().options.hideLightningFlash().get();
        float boost = pose.coreBoost * (reduced ? 0.6F : 1.0F) * (hideFlash ? 0.4F : 1.0F);

        // core: pulses slowly, flares up on release; pulled towards the camera so it glows through the body
        Vec3 core = pose.anchor(ElementalPose.Anchor.CORE);
        Vec3 toCamera = cam.subtract(core);
        double distance = toCamera.length();
        if (distance > 0.4) {
            core = core.add(toCamera.scale(0.3 / distance));
        }
        float pulse = 0.5F + 0.5F * Mth.sin(pose.age * 0.12F);
        float radius = (0.15F + 0.03F * pulse + 0.2F * boost) * presence;
        float alpha = Mth.clamp((0.6F + 0.15F * pulse + 0.4F * boost) * presence, 0.0F, 1.0F);
        ArcGeometry.flash(vc, matrix, cam, core, left, up, radius, alpha);

        // eyes: two short bright slits, flat in the face (a camera-facing strip turned into an upright bar seen from the side)
        Vec3[] eyes = pose.eyes();
        Vec3 eyeUp = pose.eyeUp();
        float eyeAlpha = 0.95F * presence;
        ArcGeometry.slit(vc, matrix, cam, eyes[0], eyes[1], eyeUp, 0.03F, 0.08F, eyeAlpha);
        ArcGeometry.slit(vc, matrix, cam, eyes[2], eyes[3], eyeUp, 0.03F, 0.08F, eyeAlpha);
    }
}
