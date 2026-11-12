package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Procedural pose of the Storm Elemental for one frame, shared by the puff renderer ({@code client.StormElementalRenderer})
 * and the glow and arcs ({@link ElementalFx}), so core, eyes and arcs always sit on the drawn body.
 * <p>
 * The figure is described in a local frame (x to its right, y up from the ground, z forward, in blocks, about 1.8 tall):
 * head around {@link #HEAD_CENTER}, chest and shoulders around {@link #CORE}, two arms from the shoulders to the hands
 * and a vortex that narrows from the hips down to a tip. This class moves the frame:
 * <ul>
 *     <li>hovering {@link #HOVER} blocks above the ground with a slow up and down,</li>
 *     <li>the head turns towards the look direction (and target), limited to ±60° to the body,</li>
 *     <li>leaning forward with the speed, the arms and the vortex trail a bit behind the movement,</li>
 *     <li>the hands reach forward while casting ({@code clientCast}).</li>
 * </ul>
 * Appearing, vanishing and being hurt are values for the renderer ({@link #appear}, {@link #vanish}, {@link #hurt}).
 */
public final class ElementalPose {
    /** Points of the body the arcs inside the body connect. */
    public enum Anchor { CORE, HEAD, HAND_LEFT, HAND_RIGHT, SHOULDER_LEFT, SHOULDER_RIGHT, TIP }

    public static final double HOVER = 0.2;
    public static final Vec3 CORE = new Vec3(0.0, 1.15, 0.05);
    public static final Vec3 HEAD_CENTER = new Vec3(0.0, 1.55, 0.0);
    /** Pivot of the head (neck). */
    private static final Vec3 NECK = new Vec3(0.0, 1.40, 0.0);
    /** Pivot of the lean (hips). */
    private static final double LEAN_PIVOT = 0.9;
    private static final double MAX_HEAD_YAW = Math.toRadians(60.0);
    /** Top and bottom of the vortex below the chest. */
    public static final double VORTEX_TOP = 0.98;
    public static final double VORTEX_BOTTOM = 0.22;
    public static final double VORTEX_TOP_RADIUS = 0.24;
    public static final double VORTEX_BOTTOM_RADIUS = 0.025;
    /** Vortex spin, radians per tick. */
    public static final double VORTEX_SPIN = 0.12;
    private static final int APPEAR_TICKS = 20;
    private static final int VANISH_TICKS = 20;

    /** Interpolated entity position (world), the origin of all offsets. */
    public final Vec3 base;
    /** Age in ticks including the partial tick. */
    public final float age;
    /** 0 = arms down, 1 = reaching forward (casting). */
    public final float cast;
    /** 0..1 extra core brightness after a release. */
    public final float coreBoost;
    /** 0 while forming, 1 when complete. */
    public final float appear;
    /** 0 normally, rising to 1 while drifting apart (despawn or death). */
    public final float vanish;
    /** 0..1 right after taking damage. */
    public final float hurt;
    /** Movement of the last tick (world), used for the trailing puffs. */
    public final Vec3 velocity;
    private final double sinYaw;
    private final double cosYaw;
    private final double headYaw;
    private final double headPitch;
    private final double lean;
    private final double hover;
    private final double scale;

    private ElementalPose(StormElementalEntity e, float partialTick) {
        this.base = new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()),
                Mth.lerp(partialTick, e.zo, e.getZ()));
        this.age = e.tickCount + partialTick;
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        float headYawAbs = Mth.rotLerp(partialTick, e.yHeadRotO, e.yHeadRot);
        double yaw = Math.toRadians(bodyYaw);
        this.sinYaw = Math.sin(yaw);
        this.cosYaw = Math.cos(yaw);
        this.headYaw = Mth.clamp(Math.toRadians(Mth.wrapDegrees(headYawAbs - bodyYaw)), -MAX_HEAD_YAW, MAX_HEAD_YAW);
        this.headPitch = Math.toRadians(Mth.clamp(e.getViewXRot(partialTick), -40.0F, 40.0F)) * 0.6;
        this.velocity = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        this.lean = Mth.clamp(speed * 1.6, 0.0, 0.35);
        this.hover = HOVER + 0.07 * Math.sin(age * 0.1);
        this.scale = e.getScale();
        this.cast = Mth.lerp(partialTick, e.clientCastPrev, e.clientCast);
        this.coreBoost = Mth.lerp(partialTick, e.clientCoreBoostPrev, e.clientCoreBoost);
        this.appear = e.isSpawning() ? Mth.clamp((e.tickCount + partialTick) / APPEAR_TICKS, 0.0F, 1.0F) : 1.0F;
        float despawn = e.clientDespawnStart >= 0 ? (e.tickCount - e.clientDespawnStart + partialTick) / VANISH_TICKS : 0.0F;
        float death = e.deathTime > 0 ? (e.deathTime + partialTick) / VANISH_TICKS : 0.0F;
        this.vanish = Mth.clamp(Math.max(despawn, death), 0.0F, 1.0F);
        this.hurt = e.hurtTime > 0 ? Mth.clamp((e.hurtTime - partialTick) / 10.0F, 0.0F, 1.0F) : 0.0F;
    }

    public static ElementalPose of(StormElementalEntity entity, float partialTick) {
        return new ElementalPose(entity, partialTick);
    }

    /**
     * Offset from {@link #base} (world axes) of a point in the local frame.
     *
     * @param head  the point belongs to the head (turns with the look direction)
     * @param trail 0..1, how far the point trails behind the movement
     */
    public Vec3 offset(double x, double y, double z, boolean head, double trail) {
        if (head) {
            double dx = x - NECK.x;
            double dy = y - NECK.y;
            double dz = z - NECK.z;
            // pitch around x (positive looks down), then yaw around y
            double cp = Math.cos(headPitch);
            double sp = Math.sin(headPitch);
            double y1 = dy * cp - dz * sp;
            double z1 = dy * sp + dz * cp;
            double cy = Math.cos(headYaw);
            double sy = Math.sin(headYaw);
            // Minecraft yaw turns clockwise seen from above: forward (0, 0, 1) becomes (sin, 0, cos)
            double x2 = dx * cy + z1 * sy;
            double z2 = -dx * sy + z1 * cy;
            x = NECK.x + x2;
            y = NECK.y + y1;
            z = NECK.z + z2;
        }
        // lean forward around the hips
        double ly = y - LEAN_PIVOT;
        double cl = Math.cos(lean);
        double sl = Math.sin(lean);
        double y3 = LEAN_PIVOT + ly * cl - z * sl;
        double z3 = ly * sl + z * cl;
        y3 += hover;
        x *= scale;
        y3 *= scale;
        z3 *= scale;
        // body yaw: forward = (-sin, 0, cos), right = (-cos, 0, -sin)
        double wx = x * -cosYaw + z3 * -sinYaw;
        double wz = x * -sinYaw + z3 * cosYaw;
        Vec3 out = new Vec3(wx, y3, wz);
        if (trail > 0.0) {
            out = out.subtract(velocity.x * 1.6 * trail, 0.0, velocity.z * 1.6 * trail);
        }
        return out;
    }

    public Vec3 offset(Vec3 local, boolean head, double trail) {
        return offset(local.x, local.y, local.z, head, trail);
    }

    public Vec3 world(Vec3 local, boolean head, double trail) {
        return base.add(offset(local, head, trail));
    }

    /** Local point along an arm, {@code t} = 0 at the shoulder, 1 at the hand. */
    public Vec3 armLocal(boolean left, double t) {
        double side = left ? -1.0 : 1.0;
        double phase = left ? 1.7 : 0.0;
        Vec3 shoulder = new Vec3(side * 0.34, 1.30, 0.0);
        Vec3 idle = new Vec3(side * 0.50, 0.84 + 0.03 * Math.sin(age * 0.1 + phase), 0.06 + 0.04 * Math.sin(age * 0.08 + phase));
        Vec3 reach = new Vec3(side * 0.20, 1.24, 0.64);
        Vec3 hand = idle.lerp(reach, cast);
        Vec3 p = shoulder.lerp(hand, t);
        // a slight bow outwards
        return p.add(side * 0.06 * Math.sin(Math.PI * t), 0.0, 0.0);
    }

    /** Local point of the vortex, {@code t} = 0 at the hips, 1 at the tip, with the angle of the spinning spiral. */
    public Vec3 vortexLocal(double t, double phase) {
        double y = Mth.lerp(t, VORTEX_TOP, VORTEX_BOTTOM);
        double r = Mth.lerp(t, VORTEX_TOP_RADIUS, VORTEX_BOTTOM_RADIUS);
        double angle = phase + t * Math.PI * 4.0 + age * VORTEX_SPIN;
        return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
    }

    /** World position of an anchor. */
    public Vec3 anchor(Anchor anchor) {
        return switch (anchor) {
            case CORE -> world(CORE, false, 0.0);
            case HEAD -> world(HEAD_CENTER, true, 0.0);
            case HAND_LEFT -> world(armLocal(true, 1.0), false, 1.0);
            case HAND_RIGHT -> world(armLocal(false, 1.0), false, 1.0);
            case SHOULDER_LEFT -> world(armLocal(true, 0.0), false, 0.0);
            case SHOULDER_RIGHT -> world(armLocal(false, 0.0), false, 0.0);
            case TIP -> world(vortexLocal(1.0, 0.0), false, 1.0);
        };
    }

    /** World positions of the two eyes (narrow slits at the front of the head): left end and right end of each. */
    public Vec3[] eyes() {
        Vec3[] eyes = new Vec3[4];
        double[] xs = {-0.105, -0.035, 0.035, 0.105};
        for (int i = 0; i < 4; i++) {
            eyes[i] = world(new Vec3(xs[i], 1.58, 0.19), true, 0.0);
        }
        return eyes;
    }

    /** Up axis of the head in world space (unit vector), turns with pitch and lean: the width of the eye slits. */
    public Vec3 eyeUp() {
        Vec3 up = offset(0.0, 1.78, 0.19, true, 0.0).subtract(offset(0.0, 1.58, 0.19, true, 0.0));
        return up.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : up.normalize();
    }
}
