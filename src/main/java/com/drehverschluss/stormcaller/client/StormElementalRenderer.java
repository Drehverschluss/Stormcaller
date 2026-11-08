package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Renderer of the Storm Elemental: the cloud body slightly see-through ({@code entityTranslucent}), hovering
 * {@link #HOVER} blocks above its position, plus a glow mask (lightning core, eyes, veins) at full brightness with
 * {@link RenderType#eyes}, so the core shines at night.
 */
public class StormElementalRenderer extends MobRenderer<StormElementalEntity, StormElementalModel> {
    public static final ResourceLocation TEXTURE = Stormcaller.id("textures/entity/storm_elemental.png");
    public static final ResourceLocation GLOW_TEXTURE = Stormcaller.id("textures/entity/storm_elemental_glow.png");
    /** The body floats this far above the ground (blocks). */
    private static final float HOVER = 0.2F;

    public StormElementalRenderer(EntityRendererProvider.Context context) {
        super(context, new StormElementalModel(context.bakeLayer(StormElementalModel.LAYER)), 0.4F);
        addLayer(new Glow(this));
    }

    @Override
    protected void setupRotations(StormElementalEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick,
                                  float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        poseStack.translate(0.0F, HOVER, 0.0F);
    }

    /** Cloud body with soft, slightly transparent edges. */
    @Nullable
    @Override
    protected RenderType getRenderType(StormElementalEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (!bodyVisible && !translucent) {
            return glowing ? RenderType.outline(TEXTURE) : null;
        }
        return RenderType.entityTranslucent(TEXTURE);
    }

    @Override
    public ResourceLocation getTextureLocation(StormElementalEntity entity) {
        return TEXTURE;
    }

    /** Full bright glow mask, like the eyes of spiders and endermen. */
    private static final class Glow extends RenderLayer<StormElementalEntity, StormElementalModel> {
        private static final RenderType TYPE = RenderType.eyes(GLOW_TEXTURE);

        Glow(RenderLayerParent<StormElementalEntity, StormElementalModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, StormElementalEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            VertexConsumer vc = buffers.getBuffer(TYPE);
            getParentModel().renderToBuffer(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}
