package com.drehverschluss.stormcaller.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Vanilla keyframe animations of the Storm Elemental (no GeckoLib). Parts by name, see {@link StormElementalModel}.
 * <ul>
 *     <li>{@link #HOVER}: slow up and down of the body, swaying arms and tail (always on).</li>
 *     <li>{@link #SPIN}: the three cloud puffs circle around the body (always on).</li>
 *     <li>{@link #APPEAR}: grows out of the strike (spawn phase); played backwards it is the vanishing (despawn
 *     phase).</li>
 *     <li>{@link #CHARGE} / {@link #RELEASE}: arms up while casting, a push forward on release.</li>
 * </ul>
 */
public final class StormElementalAnimations {
    public static final long HOVER_MS = 3000L;
    public static final long SPIN_MS = 4000L;

    private static final AnimationChannel.Interpolation SMOOTH = AnimationChannel.Interpolations.CATMULLROM;
    private static final AnimationChannel.Interpolation LINEAR = AnimationChannel.Interpolations.LINEAR;

    public static final AnimationDefinition HOVER = AnimationDefinition.Builder.withLength(3.0F).looping()
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    pos(0.0F, 0.0F, 0.0F, 0.0F), pos(1.5F, 0.0F, 1.6F, 0.0F), pos(3.0F, 0.0F, 0.0F, 0.0F)))
            .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, -6.0F, 0.0F, 4.0F), rot(1.5F, 6.0F, 0.0F, 10.0F), rot(3.0F, -6.0F, 0.0F, 4.0F)))
            .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, 6.0F, 0.0F, -4.0F), rot(1.5F, -6.0F, 0.0F, -10.0F), rot(3.0F, 6.0F, 0.0F, -4.0F)))
            .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, 5.0F, 0.0F, -4.0F), rot(1.5F, -5.0F, 0.0F, 4.0F), rot(3.0F, 5.0F, 0.0F, -4.0F)))
            .addAnimation("tail_lower", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, 8.0F, 0.0F, 6.0F), rot(1.5F, -8.0F, 0.0F, -6.0F), rot(3.0F, 8.0F, 0.0F, 6.0F)))
            .addAnimation("tail_tip", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, -10.0F, 0.0F, 8.0F), rot(1.5F, 10.0F, 0.0F, -8.0F), rot(3.0F, -10.0F, 0.0F, 8.0F)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    pos(0.0F, 0.0F, 0.0F, 0.0F), pos(1.8F, 0.0F, 0.4F, 0.0F), pos(3.0F, 0.0F, 0.0F, 0.0F)))
            .build();

    public static final AnimationDefinition SPIN = AnimationDefinition.Builder.withLength(4.0F).looping()
            .addAnimation("ring", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 180.0F, 0.0F), LINEAR),
                    new Keyframe(4.0F, KeyframeAnimations.degreeVec(0.0F, 360.0F, 0.0F), LINEAR)))
            .addAnimation("puff_1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    pos(0.0F, 0.0F, 0.0F, 0.0F), pos(2.0F, 0.0F, 1.2F, 0.0F), pos(4.0F, 0.0F, 0.0F, 0.0F)))
            .addAnimation("puff_2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    pos(0.0F, 0.0F, 1.0F, 0.0F), pos(2.0F, 0.0F, -0.4F, 0.0F), pos(4.0F, 0.0F, 1.0F, 0.0F)))
            .addAnimation("puff_3", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    pos(0.0F, 0.0F, -0.6F, 0.0F), pos(2.0F, 0.0F, 0.8F, 0.0F), pos(4.0F, 0.0F, -0.6F, 0.0F)))
            .build();

    public static final AnimationDefinition APPEAR = AnimationDefinition.Builder.withLength(1.0F)
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    scale(0.0F, 0.05F), scale(0.6F, 1.12F), scale(1.0F, 1.0F)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    pos(0.0F, 0.0F, -8.0F, 0.0F), pos(0.6F, 0.0F, 1.0F, 0.0F), pos(1.0F, 0.0F, 0.0F, 0.0F)))
            .build();

    /** Arms up while charging; held after 0.4 s. */
    public static final AnimationDefinition CHARGE = AnimationDefinition.Builder.withLength(0.4F)
            .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, 0.0F, 0.0F, 0.0F), rot(0.4F, -110.0F, 0.0F, 15.0F)))
            .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, 0.0F, 0.0F, 0.0F), rot(0.4F, -60.0F, 0.0F, -20.0F)))
            .build();

    /** Push forward on release. */
    public static final AnimationDefinition RELEASE = AnimationDefinition.Builder.withLength(0.75F)
            .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, -110.0F, 0.0F, 15.0F), rot(0.15F, -80.0F, 0.0F, 5.0F), rot(0.75F, 0.0F, 0.0F, 0.0F)))
            .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, -60.0F, 0.0F, -20.0F), rot(0.75F, 0.0F, 0.0F, 0.0F)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    rot(0.0F, 0.0F, 0.0F, 0.0F), rot(0.12F, 10.0F, 0.0F, 0.0F), rot(0.75F, 0.0F, 0.0F, 0.0F)))
            .build();

    private StormElementalAnimations() {}

    private static Keyframe pos(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), SMOOTH);
    }

    private static Keyframe rot(float time, float x, float y, float z) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), SMOOTH);
    }

    private static Keyframe scale(float time, float s) {
        return new Keyframe(time, KeyframeAnimations.scaleVec(s, s, s), SMOOTH);
    }
}
