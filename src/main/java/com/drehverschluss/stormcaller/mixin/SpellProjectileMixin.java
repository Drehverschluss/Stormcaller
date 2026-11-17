package com.drehverschluss.stormcaller.mixin;

import com.drehverschluss.stormcaller.fx.FxSpellHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.drehverschluss.stormcaller.spell.StoredCharge;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
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
 * Third ricochet problem: the hit check of each tick ({@code ProjectileUtil.getHitResultOnMoveVector}) only returns the
 * nearest entity on the move, and that may be one the projectile has already hit. Spell Engine then ignores the hit
 * ({@code onHitEntity} checks {@code impactHistory}) but still moves the projectile the full step. If an earlier target
 * lies on the way to the new one, the projectile passes the new target (velocity 3.0 against 3 close targets: 2 hits
 * instead of 3). For the same spells the hit check skips the entities in {@code impactHistory}.
 * <p>
 * Another hook: right before a falling projectile (METEOR) performs its impacts, for the strike from the sky of
 * Lightning Strike.
 * <p>
 * Stored Charge (Amber Lightning Staff): while a discharging Lightning Bolt performs the impacts of a hit, its shock
 * impacts use the Stored Charge chance ({@link StoredCharge}).
 */
@Mixin(SpellProjectile.class)
public abstract class SpellProjectileMixin {
    // Forge 1.20.1: members that only exist in Spell Engine are not remapped (no SRG name)
    @Shadow(remap = false)
    private boolean skipTravel;
    @Shadow(remap = false)
    protected Set<Integer> impactHistory;

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult stormcaller$skipHitTargets(Entity projectile, Predicate<Entity> filter, Operation<HitResult> original) {
        SpellProjectile self = (SpellProjectile) (Object) this;
        if (!self.level().isClientSide() && impactHistory != null && !impactHistory.isEmpty()
                && FxSpellHooks.ricochetFix(self.getSpellEntry())) {
            Set<Integer> hit = impactHistory;
            filter = filter.and(entity -> !hit.contains(entity.getId()));
        }
        return original.call(projectile, filter);
    }

    @WrapOperation(method = "ricochetFrom", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/spell_engine/entity/SpellProjectile;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    ordinal = 0, remap = true))
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

    @WrapOperation(method = "onHitEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/spell_engine/internals/impact/SpellImpacts;projectileImpact(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/spell_engine/internals/SpellExecution$ImpactContext;)Z",
                    remap = false))
    private boolean stormcaller$projectileImpact(LivingEntity caster, Entity projectile, Entity target, Holder<Spell> spell,
                                                 SpellExecution.ImpactContext context, Operation<Boolean> original) {
        boolean discharge = StoredCharge.isDischarge(projectile);
        StoredCharge.beginImpacts(discharge);
        try {
            return original.call(caster, projectile, target, spell, context);
        } finally {
            StoredCharge.endImpacts(discharge);
        }
    }

    @Inject(method = "ricochetFrom", at = @At("RETURN"), remap = false)
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
    @Inject(method = "finishFalling", at = @At("HEAD"), remap = false)
    private void stormcaller$beforeFallImpact(CallbackInfo ci) {
        SpellProjectile self = (SpellProjectile) (Object) this;
        if (!self.level().isClientSide()) {
            FxSpellHooks.onFallFinished(self.getSpellEntry(), self, self.position());
            // skill tree, Charged Ground: field at the landing point (also on empty ground)
            com.drehverschluss.stormcaller.spell.SkillMechanics.onFallFinished(self.getSpellEntry(), self, self.position());
        }
    }
}
