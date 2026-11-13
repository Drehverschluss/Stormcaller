package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Soft cloud puffs shared by the Thunderstorm cloud ({@link StormCloudRenderer}) and the Storm Elemental
 * ({@link StormElementalRenderer}): camera-facing quads with one of four puff shapes, storm grey by day and a dark blue
 * grey by night, lighter at the top and darker at the bottom of every puff. A glow value lights a puff up towards a pale
 * blue white (flashes and arcs nearby), a tint colours it (e.g. red when hurt).
 * <p>
 * <b>Without a shader pack</b> (vanilla, also with Iris installed but shaders off): {@code storm_cloud.png} with normal
 * alpha blending ({@code entityTranslucent}, sorted back to front by Minecraft) and full brightness.
 * <p>
 * <b>With a shader pack</b> (Iris/Oculus, checked by reflection in {@link ShaderCompat}) everything see-through on an
 * entity goes through the pack's translucent entity program, and packs like Complementary treat that like glass: the
 * half transparent edge of every puff showed a bright rim in the colour of the background. So with a shader pack the
 * puffs are drawn as a solid entity ({@code entityCutoutNoCull}, every pixel fully there or not):
 * <ul>
 *     <li>{@code storm_cloud_seam.png}: solid core, the soft outer edge as a fixed fine raster (a pixel is kept where the
 *     soft alpha of the puff is above a random threshold of that pixel). The raster stays still on the puff; a raster that
 *     changes every frame flickers, because Complementary's TAA does not smooth it.</li>
 *     <li>Lit by the pack with the real light at the entity (the full block light of the vanilla path made Complementary
 *     tint the cloud orange like torch light), normals leaning outwards at the corners, so every flat puff is lit like a
 *     ball.</li>
 *     <li>Neutral grey (the pack adds the blue of the sky light), lighter at the top of the cloud, darker at the bottom, a
 *     fixed random offset per puff ({@link #shaderShade}), and calmer own movement ({@link #motion}).</li>
 *     <li>The seam breathes ({@link #breath}): every puff gets a bit denser and thinner again in its own slow rhythm, so
 *     its fuzzy edge grows and melts while the raster itself stays still.</li>
 *     <li>Vertex alpha always 1: packs like Complementary read the vertex alpha of an entity as ambient occlusion, so
 *     a lower alpha darkens the puff (nearly black by day at 0.4). Density, breathing, appearing and fading pick one of
 *     the binary stages of the seam texture instead.</li>
 * </ul>
 * <p>
 * <b>Shadow pass</b> (Iris draws every entity once more from the sun or moon for the shadow map): the puffs face the
 * shadow camera there instead of the player's camera, so the shadow on the ground is a round cloud shadow from every
 * view angle (facing the player's camera gave stripes when looking straight ahead). In the shadow pass every puff is
 * also pushed back along the light ray by twice its size ({@link #SHADOW_PUSH}): the shadow on the ground stays where it
 * is, but no puff cuts through a shadow caster (that drew hard lines across the puffs). The shadow uses the smooth outline of
 * {@code storm_cloud_shader.png}.
 */
public final class PuffQuads {
    public static final ResourceLocation TEXTURE = Stormcaller.id("textures/entity/storm_cloud.png");
    public static final RenderType TYPE = RenderType.entityTranslucent(TEXTURE);
    /**
     * Shader pack path: solid core, soft edge as a fixed fine raster (256 px per puff), as a stack of
     * {@link #SEAM_STAGES} binary stages (every pixel fully there or not) from thin to dense, stage 0 at the top.
     */
    public static final ResourceLocation SEAM_TEXTURE = Stormcaller.id("textures/entity/storm_cloud_seam.png");
    public static final RenderType SEAM_TYPE = RenderType.entityCutoutNoCull(SEAM_TEXTURE);
    /** Shadow pass: the puff with a smooth outline (4x, 128 px per puff). */
    public static final ResourceLocation SHADOW_TEXTURE = Stormcaller.id("textures/entity/storm_cloud_shader.png");
    public static final RenderType SHADOW_TYPE = RenderType.entityCutoutNoCull(SHADOW_TEXTURE);
    /** The texture holds this many puff variants side by side. */
    public static final int VARIANTS = 4;
    /** Cloud shadows with a shader pack (false = puffs are left out of the shadow pass, no cloud shadow at all). */
    public static final boolean CAST_SHADOW = true;
    /**
     * Shadow pass: every puff is pushed back along the light ray by this many times its half size. With 1 the shadow caster
     * of a puff still cut through the camera-facing puffs seen at an angle to the sun (its corners reach up to 1.4 sizes
     * deep), which drew straight shadow edges across the lit puffs by day; 2 keeps them behind (tested in phase S.1). The
     * shadow on the ground does not move.
     */
    private static final float SHADOW_PUSH = 2.0F;

    // storm grey by day, a darker blue grey by night: just a bit lighter than the night sky and the dark ground, so it
    // reads as a dark cloud and not as a bright patch (tested in phase 4B)
    private static final float[] DAY = {0.22F, 0.24F, 0.29F};
    private static final float[] NIGHT = {0.12F, 0.13F, 0.18F};
    private static final float[] GLOW = {0.78F, 0.80F, 0.95F};
    /** How far a flash nearby lights a puff up (0..1). */
    private static final float GLOW_STRENGTH = 0.5F;
    private static final float TOP_SHADE = 0.09F;
    private static final float BOTTOM_SHADE = -0.07F;

    // shader pack: the pack lights the puffs, so the colours are lighter and neutral grey (tested in phase S)
    private static final float[] SHADER_DAY = {0.52F, 0.53F, 0.55F};
    private static final float[] SHADER_NIGHT = {0.44F, 0.45F, 0.48F};
    /** Shader pack: light/dark of the top and bottom of every puff, times the vanilla values. */
    private static final float SHADER_PUFF_SHADE = 2.0F;
    /** Shader pack: height gradient over the cloud (+- half of it) and fixed random offset per puff (+- half). */
    private static final float SHADER_HEIGHT_SHADE = 0.20F;
    private static final float SHADER_RANDOM_SHADE = 0.07F;
    /** Shader pack: the puffs float and turn this much slower and less (a fine raster looks restless otherwise). */
    private static final float SHADER_MOTION = 0.5F;
    /** Shader pack: alpha factor, so the core of every puff stays solid (the raster only shows in the outer seam). */
    private static final float SHADER_DENSITY = 1.3F;
    /** Shader pack: how much denser the seam gets when a puff breathes in (coverage from 1 to 1 + this; about +10 % area). */
    private static final float BREATH_DEPTH = 0.35F;
    /** Shader pack: breathing in this many steps (every step is one stage of the seam texture). */
    private static final int BREATH_STEPS = 4;
    /** Shader pack: breathing speed in radians per tick (about 9 seconds per breath), varied per puff by +-25 %. */
    private static final float BREATH_SPEED = 0.035F;
    /**
     * Coverage of every stage of {@code storm_cloud_seam.png} (density times breath; 1 = the plain seam, below 1 thinner
     * from the outside, above 1 the seam grows outwards). Thin stages for the tip of the vortex, appearing and fading; then
     * one stage per breathing step. The texture is made from these values ({@code Konzept/Probe-Vorlage/ShaderAlpha/seam_stages.py}).
     */
    private static final float[] SEAM_STAGES = seamStages();
    /**
     * Phase S drew the seam with a vertex alpha of about 0.33 to 0.45 (alpha headroom in the texture). Shader packs like
     * Complementary read the vertex alpha of an entity as vanilla ambient occlusion and square it: the cloud got nearly
     * black by day. So the vertex alpha is always 1 now, and density and breathing pick a stage of the seam texture.
     */
    private static final int SHADER_ALPHA = 255;

    /**
     * How the puffs of one entity are drawn in the current pass.
     *
     * @param buffer vertex consumer of the right render type
     * @param facing rotation that turns a puff quad to the viewer (player camera, or the sun in the shadow pass)
     * @param shader a shader pack is active
     * @param shadow this is Iris' shadow pass
     * @param light  packed light of the vertices
     */
    public record Style(VertexConsumer buffer, Quaternionf facing, boolean shader, boolean shadow, int light) {}

    private PuffQuads() {}

    /**
     * Drawing style for one entity in the current pass, or null if its puffs are not drawn in this pass (shadow pass with
     * {@link #CAST_SHADOW} off).
     *
     * @param camera      the player's camera orientation
     * @param packedLight light at the entity (used with a shader pack)
     */
    @Nullable
    public static Style style(MultiBufferSource buffers, PoseStack poseStack, Quaternionf camera, int packedLight) {
        boolean shader = ShaderCompat.shaderPackInUse();
        boolean shadow = shader && ShaderCompat.renderingShadowPass();
        if (shadow && !CAST_SHADOW) {
            return null;
        }
        Quaternionf facing = camera;
        if (shadow) {
            // the shadow pass draws with the shadow camera in the pose: face that camera (the sun or moon)
            facing = poseStack.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize().conjugate();
        }
        RenderType type = !shader ? TYPE : shadow ? SHADOW_TYPE : SEAM_TYPE;
        return new Style(buffers.getBuffer(type), facing, shader, shadow, shader ? packedLight : LightTexture.FULL_BRIGHT);
    }

    /** Base colour of the puffs for the current time of day and drawing style (RGB 0..1). */
    public static float[] baseColor(Level level, float partialTick, Style style) {
        float[] day = style.shader() ? SHADER_DAY : DAY;
        float[] night = style.shader() ? SHADER_NIGHT : NIGHT;
        float f = nightFactor(level, partialTick);
        return new float[]{Mth.lerp(f, day[0], night[0]), Mth.lerp(f, day[1], night[1]), Mth.lerp(f, day[2], night[2])};
    }

    /**
     * Extra brightness of one puff with a shader pack (0 without): lighter at the top of the cloud, darker at the bottom,
     * plus a fixed random offset per puff.
     *
     * @param height01 height of the puff in its cloud or body, 0 = bottom, 1 = top
     * @param seed     fixed number per puff
     */
    public static float shaderShade(Style style, float height01, int seed) {
        if (!style.shader()) {
            return 0.0F;
        }
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        float random = (h & 0xFFFF) / 65535.0F - 0.5F;
        return (Mth.clamp(height01, 0.0F, 1.0F) - 0.5F) * SHADER_HEIGHT_SHADE + random * SHADER_RANDOM_SHADE;
    }

    /** Factor for the own movement of the puffs (floating, turning): calmer with a shader pack. */
    public static float motion(Style style) {
        return style.shader() ? SHADER_MOTION : 1.0F;
    }

    /**
     * Breathing of the seam with a shader pack (1 without): every puff gets a bit denser and thinner again over a few
     * seconds, each in its own rhythm, so its fuzzy edge grows and melts. The breath goes in {@link #BREATH_STEPS} steps
     * (one stage of the seam texture each); the steps lie inside the swing, never at its turning points, so a puff changes
     * its stage only every second or so and never back and forth from frame to frame (that would flicker with TAA).
     *
     * @param age   age in ticks (with partial tick)
     * @param phase fixed random phase of the puff (0..2 pi)
     */
    public static float breath(Style style, float age, float phase) {
        if (!style.shader()) {
            return 1.0F;
        }
        // a slightly different speed per puff, so they do not breathe in step
        float rate = BREATH_SPEED * (0.75F + 0.5F * (phase / Mth.TWO_PI));
        float swing = 0.5F + 0.5F * Mth.sin(age * rate + phase * 3.1F);
        int step = Math.min(BREATH_STEPS - 1, (int) (swing * BREATH_STEPS));
        return breathStep(step);
    }

    private static float breathStep(int step) {
        return 1.0F + BREATH_DEPTH * step / (BREATH_STEPS - 1);
    }

    private static float[] seamStages() {
        float[] thin = {0.45F, 0.6F, 0.78F, 0.9F};
        float[] stages = new float[thin.length + BREATH_STEPS];
        System.arraycopy(thin, 0, stages, 0, thin.length);
        for (int i = 0; i < BREATH_STEPS; i++) {
            stages[thin.length + i] = breathStep(i);
        }
        return stages;
    }

    /** Densest stage of the seam texture that is not above this coverage, or -1 if the puff is too thin to show. */
    private static int seamStage(float coverage) {
        for (int i = SEAM_STAGES.length - 1; i >= 0; i--) {
            if (SEAM_STAGES[i] <= coverage + 0.001F) {
                return i;
            }
        }
        return -1;
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
     * One puff, centred at the current origin of {@code pose}, which must already be turned by {@link Style#facing()}.
     *
     * @param s       half size in blocks
     * @param shade   brightness offset of this puff (-darker / +lighter)
     * @param glow    0..1, lights the puff up
     * @param alpha   0..1
     * @param variant puff shape 0..3
     * @param tint    colour to mix in (or null)
     * @param tintAmount 0..1
     * @param breath  breathing of the seam, see {@link #breath}
     */
    public static void quad(Style style, PoseStack.Pose pose, float s, float[] color, float shade, float glow, float alpha,
                            int variant, @Nullable float[] tint, float tintAmount, float breath) {
        float u0 = variant / (float) VARIANTS;
        float u1 = (variant + 1) / (float) VARIANTS;
        float v0 = 0.0F;
        float v1 = 1.0F;
        float topShade = TOP_SHADE;
        float bottomShade = BOTTOM_SHADE;
        boolean seam = false;
        if (style.shader()) {
            topShade *= SHADER_PUFF_SHADE;
            bottomShade *= SHADER_PUFF_SHADE;
            if (!style.shadow()) {
                int stage = seamStage(Math.min(1.0F, alpha * SHADER_DENSITY) * breath);
                if (stage < 0) {
                    return;
                }
                v0 = stage / (float) SEAM_STAGES.length;
                v1 = (stage + 1) / (float) SEAM_STAGES.length;
                seam = true;
            }
        }
        int[] top = rgba(color, shade + topShade, glow, alpha, tint, tintAmount);
        int[] bottom = rgba(color, shade + bottomShade, glow, alpha, tint, tintAmount);
        if (seam) {
            // the stage holds the coverage; a vertex alpha below 1 would darken the puff (ambient occlusion of the pack)
            top[3] = SHADER_ALPHA;
            bottom[3] = SHADER_ALPHA;
        }
        // shadow pass: push the caster back along the light ray (the shadow on the ground does not move)
        float z = style.shadow() ? -s * SHADOW_PUSH : 0.0F;
        vertex(style, pose, -s, -s, z, bottom, u0, v1);
        vertex(style, pose, s, -s, z, bottom, u1, v1);
        vertex(style, pose, s, s, z, top, u1, v0);
        vertex(style, pose, -s, s, z, top, u0, v0);
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

    private static void vertex(Style style, PoseStack.Pose pose, float x, float y, float z, int[] c, float u, float v) {
        VertexConsumer vertex = style.buffer().addVertex(pose, x, y, z)
                .setColor(c[0], c[1], c[2], c[3])
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(style.light());
        if (style.shader()) {
            // corners lean outwards: the pack lights the flat puff like a ball
            float nx = Math.signum(x);
            float ny = Math.signum(y);
            float len = (float) Math.sqrt(nx * nx + ny * ny + 1.0F);
            vertex.setNormal(pose, nx / len, ny / len, 1.0F / len);
        } else {
            vertex.setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    public static float smooth(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
