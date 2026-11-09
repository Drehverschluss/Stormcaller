package com.drehverschluss.stormcaller.mixin;

import com.drehverschluss.stormcaller.entity.StormCloudEntity;
import com.drehverschluss.stormcaller.spell.NearestTarget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.impact.SpellImpacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target lookup of area impacts.
 * <ul>
 *     <li>Thunderstorm cloud: Spell Engine looks up the targets of a cloud in a sphere around its ground point. For a
 *     cloud that floats above the ground that misses targets hovering a bit above the ground (Blaze, Vex). For
 *     {@link StormCloudEntity} the lookup runs with a larger range and is then cut to a column from the ground up to
 *     the cloud base ({@link StormCloudEntity#columnTargets}).</li>
 *     <li>Spells in {@code #stormcaller:area/nearest_target} (Forked Spark): only the nearest valid enemy
 *     ({@link NearestTarget}).</li>
 * </ul>
 * All other area impacts are unchanged.
 */
@Mixin(SpellImpacts.class)
public abstract class SpellImpactsMixin {
    @WrapOperation(
            method = "lookupAndPerformAreaImpact(Lnet/spell_engine/api/spell/Spell$AreaImpact;Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Ljava/util/List;Lnet/spell_engine/internals/SpellExecution$ImpactContext;ZLjava/lang/Float;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/spell_engine/utils/TargetHelper;targetsFromArea(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FLnet/spell_engine/api/spell/Spell$Target$Area;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> stormcaller$areaTargets(Level world, Entity center, Vec3 origin, Vec3 look, float range,
                                                        Spell.Target.Area area, Predicate<Entity> predicate,
                                                        Operation<List<Entity>> original,
                                                        @Local(argsOnly = true) Holder<Spell> spell,
                                                        @Local(argsOnly = true) LivingEntity caster,
                                                        @Local(argsOnly = true, ordinal = 0) Entity exclude) {
        if (center instanceof StormCloudEntity cloud) {
            return cloud.columnTargets(origin, range, r -> original.call(world, center, origin, look, r, area, predicate));
        }
        List<Entity> targets = original.call(world, center, origin, look, range, area, predicate);
        if (spell != null && spell.is(NearestTarget.TAG)) {
            return NearestTarget.filter(spell, caster, exclude, origin, targets);
        }
        return targets;
    }
}
