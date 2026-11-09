package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.fx.ArcStyle;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import org.jetbrains.annotations.Nullable;

/**
 * Jump to one more enemy (Forked Spark, skill tree passive 1 side A), as data plus two small hooks:
 * <ul>
 *     <li>Spell tag {@code stormcaller:area/nearest_target}: the {@code area_impact} of the spell hits only the nearest
 *     valid enemy around the impact center instead of all of them, and a thin arc jumps from the center target to it.
 *     Spell Engine has no target limit for area impacts. Used by a passive with {@code target FROM_TRIGGER}: its own
 *     impacts skip the hit target ({@code allow_on_center_target false}), the area impact around it hits the next one.
 *     Hook: {@code SpellImpactsMixin}.</li>
 *     <li>Entity predicate {@code stormcaller:has_neighbor} for {@code target_conditions} of a trigger: true if another
 *     enemy of the caster is within the parameter (blocks, default 6) of the tested target. So the passive only rolls
 *     (and only goes on cooldown) when there is somewhere to jump to.</li>
 * </ul>
 * "Valid enemy": alive, attackable, not the caster, not the center target, and harmful spells are allowed on it
 * (Spell Engine's relations, so pets, team mates and friendly NPCs are skipped).
 */
public final class NearestTarget {
    public static final TagKey<Spell> TAG = TagKey.create(SpellRegistry.KEY, Stormcaller.id("area/nearest_target"));
    public static final ResourceLocation HAS_NEIGHBOR = Stormcaller.id("has_neighbor");
    public static final double DEFAULT_RANGE = 6.0;
    /**
     * Custom impact {@code stormcaller:fx_fork_origin}: a small spark on the target, and it counts as a successful
     * impact. Put it first in such a passive, for the center target. Spell Engine treats a delivery as failed when no
     * impact succeeds on the center target (the area part does not count), and then sets no cooldown for the passive.
     */
    public static final ResourceLocation ORIGIN_IMPACT = Stormcaller.id("fx_fork_origin");
    private static final float ARC_SCALE = 0.8F;
    private static final float ORIGIN_SIZE = 0.5F;

    private NearestTarget() {}

    public static void init() {
        SpellEntityPredicates.register(HAS_NEIGHBOR, args -> args.entity() != null
                && args.other() instanceof LivingEntity caster
                && nearest(args.entity(), caster, args.entity().position(), parseRange(args.param())) != null);
        SpellHandlers.registerCustomImpact(ORIGIN_IMPACT, (spell, power, caster, target, context) -> {
            if (target != null && target.level() instanceof ServerLevel level) {
                StormcallerFx.impact(level, StormcallerFx.center(target), ORIGIN_SIZE, 1);
            }
            return new SpellHandlers.ImpactResult(true, false);
        });
    }

    /**
     * Server, area lookup of a spell in {@link #TAG}: keeps only the nearest valid enemy of {@code found} and draws the
     * jump arc. Returns a mutable list (Spell Engine removes the center target from it afterwards).
     */
    public static List<Entity> filter(Holder<Spell> spell, LivingEntity caster, @Nullable Entity center, Vec3 origin,
                                      List<Entity> found) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : found) {
            if (!valid(entity, caster, center)) {
                continue;
            }
            double distance = entity.distanceToSqr(origin);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        List<Entity> result = new ArrayList<>(1);
        if (best != null) {
            result.add(best);
            if (best.level() instanceof ServerLevel level) {
                Vec3 from = center != null ? StormcallerFx.center(center) : origin;
                StormcallerFx.arc(level, from, StormcallerFx.center(best), ArcStyle.CHAIN, ARC_SCALE);
            }
        }
        return result;
    }

    @Nullable
    private static LivingEntity nearest(Entity center, LivingEntity caster, Vec3 origin, double range) {
        AABB box = new AABB(origin, origin).inflate(range);
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity entity : center.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!valid(entity, caster, center)) {
                continue;
            }
            double distance = entity.distanceToSqr(origin);
            if (distance <= bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean valid(Entity entity, LivingEntity caster, @Nullable Entity center) {
        return entity != center && entity != caster && entity instanceof LivingEntity living && living.isAlive()
                && entity.isAttackable() && !entity.isSpectator()
                && EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL, caster, entity);
    }

    private static double parseRange(@Nullable String param) {
        if (param == null || param.isBlank()) {
            return DEFAULT_RANGE;
        }
        try {
            return Double.parseDouble(param.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_RANGE;
        }
    }
}
