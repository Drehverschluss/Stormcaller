package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import com.drehverschluss.stormcaller.fx.client.ElementalPose;
import com.drehverschluss.stormcaller.fx.client.PuffGlows;
import com.drehverschluss.stormcaller.fx.client.StormcallerClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Storm Elemental drawn from soft cloud puffs, with the same technique as the Thunderstorm cloud ({@link PuffQuads}):
 * a small dense head, a big chest with broad shoulders, two arms as chains of puffs that get thinner towards the hands
 * and a spinning vortex that narrows down to a tip. Pose and movement come from {@link ElementalPose} (hovering,
 * turning head, leaning, arms reaching forward while casting, trailing arms and vortex).
 * <p>
 * Head and chest are drawn darker and denser than the arms and the vortex, so the figure stays readable from 10 to 20
 * blocks. Puffs light up around the arcs inside the body ({@link PuffGlows}) and around the glowing core. When hurt the
 * puffs flicker red and twitch (the red overlay of vanilla only works for models). Appearing: the puffs gather from a
 * whirl at the ground into the figure within a second. Vanishing and death: the puffs drift apart and fade.
 * <p>
 * Cost: 56 puffs (one quad each) up close; beyond {@link #DETAIL_DISTANCE} blocks or with {@code strength = REDUCED}
 * only the 33 main puffs, a bit bigger. Core, eyes and arcs are drawn by {@code fx.client.ElementalFx}.
 */
public class StormElementalRenderer extends EntityRenderer<StormElementalEntity> {
    private static final double DETAIL_DISTANCE = 24.0;
    private static final float[] HURT = {0.78F, 0.12F, 0.10F};

    private enum Part { TORSO, HEAD, ARM_LEFT, ARM_RIGHT, VORTEX }

    /**
     * One puff of the body.
     *
     * @param local  local position (torso, head) or small offset (arms, vortex), see {@link ElementalPose}
     * @param t      position along an arm or the vortex (0..1)
     * @param size   half size in blocks
     * @param delay  start delay while appearing (0..0.35)
     * @param dir    direction in which the puff drifts away when vanishing
     * @param detail left out at a distance and with REDUCED
     */
    private record Puff(Part part, Vec3 local, double t, float size, float shade, float alpha, float phase, float delay, int variant,
                        Vec3 dir, boolean detail) {}

    private final Map<Integer, List<Puff>> layouts = new HashMap<>();

    public StormElementalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(StormElementalEntity e, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        if (e.isInvisible()) {
            return;
        }
        ElementalPose pose = ElementalPose.of(e, partialTick);
        if (pose.vanish >= 1.0F) {
            return;
        }
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        boolean far = pose.base.distanceToSqr(cam) > DETAIL_DISTANCE * DETAIL_DISTANCE;
        boolean skipDetail = far || StormcallerClientConfig.reduced();
        float sizeScale = skipDetail ? 1.15F : 1.0F;
        float[] color = PuffQuads.baseColor(e.level(), partialTick);
        float tint = pose.hurt * 0.6F;
        float pulse = 0.5F + 0.5F * Mth.sin(pose.age * 0.12F);
        float coreLight = 0.15F + 0.1F * pulse + 0.6F * pose.coreBoost;
        RandomSource jitter = RandomSource.create(e.getId() * 131L + e.tickCount);

        VertexConsumer vc = buffers.getBuffer(PuffQuads.TYPE);
        Quaternionf camera = this.entityRenderDispatcher.cameraOrientation();
        List<Puff> puffs = layoutFor(e.getId());
        for (int i = 0; i < puffs.size(); i++) {
            Puff puff = puffs.get(i);
            if (puff.detail() && skipDetail) {
                continue;
            }
            double drift = 0.025;
            Vec3 local = switch (puff.part()) {
                case TORSO, HEAD -> puff.local();
                case ARM_LEFT -> pose.armLocal(true, puff.t()).add(puff.local());
                case ARM_RIGHT -> pose.armLocal(false, puff.t()).add(puff.local());
                case VORTEX -> pose.vortexLocal(puff.t(), puff.phase()).add(puff.local());
            };
            local = local.add(drift * Math.sin(pose.age * 0.07 + puff.phase()), drift * Math.sin(pose.age * 0.09 + puff.phase() * 1.3),
                    drift * Math.sin(pose.age * 0.06 + puff.phase() * 0.7));
            float size = puff.size() * sizeScale;
            float alpha = puff.alpha();
            // appearing: gather from a whirl at the ground
            if (pose.appear < 1.0F) {
                float p = PuffQuads.smooth((pose.appear * 1.35F - puff.delay()));
                double angle = puff.phase() * 3.0 + pose.age * 0.35;
                Vec3 start = new Vec3(Math.cos(angle) * 0.55, 0.1 + 0.5 * (puff.phase() / 6.28), Math.sin(angle) * 0.55);
                local = start.lerp(local, p);
                size *= 0.4F + 0.6F * p;
                alpha *= Mth.clamp(pose.appear * 2.0F, 0.0F, 1.0F);
            }
            // vanishing: drift apart and fade
            if (pose.vanish > 0.0F) {
                local = local.add(puff.dir().scale(pose.vanish * 1.3)).add(0.0, pose.vanish * 0.5, 0.0);
                alpha *= (float) Math.pow(1.0F - pose.vanish, 1.3);
                size *= 1.0F + 0.4F * pose.vanish;
            }
            double trail = switch (puff.part()) {
                case ARM_LEFT, ARM_RIGHT -> puff.t() * 0.8;
                case VORTEX -> puff.t();
                default -> 0.0;
            };
            Vec3 offset = pose.offset(local, puff.part() == Part.HEAD, trail);
            if (pose.hurt > 0.0F) {
                offset = offset.add((jitter.nextDouble() - 0.5) * 0.1 * pose.hurt, (jitter.nextDouble() - 0.5) * 0.1 * pose.hurt,
                        (jitter.nextDouble() - 0.5) * 0.1 * pose.hurt);
            }
            float glow = PuffGlows.glowAt(e.getId(), pose.base.add(offset), partialTick);
            if (puff.part() == Part.TORSO) {
                double toCore = local.distanceTo(ElementalPose.CORE);
                glow += coreLight * (float) Mth.clamp(1.0 - toCore / 0.4, 0.0, 1.0);
            }
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(camera);
            PuffQuads.quad(vc, poseStack.last(), size, color, puff.shade(), glow, alpha, puff.variant(), HURT, tint);
            poseStack.popPose();
        }
        super.render(e, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private List<Puff> layoutFor(int id) {
        List<Puff> layout = layouts.get(id);
        if (layout == null) {
            if (layouts.size() > 64) {
                layouts.clear();
            }
            layout = build(id);
            layouts.put(id, layout);
        }
        return layout;
    }

    /** The puffs of one elemental: fixed per entity, so every elemental looks a bit different. */
    private static List<Puff> build(int seed) {
        RandomSource r = RandomSource.create(seed * 7919L + 4242L);
        List<Puff> puffs = new ArrayList<>();
        // chest: shoulders plus an ellipsoid of puffs, dark and dense
        for (int side = -1; side <= 1; side += 2) {
            puffs.add(puff(r, Part.TORSO, new Vec3(side * 0.30, 1.30, 0.0), 0.0, 0.22F, -0.05F, 0.96F, false));
        }
        // neck, so the head does not float above the chest
        puffs.add(puff(r, Part.TORSO, new Vec3(0.0, 1.38, 0.0), 0.0, 0.16F, -0.06F, 0.96F, false));
        for (int i = 0; i < 14; i++) {
            Vec3 p = inEllipsoid(r, new Vec3(0.0, 1.12, 0.0), 0.28, 0.22, 0.16);
            puffs.add(puff(r, Part.TORSO, p, 0.0, 0.20F + r.nextFloat() * 0.06F, -0.05F + r.nextFloat() * 0.03F, 0.95F, i >= 8));
        }
        // head: small and dense, darkest
        for (int i = 0; i < 7; i++) {
            Vec3 p = inEllipsoid(r, ElementalPose.HEAD_CENTER, 0.11, 0.10, 0.10);
            puffs.add(puff(r, Part.HEAD, p, 0.0, 0.15F + r.nextFloat() * 0.04F, -0.07F, 0.97F, i >= 5));
        }
        // arms: chains getting thinner towards the hands
        for (Part arm : new Part[]{Part.ARM_LEFT, Part.ARM_RIGHT}) {
            for (int i = 0; i < 7; i++) {
                double t = i / 6.0;
                Vec3 jitter = new Vec3((r.nextDouble() - 0.5) * 0.03, (r.nextDouble() - 0.5) * 0.03, (r.nextDouble() - 0.5) * 0.03);
                puffs.add(puff(r, arm, jitter, t, Mth.lerp((float) t, 0.18F, 0.11F), -0.03F, Mth.lerp((float) t, 0.94F, 0.78F),
                        i % 2 == 1));
            }
        }
        // vortex: spiral down to a tip, lighter and more see-through towards the bottom
        for (int i = 0; i < 18; i++) {
            double t = i / 17.0;
            Vec3 jitter = new Vec3((r.nextDouble() - 0.5) * 0.03, (r.nextDouble() - 0.5) * 0.03, (r.nextDouble() - 0.5) * 0.03);
            puffs.add(puff(r, Part.VORTEX, jitter, t, Mth.lerp((float) t, 0.22F, 0.08F), Mth.lerp((float) t, -0.03F, 0.03F),
                    Mth.lerp((float) t, 0.92F, 0.6F), i % 2 == 1));
        }
        return puffs;
    }

    private static Puff puff(RandomSource r, Part part, Vec3 local, double t, float size, float shade, float alpha, boolean detail) {
        Vec3 dir = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.6 - 0.2, r.nextDouble() - 0.5).normalize();
        return new Puff(part, local, t, size, shade, alpha, r.nextFloat() * 6.28F, r.nextFloat() * 0.35F, r.nextInt(PuffQuads.VARIANTS),
                dir, detail);
    }

    private static Vec3 inEllipsoid(RandomSource r, Vec3 center, double rx, double ry, double rz) {
        double u = r.nextDouble() * 2.0 - 1.0;
        double phi = r.nextDouble() * Math.PI * 2.0;
        double rad = Math.cbrt(0.35 + 0.65 * r.nextDouble());
        double s = Math.sqrt(1.0 - u * u);
        return center.add(Math.cos(phi) * s * rad * rx, u * rad * ry, Math.sin(phi) * s * rad * rz);
    }

    @Override
    public ResourceLocation getTextureLocation(StormElementalEntity entity) {
        return PuffQuads.TEXTURE;
    }
}
