package com.drehverschluss.stormcaller.fx.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Builds and draws the lightning geometry: jagged polylines as camera-facing strips (white core plus soft yellow glow)
 * and round flashes. All positions are world positions; drawing subtracts the camera position in double precision.
 */
final class ArcGeometry {
    /** Color.ELECTRIC of Spell Engine (0xFFFF66). */
    static final int GLOW_R = 255, GLOW_G = 255, GLOW_B = 102;

    private ArcGeometry() {}

    /**
     * Jagged polyline from {@code a} to {@code b}. The ends stay fixed, the middle zigzags the most.
     *
     * @param jag        amplitude relative to the length
     * @param maxJag     amplitude cap in blocks
     * @param segmentLen rough length of one segment in blocks
     */
    static List<Vec3> jagged(Vec3 a, Vec3 b, float jag, float maxJag, float segmentLen, int maxSegments, RandomSource random) {
        Vec3 delta = b.subtract(a);
        double length = delta.length();
        List<Vec3> points = new ArrayList<>();
        if (length < 1.0E-4) {
            points.add(a);
            points.add(b);
            return points;
        }
        Vec3 dir = delta.scale(1.0 / length);
        Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();
        int segments = Mth.clamp((int) Math.round(length / segmentLen), 3, maxSegments);
        double amplitude = Math.min(length * jag, maxJag);
        points.add(a);
        for (int i = 1; i < segments; i++) {
            double t = (double) i / segments;
            double envelope = Math.sqrt(Math.sin(Math.PI * t)) * amplitude;
            double ou = (random.nextDouble() * 2.0 - 1.0) * envelope;
            double ov = (random.nextDouble() * 2.0 - 1.0) * envelope;
            points.add(a.add(delta.scale(t)).add(u.scale(ou)).add(v.scale(ov)));
        }
        points.add(b);
        return points;
    }

    /**
     * Small side branches off a main polyline.
     *
     * @param downward bias the branches downwards (strikes)
     */
    static List<List<Vec3>> branches(List<Vec3> main, int count, float jag, float maxJag, boolean downward, RandomSource random) {
        List<List<Vec3>> result = new ArrayList<>();
        int n = main.size();
        if (n < 4 || count <= 0) {
            return result;
        }
        Vec3 start = main.get(0);
        Vec3 end = main.get(n - 1);
        Vec3 mainDelta = end.subtract(start);
        double mainLength = mainDelta.length();
        Vec3 dir = mainDelta.normalize();
        Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();
        for (int i = 0; i < count; i++) {
            int index = Mth.clamp((int) (n * (0.2 + random.nextDouble() * 0.5)), 1, n - 2);
            Vec3 from = main.get(index);
            Vec3 branchDir = dir.add(u.scale((random.nextDouble() * 2.0 - 1.0) * 1.1))
                    .add(v.scale((random.nextDouble() * 2.0 - 1.0) * 1.1));
            if (downward) {
                branchDir = branchDir.add(0.0, -0.4, 0.0);
            }
            double length = Math.min(mainLength * (0.15 + random.nextDouble() * 0.15), 5.0);
            Vec3 to = from.add(branchDir.normalize().scale(length));
            result.add(jagged(from, to, jag * 1.4F, maxJag * 0.6F, 0.35F, 12, random));
        }
        return result;
    }

    /**
     * Draws a polyline as a strip facing the camera: soft glow on both sides of the line and a white core.
     *
     * @param taper the strip gets narrower towards the last point (branches)
     */
    static void strip(VertexConsumer vc, Matrix4f m, Vec3 cam, List<Vec3> points, float coreWidth, float glowWidth,
                      float alpha, boolean taper) {
        strip(vc, m, cam, points, coreWidth, glowWidth, alpha, taper, GLOW_R, GLOW_G, GLOW_B);
    }

