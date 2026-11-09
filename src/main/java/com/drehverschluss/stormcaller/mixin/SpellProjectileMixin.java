package com.drehverschluss.stormcaller.mixin;

import com.drehverschluss.stormcaller.fx.FxSpellHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.entity.SpellProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ricochet of Spell Engine projectiles.
 * <p>
 * After a ricochet, {@code SpellProjectile.tick()} still moves the projectile a full step towards the new target in the
 * same tick, without a hit check. If the new target is closer than that step, the projectile flies through it. For the
 * spells in {@code #stormcaller:fx/ricochet_fix} we skip that move (like Spell Engine does after a block bounce), so
 * the next tick checks the whole way to the new target.
 * <p>
 * The same place knows the old and the new target, so it also sends the arc between them.
 * <p>
 * Second ricochet problem: {@code ricochetFrom} looks for the next target in a box of {@code ricochet_range} around the
 * projectile, before it moves the projectile to the target it just hit. In the tick of the hit the projectile is still up
 * to one step in front of that target, so a target straight behind it has to be up to one step closer than the range
 * (with velocity 2.5 about 2.6 blocks). For the same spells we center the box on the target that was hit.
 * <p>
 * Another hook: right before a falling projectile (METEOR) performs its impacts, for the strike from the sky of
 * Lightning Strike.
 */
@Mixin(SpellProjectile.class)
public abstract class SpellProjectileMixin {
    @Shadow
    private boolean skipTravel;

    @WrapOperation(method = "ricochetFrom",
            at = @At(value = "INVOKE",
                    target = "Lnet/spell_engine/entity/SpellProjectile;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    ordinal = 0))
    private AABB stormcaller$ricochetSearchBox(SpellProjectile self, Operation<AABB> original,
                                              @Local(argsOnly = true) Entity target) {
        AABB box = original.call(self);
        if (!FxSpellHooks.ricochetFix(self.getSpellEntry())) {
            return box;
        }
        // same point Spell Engine moves the projectile to right after this lookup
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        return box.move(center.subtract(self.position()));
    }

    @Inject(method = "ricochetFrom", at = @At("RETURN"))
    private void stormcaller$afterRicochet(Entity target, LivingEntity caster, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        SpellProjectile self = (SpellProjectile) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (FxSpellHooks.ricochetFix(self.getSpellEntry())) {
            this.skipTravel = true;
        }
        FxSpellHooks.onRicochet(self.getSpellEntry(), target, self.getFollowedTarget());
    }

    /**
     * Falling projectiles (METEOR): visuals at the landing point for the spells in {@code #stormcaller:fx/sky_strike}, and
     * the charged ground of the skill tree ({@code spell.SkillMechanics}).
     */
    @Inject(method = "finishFalling", at = @At("HEAD"))
    private void stormcaller$beforeFallImpact(CallbackInfo ci) {
        SpellProjectile self = (SpellProjectile) (Object) this;
        if (!self.level().isClientSide()) {
            FxSpellHooks.onFallFinished(self.getSpellEntry(), self, self.position());
            // skill tree, Charged Ground: field at the landing point (also on empty ground)
            com.drehverschluss.stormcaller.spell.SkillMechanics.onFallFinished(self.getSpellEntry(), self, self.position());
        }
    }
}
