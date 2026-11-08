package com.drehverschluss.stormcaller.fx;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import org.jetbrains.annotations.Nullable;

/**
 * Connects the lightning visuals to spells. Everything is chosen per spell through data:
 * <ul>
 *     <li>Spell tag {@code stormcaller:fx/ricochet_fix}: projectile does not skip a target right after a ricochet.</li>
 *     <li>Spell tag {@code stormcaller:fx/chain_arc}: arc from the old to the new target on every ricochet;
 *     additionally in {@code stormcaller:fx/chain_arc_strong}: style {@link ArcStyle#STRONG} instead of
 *     {@link ArcStyle#CHAIN}.</li>
 *     <li>Spell tag {@code stormcaller:fx/nova_ring}: flash and lightning ring (radius = spell range) around the caster
 *     on release.</li>
 *     <li>Spell tag {@code stormcaller:fx/shield_burst}: small flash and short ring around the caster on release
 *     (self buffs like Static Shield).</li>
 *     <li>Spell tag {@code stormcaller:fx/sky_strike}: when a falling projectile (METEOR) lands, a lightning strike from
 *     the sky plus a ground impact with arcs at the landing point (Lightning Strike).</li>
 *     <li>Custom impacts (put them first in {@code impacts}, they never change the game, only draw):
 *     {@code stormcaller:fx_impact}, {@code stormcaller:fx_impact_small} (flash and arcs at the hit point) and
 *     {@code stormcaller:fx_arc_from_caster} (arc in style {@link ArcStyle#STRONG} from the caster to the target),
 *     {@code stormcaller:fx_arc_from_center} (thinner {@link ArcStyle#STRONG} arc from the impact center, e.g. the
 *     center of an {@code area_impact}, to the target).</li>
 * </ul>
 */
public final class FxSpellHooks {
    public static final TagKey<Spell> RICOCHET_FIX = TagKey.create(SpellRegistry.KEY, Stormcaller.id("fx/ricochet_fix"));
    public static final TagKey<Spell> CHAIN_ARC = TagKey.create(SpellRegistry.KEY, Stormcaller.id("fx/chain_arc"));
    public static final TagKey<Spell> CHAIN_ARC_STRONG = TagKey.create(SpellRegistry.KEY, Stormcaller.id("fx/chain_arc_strong"));
    public static final TagKey<Spell> NOVA_RING = TagKey.create(SpellRegistry.KEY, Stormcaller.id("fx/nova_ring"));
    public static final TagKey<Spell> SHIELD_BURST = TagKey.create(SpellRegistry.KEY, Stormcaller.id("fx/shield_burst"));
    public static final TagKey<Spell> SKY_STRIKE = TagKey.create(SpellRegistry.KEY, Stormcaller.id("fx/sky_strike"));

    public static final ResourceLocation IMPACT = Stormcaller.id("fx_impact");
    public static final ResourceLocation IMPACT_SMALL = Stormcaller.id("fx_impact_small");
    public static final ResourceLocation ARC_FROM_CASTER = Stormcaller.id("fx_arc_from_caster");
    public static final ResourceLocation ARC_FROM_CENTER = Stormcaller.id("fx_arc_from_center");

    /** Ring radius of {@code fx/shield_burst}. */
    private static final float SHIELD_RING_RADIUS = 1.6F;
    /** Start height of the {@code fx/sky_strike} bolt above the landing point. */
    private static final float SKY_STRIKE_HEIGHT = 16.0F;

    private static final SpellHandlers.ImpactResult NO_EFFECT = new SpellHandlers.ImpactResult(false, false);

    private FxSpellHooks() {}

