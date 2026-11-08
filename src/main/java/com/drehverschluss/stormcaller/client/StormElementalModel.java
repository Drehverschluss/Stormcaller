package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Model of the Storm Elemental: a hovering storm spirit without legs, about as tall as a player. A cloud body of a few
 * boxes with a glowing lightning core, a cloud head, two cloud arms, a tail of three shrinking lumps and three small
 * cloud puffs that circle around the body.
 * <p>
 * <b>For a later replacement</b> (Blockbench "Modded Entity" / Java export works the same way): keep the part names,
 * the animations in {@link StormElementalAnimations} address them by name. Texture 64 × 64, glow mask with the same
 * layout ({@code storm_elemental_glow.png}). Sizes in pixels (w × h × d) and UV origins:
 * <pre>
 * part        size       UV      parent       pivot (relative to parent)
 * body        -          -       root         0, -16, 0     (root sits on the ground)
 * torso       9 × 8 × 6  0, 14   body
 * core        3 × 5 × 7  32, 0   body         (sticks out 0.5 px at front and back, glows)
 * head        7 × 7 × 7  0, 0    body         0, -6, 0
 * right_arm   3 × 8 × 4  0, 36   body         -5.5, -5, 0
 * left_arm    3 × 8 × 4  0, 36   body         5.5, -5, 0    (mirrored)
 * tail        6 × 5 × 5  32, 14  body         0, 2, 0
 * tail_lower  4 × 4 × 3  0, 28   tail         0, 5, 0
 * tail_tip    2 × 3 × 2  16, 28  tail_lower   0, 4, 0
 * ring        -          -       body         0, -1, 0      (spins)
 * puff_1..3   4 × 3 × 4  16, 36  ring         at 8 px around the body
 * </pre>
 */
public class StormElementalModel extends HierarchicalModel<StormElementalEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Stormcaller.id("storm_elemental"), "main");
    private static final Vector3f CACHE = new Vector3f();

    private final ModelPart root;
    private final ModelPart head;

    public StormElementalModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.head = root.getChild("body").getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 8.0F, 0.0F));
        body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 14).addBox(-4.5F, -6.0F, -3.0F, 9.0F, 8.0F, 6.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, -4.5F, -3.5F, 3.0F, 5.0F, 7.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
                PartPose.offset(0.0F, -6.0F, 0.0F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 36).addBox(-3.0F, -1.0F, -2.0F, 3.0F, 8.0F, 4.0F),
                PartPose.offsetAndRotation(-4.5F, -5.0F, 0.0F, 0.0F, 0.0F, 0.15F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 36).mirror().addBox(0.0F, -1.0F, -2.0F, 3.0F, 8.0F, 4.0F),
                PartPose.offsetAndRotation(4.5F, -5.0F, 0.0F, 0.0F, 0.0F, -0.15F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(32, 14)
                .addBox(-3.0F, 0.0F, -2.5F, 6.0F, 5.0F, 5.0F), PartPose.offset(0.0F, 2.0F, 0.0F));
        PartDefinition tailLower = tail.addOrReplaceChild("tail_lower", CubeListBuilder.create().texOffs(0, 28)
                .addBox(-2.0F, 0.0F, -1.5F, 4.0F, 4.0F, 3.0F), PartPose.offset(0.0F, 5.0F, 0.0F));
        tailLower.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(16, 28)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 4.0F, 0.0F));
        PartDefinition ring = body.addOrReplaceChild("ring", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 0.0F));
        CubeListBuilder puff = CubeListBuilder.create().texOffs(16, 36).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F,
                new CubeDeformation(0.0F));
        ring.addOrReplaceChild("puff_1", puff, PartPose.offset(8.0F, 0.0F, 0.0F));
        ring.addOrReplaceChild("puff_2", puff, PartPose.offset(-4.0F, -2.0F, 7.0F));
        ring.addOrReplaceChild("puff_3", puff, PartPose.offset(-4.0F, 1.5F, -7.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(StormElementalEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;

        // always: hovering and the turning cloud puffs (also without behaviour, e.g. /summon)
        long ms = (long) (ageInTicks * 50.0F);
        KeyframeAnimations.animate(this, StormElementalAnimations.HOVER, ms % StormElementalAnimations.HOVER_MS, 1.0F, CACHE);
        KeyframeAnimations.animate(this, StormElementalAnimations.SPIN, ms % StormElementalAnimations.SPIN_MS, 1.0F, CACHE);
        // leaning into the movement
        float move = Math.min(limbSwingAmount, 1.0F);
        root.getChild("body").xRot += move * 0.25F;

        animate(entity.spawnAnimationState, StormElementalAnimations.APPEAR, ageInTicks, 1.0F);
        // Spell Engine starts the despawn state at the END of the despawn phase: played backwards, the appear animation
        // runs from "full size" down to nothing over the despawn ticks
        animate(entity.despawnAnimationState, StormElementalAnimations.APPEAR, ageInTicks, -1.0F);
        if (entity.spellReleaseAnimationState.isStarted()) {
            float speed = entity.getSpellReleaseAnimationSpeed(StormElementalAnimations.RELEASE.lengthInSeconds() * 20.0F);
            animate(entity.spellReleaseAnimationState, StormElementalAnimations.RELEASE, ageInTicks, speed);
        } else if (entity.spellCastAnimationState.isStarted()) {
            animate(entity.spellCastAnimationState, StormElementalAnimations.CHARGE, ageInTicks, 1.0F);
        }
    }
}