    /** As above with another glow colour (the white core stays). */
    static void strip(VertexConsumer vc, Matrix4f m, Vec3 cam, List<Vec3> points, float coreWidth, float glowWidth,
                      float alpha, boolean taper, int glowR, int glowG, int glowB) {
        int n = points.size();
        if (n < 2 || alpha <= 0.003F) {
            return;
        }
        Vec3[] sides = new Vec3[n];
        float[] widthFactor = new float[n];
        Vec3 previous = null;
        for (int i = 0; i < n; i++) {
            Vec3 tangent = points.get(Math.min(i + 1, n - 1)).subtract(points.get(Math.max(i - 1, 0)));
            Vec3 view = points.get(i).subtract(cam);
            Vec3 side = tangent.cross(view);
            double len = side.length();
            // nearly end-on (line points at the camera): the side vector is unstable, keep the previous one
            if (len < 0.08 * tangent.length() * view.length() && previous != null) {
                side = previous;
            } else if (len < 1.0E-9) {
                side = new Vec3(0.0, 1.0, 0.0);
            } else {
                side = side.scale(1.0 / len);
            }
            // keep the orientation, otherwise the strip twists into a bow tie
            if (previous != null && side.dot(previous) < 0.0) {
                side = side.scale(-1.0);
            }
            sides[i] = side;
            previous = side;
            widthFactor[i] = taper ? 1.0F - 0.75F * i / (n - 1) : 1.0F;
        }
        int glowAlpha = alphaInt(0.55F * alpha);
        int coreAlpha = alphaInt(alpha);
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = points.get(i);
            Vec3 p1 = points.get(i + 1);
            float g0 = glowWidth * 0.5F * widthFactor[i];
            float g1 = glowWidth * 0.5F * widthFactor[i + 1];
            float c0 = coreWidth * 0.5F * widthFactor[i];
            float c1 = coreWidth * 0.5F * widthFactor[i + 1];
            for (int s = -1; s <= 1; s += 2) {
                vertex(vc, m, cam, p0.add(sides[i].scale(s * g0)), glowR, glowG, glowB, 0);
                vertex(vc, m, cam, p0, glowR, glowG, glowB, glowAlpha);
                vertex(vc, m, cam, p1, glowR, glowG, glowB, glowAlpha);
                vertex(vc, m, cam, p1.add(sides[i + 1].scale(s * g1)), glowR, glowG, glowB, 0);
            }
            vertex(vc, m, cam, p0.subtract(sides[i].scale(c0)), 255, 255, 255, coreAlpha);
            vertex(vc, m, cam, p0.add(sides[i].scale(c0)), 255, 255, 255, coreAlpha);
            vertex(vc, m, cam, p1.add(sides[i + 1].scale(c1)), 255, 255, 255, coreAlpha);
            vertex(vc, m, cam, p1.subtract(sides[i + 1].scale(c1)), 255, 255, 255, coreAlpha);
        }
    }

    /** Round flash facing the camera: yellow halo with a white center, both fading to the edge. */
    static void flash(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 center, Vector3f left, Vector3f up, float radius, float alpha) {
        if (alpha <= 0.003F || radius <= 0.0F) {
            return;
        }
        disc(vc, m, cam, center, left, up, radius, GLOW_R, GLOW_G, GLOW_B, alphaInt(0.6F * alpha));
        disc(vc, m, cam, center, left, up, radius * 0.4F, 255, 255, 255, alphaInt(alpha));
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 center, Vector3f left, Vector3f up,
                             float radius, int r, int g, int b, int centerAlpha) {
        int segments = 16;
        for (int k = 0; k < segments; k++) {
            double a0 = Math.PI * 2.0 * k / segments;
            double a1 = Math.PI * 2.0 * (k + 1) / segments;
            Vec3 e0 = center.add(left.x() * Math.cos(a0) * radius + up.x() * Math.sin(a0) * radius,
                    left.y() * Math.cos(a0) * radius + up.y() * Math.sin(a0) * radius,
                    left.z() * Math.cos(a0) * radius + up.z() * Math.sin(a0) * radius);
            Vec3 e1 = center.add(left.x() * Math.cos(a1) * radius + up.x() * Math.sin(a1) * radius,
                    left.y() * Math.cos(a1) * radius + up.y() * Math.sin(a1) * radius,
                    left.z() * Math.cos(a1) * radius + up.z() * Math.sin(a1) * radius);
            vertex(vc, m, cam, center, r, g, b, centerAlpha);
            vertex(vc, m, cam, e0, r, g, b, 0);
            vertex(vc, m, cam, e1, r, g, b, 0);
            vertex(vc, m, cam, center, r, g, b, centerAlpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(r, g, b, a);
    }

    private static int alphaInt(float alpha) {
        return Mth.clamp((int) (alpha * 255.0F), 0, 255);
    }
}
