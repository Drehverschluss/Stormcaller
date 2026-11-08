package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.entity.StormCloudEntity;
import com.drehverschluss.stormcaller.fx.client.StormCloudFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The dark thunder cloud of Thunderstorm: a few dozen soft cloud puffs (camera-facing quads with a puff texture) in a
 * flat dome above the ground point.
 * <p>
 * Why an own renderer: Spell Engine particles are lit by the block light (a dark cloud would vanish at night) and
 * need hundreds of particles for a radius of 4; a {@code model_fx} model only scales with radius growth, not with
 * {@code radius_add} modifiers, and is drawn additive or as a solid block. Here the cloud uses normal alpha blending
 * ({@code entityTranslucent}, sorted back to front by Minecraft), full brightness and a dark storm grey (darker blue
 * grey at night), so it reads as a dark cloud by day and night. Radius and height are read from the cloud
 * ({@link StormCloudEntity#radius()}, {@link StormCloudEntity#cloudBase()}), so it grows with modifiers and sits lower
 * under a ceiling. Puffs near a flash inside the cloud light up ({@link StormCloudFx#glowAt}).
 */
public class StormCloudRenderer extends EntityRenderer<StormCloudEntity> {
    public static final ResourceLocation TEXTURE = Stormcaller.id("textures/entity/storm_cloud.png");
    private static final RenderType TYPE = RenderType.entityTranslucent(TEXTURE);
    /** The texture holds this many puff variants side by side. */
    private static final int VARIANTS = 4;
    private static final int APPEAR_TICKS = 14;
    private static final int FADE_TICKS = 10;
    /** Slow turn of the whole cloud, radians per tick. */
    private static final float SPIN = 0.0025F;

    // storm grey by day, a darker blue grey by night: just a bit lighter than the night sky and the dark ground, so it
    // reads as a dark cloud and not as a bright patch (tested in phase 4B)
    private static final float[] DAY = {0.22F, 0.24F, 0.29F};
    private static final float[] NIGHT = {0.12F, 0.13F, 0.18F};
    private static final float[] GLOW = {0.78F, 0.80F, 0.95F};
    /** How far a flash inside the cloud lights the puffs up (0..1). */
    private static final float GLOW_STRENGTH = 0.5F;

    /**
     * One puff, in polar coordinates around the ground point.
     *
     * @param y     height above the cloud base
     * @param size  half size of the quad in blocks
     * @param shade brightness offset (-darker / +lighter)
     */
    private record Puff(double angle, double distance, double y, float size, float shade, float alpha, float phase, int variant) {}

    private record Layout(float radius, List<Puff> puffs) {}

    private final Map<Integer, Layout> layouts = new HashMap<>();

    public StormCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(StormCloudEntity cloud, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        float radius = cloud.radius();
        if (radius <= 0.1F) {
            return;
        }
        Layout layout = layoutFor(cloud, radius);
        double base = cloud.cloudBase();
        float age = cloud.tickCount + partialTick;
        float appear = smooth(Mth.clamp(age / APPEAR_TICKS, 0.0F, 1.0F));
        float fade = 1.0F;
        if (cloud.clientDespawnStart >= 0) {
            fade = Mth.clamp(1.0F - (cloud.tickCount - cloud.clientDespawnStart + partialTick) / FADE_TICKS, 0.0F, 1.0F);
        }
        if (fade <= 0.0F) {
            return;
        }
        float night = nightFactor(cloud, partialTick);
        float[] color = {Mth.lerp(night, DAY[0], NIGHT[0]), Mth.lerp(night, DAY[1], NIGHT[1]), Mth.lerp(night, DAY[2], NIGHT[2])};

        VertexConsumer vc = buffers.getBuffer(TYPE);
        Quaternionf camera = this.entityRenderDispatcher.cameraOrientation();
        float spin = age * SPIN;
        Vec3 origin = cloud.position();
        for (Puff puff : layout.puffs()) {
            double angle = puff.angle() + spin;
            double x = Math.cos(angle) * puff.distance();
            double z = Math.sin(angle) * puff.distance();
            double y = base + puff.y() + Math.sin(age * 0.05F + puff.phase()) * 0.07;
            float size = puff.size() * (0.35F + 0.65F * appear);
            float glow = StormCloudFx.glowAt(cloud, origin.add(x, y, z), partialTick);
            float alpha = puff.alpha() * appear * fade;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(camera);
            quad(vc, poseStack.last(), size, color, puff.shade(), glow, alpha, puff.variant());
            poseStack.popPose();
        }
        super.render(cloud, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float s, float[] color, float shade, float glow, float alpha,
                             int variant) {
        float u0 = variant / (float) VARIANTS;
        float u1 = (variant + 1) / (float) VARIANTS;
        // lighter at the top of each puff, darker at the bottom: the cloud gets a dark underside
        int[] top = rgba(color, shade + 0.09F, glow, alpha);
        int[] bottom = rgba(color, shade - 0.07F, glow, alpha);
        vertex(vc, pose, -s, -s, bottom, u0, 1.0F);
        vertex(vc, pose, s, -s, bottom, u1, 1.0F);
        vertex(vc, pose, s, s, top, u1, 0.0F);
        vertex(vc, pose, -s, s, top, u0, 0.0F);
    }

    private static int[] rgba(float[] color, float shade, float glow, float alpha) {
        int[] c = new int[4];
        for (int i = 0; i < 3; i++) {
            float v = Mth.clamp(color[i] + shade, 0.0F, 1.0F);
            c[i] = (int) (Mth.lerp(glow * GLOW_STRENGTH, v, GLOW[i]) * 255.0F);
        }
        c[3] = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return c;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, int[] c, float u, float v) {
        vc.addVertex(pose, x, y, 0.0F)
                .setColor(c[0], c[1], c[2], c[3])
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** 0 by day, 1 at night (from the sky brightness of the level). */
    private static float nightFactor(StormCloudEntity cloud, float partialTick) {
        if (!(cloud.level() instanceof ClientLevel level) || !level.dimensionType().hasSkyLight()) {
            return 0.5F;
        }
        float sky = level.getSkyDarken(partialTick);
        return Mth.clamp((1.0F - sky) / 0.8F, 0.0F, 1.0F);
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    /** Puff layout of a cloud: fixed per entity, rebuilt when the radius changes. */
    private Layout layoutFor(StormCloudEntity cloud, float radius) {
        Layout layout = layouts.get(cloud.getId());
        if (layout != null && Math.abs(layout.radius() - radius) < 0.05F) {
            return layout;
        }
        if (layouts.size() > 64) {
            layouts.clear();
        }
        layout = new Layout(radius, build(cloud.getId(), radius));
        layouts.put(cloud.getId(), layout);
        return layout;
    }

    private static List<Puff> build(int seed, float radius) {
        RandomSource random = RandomSource.create(seed * 7919L + 17);
        List<Puff> puffs = new ArrayList<>();
        float scale = Mth.clamp(radius / 4.0F, 0.8F, 1.3F);
        int n = Mth.clamp(Math.round(1.4F * radius * radius), 10, 140);
        // main layer: even spread (sunflower spiral) with a small dome towards the middle
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            double distance = radius * 0.9 * Math.sqrt(t);
            double angle = i * 2.39996 + random.nextDouble() * 0.4;
            double y = 0.1 + random.nextDouble() * 0.35 + (1.0 - distance / radius) * 0.35;
            puffs.add(new Puff(angle, distance, y, (1.15F + random.nextFloat() * 0.5F) * scale, random.nextFloat() * 0.06F - 0.03F,
                    0.9F, random.nextFloat() * 6.28F, random.nextInt(VARIANTS)));
        }
        // top layer: fewer, smaller, a bit lighter
        for (int i = 0; i < n / 2; i++) {
            double distance = radius * 0.7 * Math.sqrt(random.nextDouble());
            double y = 0.6 + random.nextDouble() * 0.45 + (1.0 - distance / radius) * 0.3;
            puffs.add(new Puff(random.nextDouble() * Math.PI * 2.0, distance, y, (0.9F + random.nextFloat() * 0.4F) * scale,
                    0.05F, 0.85F, random.nextFloat() * 6.28F, random.nextInt(VARIANTS)));
        }
        // ragged dark underside
        for (int i = 0; i < Math.max(4, n / 3); i++) {
            double distance = radius * 0.95 * Math.sqrt(random.nextDouble());
            puffs.add(new Puff(random.nextDouble() * Math.PI * 2.0, distance, -0.15 - random.nextDouble() * 0.15,
                    (0.85F + random.nextFloat() * 0.35F) * scale, -0.07F, 0.8F, random.nextFloat() * 6.28F, random.nextInt(VARIANTS)));
        }
        return puffs;
    }

    @Override
    public ResourceLocation getTextureLocation(StormCloudEntity entity) {
        return TEXTURE;
    }
}