    public static void register() {
        SpellHandlers.registerCustomImpact(IMPACT, (spell, power, caster, target, context) ->
                impact(target, context.position(), 1.0F, 3));
        SpellHandlers.registerCustomImpact(IMPACT_SMALL, (spell, power, caster, target, context) ->
                impact(target, context.position(), 0.6F, 2));
        SpellHandlers.registerCustomImpact(ARC_FROM_CASTER, (spell, power, caster, target, context) -> {
            if (target != null && caster.level() instanceof ServerLevel level) {
                Vec3 from = caster.position().add(0.0, caster.getBbHeight() * 0.6, 0.0);
                StormcallerFx.arc(level, from, StormcallerFx.center(target), ArcStyle.STRONG);
            }
            return NO_EFFECT;
        });
        SpellHandlers.registerCustomImpact(ARC_FROM_CENTER, (spell, power, caster, target, context) -> {
            if (target != null && context.position() != null && target.level() instanceof ServerLevel level) {
                StormcallerFx.arc(level, context.position(), StormcallerFx.center(target), ArcStyle.STRONG, 0.8F);
            }
            return NO_EFFECT;
        });
        SpellEvents.SPELL_CAST.register(FxSpellHooks::onSpellCast);
    }

    private static SpellHandlers.ImpactResult impact(@Nullable Entity target, @Nullable Vec3 position, float size, int arcs) {
        if (target != null && target.level() instanceof ServerLevel level) {
            Vec3 center = StormcallerFx.center(target);
            // the hit point lies on the surface; pull it a bit into the target so the arcs start inside the body
            Vec3 pos = position == null ? center : position.lerp(center, 0.5);
            StormcallerFx.impact(level, pos, size, arcs);
        }
        return NO_EFFECT;
    }

    /** Server, release of a spell cast by a player. */
    private static void onSpellCast(SpellEvents.SpellCastEvent.Args args) {
        if (args.action() != SpellCast.Action.RELEASE || !(args.caster().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity caster = args.caster();
        if (args.spell().is(NOVA_RING)) {
            StormcallerFx.flash(level, caster.position().add(0.0, caster.getBbHeight() * 0.55, 0.0), 1.3F);
            StormcallerFx.ring(level, caster.position().add(0.0, 0.15, 0.0), args.spell().value().range);
        }
        if (args.spell().is(SHIELD_BURST)) {
            StormcallerFx.flash(level, caster.position().add(0.0, caster.getBbHeight() * 0.55, 0.0), 0.9F);
            StormcallerFx.ring(level, caster.position().add(0.0, 0.15, 0.0), SHIELD_RING_RADIUS);
        }
    }

    // ------------------------------------------------------------------------------------------------ meteor

    /** Server, right before a falling spell projectile (METEOR) performs its impacts at {@code landing}. */
    public static void onFallFinished(@Nullable Holder<Spell> spell, Entity projectile, Vec3 landing) {
        if (spell == null || !spell.is(SKY_STRIKE) || !(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        // the falling projectile stops about 0.3 blocks above its target position; put the strike on the ground
        Vec3 ground = landing.subtract(0.0, 0.3, 0.0);
        StormcallerFx.strike(level, ground, SKY_STRIKE_HEIGHT);
        StormcallerFx.impact(level, landing, 1.6F, 5);
    }

    // ------------------------------------------------------------------------------------------------ ricochet

    /** Should the ricochet fix apply to this spell? */
    public static boolean ricochetFix(@Nullable Holder<Spell> spell) {
        return spell != null && (spell.is(RICOCHET_FIX) || StormcallerConfig.get(StormcallerConfig.RICOCHET_FIX_ALL_SPELLS));
    }

    /** Server, right after a successful ricochet from {@code oldTarget} to {@code newTarget}. */
    public static void onRicochet(@Nullable Holder<Spell> spell, Entity oldTarget, @Nullable Entity newTarget) {
        if (spell == null || newTarget == null || !spell.is(CHAIN_ARC) || !(oldTarget.level() instanceof ServerLevel level)) {
            return;
        }
        ArcStyle style = spell.is(CHAIN_ARC_STRONG) ? ArcStyle.STRONG : ArcStyle.CHAIN;
        StormcallerFx.arc(level, StormcallerFx.center(oldTarget), StormcallerFx.center(newTarget), style);
    }
}
