package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Soft cloud puffs shared by the Thunderstorm cloud ({@link StormCloudRenderer}) and the Storm Elemental
 * ({@link StormElementalRenderer}): camera-facing quads with one of four puff shapes from {@code storm_cloud.png},
 * normal alpha blending ({@code entityTranslucent}, sorted back to front by Minecraft), full brightness, storm grey by
 * day and a dark blue grey by night, lighter at the top and darker at the bottom of every puff. A glow value lights a
 * puff up towards a pale blue white (flashes and arcs nearby), a tint colours it (e.g. red when hurt).
 */
public final class PuffQuads {
    public static final ResourceLocation TEXTURE = Stormcaller.id("textures/entity/storm_cloud.png");
    public static final RenderType TYPE = RenderType.entityTranslucent(TEXTURE);
    /** The texture holds this many puff variants side by side. */
    public static final int VARIANTS = 4;

    // storm grey by day, a darker blue grey by night: just a bit lighter than the night sky and the dark ground, so it
    // reads as a dark cloud and not as a bright patch (tested in phase 4B)
    private static final float[] DAY = {0.22F, 0.24F, 0.29F};
    private static final float[] NIGHT = {0.12F, 0.13F, 0.18F};
    private static final float[] GLOW = {0.78F, 0.80F, 0.95F};
    /** How far a flash nearby lights a puff up (0..1). */
    private static final float GLOW_STRENGTH = 0.5F;
    private static final float TOP_SHADE = 0.09F;
    private static final float BOTTOM_SHADE = -0.07F;

    private PuffQuads() {}

    /** Base colour of the puffs for the current time of day (RGB 0..1). */
    public static float[] baseColor(Level level, float partialTick) {
        float night = nightFactor(level, partialTick);
        return new float[]{Mth.lerp(night, DAY[0], NIGHT[0]), Mth.lerp(night, DAY[1], NIGHT[1]), Mth.lerp(night, DAY[2], NIGHT[2])};
    }

    /** 0 by day, 1 at night (from the sky brightness of the level); 0.5 without a sky. */
    public static float nightFactor(Level level, float partialTick) {
        if (!(level instanceof ClientLevel client) || !client.dimensionType().hasSkyLight()) {
            return 0.5F;
        }
        float sky = client.getSkyDarken(partialTick);
        return Mth.clamp((1.0F - sky) / 0.8F, 0.0F, 1.0F);
    }

    /**
     * One puff, centred at the current origin of {@code pose}, which must already face the camera.
     *
     * @param s       half size in blocks
     * @param shade   brightness offset of this puff (-darker / +lighter)
     * @param glow    0..1, lights the puff up
     * @param alpha   0..1
     * @param variant puff shape 0..3
     * @param tint    colour to mix in (or null)
     * @param tintAmount 0..1
     */
    public static void quad(VertexConsumer vc, PoseStack.Pose pose, float s, float[] color, float shade, float glow, float alpha,
                            int variant, @Nullable float[] tint, float tintAmount) {
        float u0 = variant / (float) VARIANTS;
        float u1 = (variant + 1) / (float) VARIANTS;
        int[] top = rgba(color, shade + TOP_SHADE, glow, alpha, tint, tintAmount);
        int[] bottom = rgba(color, shade + BOTTOM_SHADE, glow, alpha, tint, tintAmount);
        vertex(vc, pose, -s, -s, bottom, u0, 1.0F);
        vertex(vc, pose, s, -s, bottom, u1, 1.0F);
        vertex(vc, pose, s, s, top, u1, 0.0F);
        vertex(vc, pose, -s, s, top, u0, 0.0F);
    }

    private static int[] rgba(float[] color, float shade, float glow, float alpha, @Nullable float[] tint, float tintAmount) {
        int[] c = new int[4];
        for (int i = 0; i < 3; i++) {
            float v = Mth.clamp(color[i] + shade, 0.0F, 1.0F);
            if (tint != null && tintAmount > 0.0F) {
                v = Mth.lerp(tintAmount, v, tint[i]);
            }
            c[i] = (int) (Mth.lerp(Mth.clamp(glow, 0.0F, 1.0F) * GLOW_STRENGTH, v, GLOW[i]) * 255.0F);
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

    public static float smooth(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
