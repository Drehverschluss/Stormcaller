package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import org.jetbrains.annotations.Nullable;

/**
 * Target limit per hit pulse of a Thunderstorm cloud, as a marker impact in the spell file:
 * <pre>{"action": {"type": "CUSTOM", "custom": {"intent": "HARMFUL", "handler": "stormcaller:max_targets/3"}}}</pre>
 * The number after {@code max_targets/} is the most enemies one hit pulse strikes. If there are more valid enemies
 * below the cloud, that many are picked at random (uniformly, each at most once per pulse); with that many or fewer
 * all are hit. Without the marker (or with 0) there is no limit, like Spell Engine.
 * <p>
 * The marker has no handler: Spell Engine skips custom impacts without a handler, so it does nothing on its own. The
 * cloud reads it from the impacts of the spell including the caster's modifier spells (first marker wins).
 * <p>
 * Why not Spell Engine's {@code impact_cap} of the cloud: that counts hit pulses and ends the cloud early after that
 * many pulses with at least one target; it does not limit the targets of one pulse.
 * <p>
 * "Valid enemy" uses the same rules as Spell Engine's harmful impacts (alive, attackable, harmful spells allowed by
 * {@link EntityRelations}), so allies, pets and the caster never take one of the places. Other entities of the lookup
 * are passed through unchanged (no harmful impact applies to them anyway).
 */
public final class MaxTargets {
    public static final String PREFIX = Stormcaller.MODID + ":max_targets/";

    private MaxTargets() {}

    /** Limit from the spell's own impacts (tooltip); 0 = no limit. */
    public static int of(Spell spell) {
        return spell == null ? 0 : first(spell.impacts);
    }

    /** Limit from the impacts of the spell including the caster's modifier spells; 0 = no limit. */
    public static int of(Holder<Spell> spell, @Nullable LivingEntity caster) {
        if (spell == null) {
            return 0;
        }
        if (caster == null) {
            return of(spell.value());
        }
        return first(SpellModifiers.extendedImpactsOf(caster, spell).impacts());
    }

    /**
     * Keeps at most {@code limit} valid enemies of {@code targets}, chosen at random. Returns a new mutable list
     * (Spell Engine removes entries from it afterwards).
     */
    public static List<Entity> limit(List<Entity> targets, @Nullable LivingEntity caster, int limit, RandomSource random) {
        List<Entity> result = new ArrayList<>(targets.size());
        if (limit <= 0 || caster == null) {
            result.addAll(targets);
            return result;
        }
        List<Entity> valid = new ArrayList<>(targets.size());
        for (Entity entity : targets) {
            if (valid(entity, caster)) {
                valid.add(entity);
            } else {
                result.add(entity);
            }
        }
        // partial Fisher-Yates: the first `limit` entries are a uniform random pick
        int picks = Math.min(limit, valid.size());
        for (int i = 0; i < picks; i++) {
            int j = i + random.nextInt(valid.size() - i);
            Entity swap = valid.get(i);
            valid.set(i, valid.get(j));
            valid.set(j, swap);
            result.add(valid.get(i));
        }
        return result;
    }

    private static boolean valid(Entity entity, LivingEntity caster) {
        return entity != caster && entity instanceof LivingEntity living && living.isAlive() && entity.isAttackable()
                && !entity.isSpectator()
                && EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL, caster, entity);
    }

    private static int first(@Nullable List<Spell.Impact> impacts) {
        if (impacts == null) {
            return 0;
        }
        for (Spell.Impact impact : impacts) {
            if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM
                    || impact.action.custom == null || impact.action.custom.handler == null) {
                continue;
            }
            String handler = impact.action.custom.handler;
            if (handler.startsWith(PREFIX)) {
                try {
                    return Math.max(0, Integer.parseInt(handler.substring(PREFIX.length()).trim()));
                } catch (NumberFormatException e) {
                    Stormcaller.LOGGER.warn("Stormcaller: invalid target limit '{}', expected {}<number>", handler, PREFIX);
                    return 0;
                }
            }
        }
        return 0;
    }
}
