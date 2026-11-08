package com.drehverschluss.stormcaller.mixin;

import com.drehverschluss.stormcaller.fx.FxSpellHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * Second hook: right before a falling projectile (METEOR) performs its impacts, for the strike from the sky of
 * Lightning Strike.
 */
@Mixin(SpellProjectile.class)
public abstract class SpellProjectileMixin {
    @Shadow
    private boolean skipTravel;

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

    /** Falling projectiles (METEOR): visuals at the landing point, for the spells in {@code #stormcaller:fx/sky_strike}. */
    @Inject(method = "finishFalling", at = @At("HEAD"))
    private void stormcaller$beforeFallImpact(CallbackInfo ci) {
        SpellProjectile self = (SpellProjectile) (Object) this;
        if (!self.level().isClientSide()) {
            FxSpellHooks.onFallFinished(self.getSpellEntry(), self, self.position());
        }
    }
}
