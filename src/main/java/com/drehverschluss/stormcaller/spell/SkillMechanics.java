package com.drehverschluss.stormcaller.spell;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.entity.StormCloudEntity;
import com.drehverschluss.stormcaller.fx.ArcStyle;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import com.drehverschluss.stormcaller.shock.ShockHandler;
import com.drehverschluss.stormcaller.shock.StormcallerEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.spell_engine.api.effect.InstantCast;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.delivery.CloudPlacer;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

/**
 * Mechanics of the skill tree nodes that Spell Engine cannot express with data alone. Every hook is chosen by data, so
 * other spells and packs can use them too.
 * <ul>
 *     <li><b>Stash duration</b> (Lasting Charge): Spell Engine has no modifier for the duration of a stash effect. For
 *     spells with a {@code STASH_EFFECT} delivery Stormcaller adds the {@code spawn_duration_add} of the caster's
 *     modifiers to the stashed effect right after the cast. ({@code effect_duration_add} would also lengthen the stun of
 *     Stormcaller's shock, see {@code ShockImpacts}.)</li>
 *     <li><b>{@code stormcaller:charged_ground}</b> (Charged Ground): marker impact. When a falling projectile (METEOR) of a
 *     spell whose impacts contain it lands, the cloud spell {@link #GROUND_FIELD_SPELL} is placed at the landing point,
 *     also on empty ground. The impact itself does nothing.</li>
 *     <li><b>{@code stormcaller:static_rain}</b> (Static Rain): slows the target with {@code stormcaller:static_rain} for
 *     {@link #STATIC_RAIN_TICKS} ticks, stage 2 (-30 %) on normal targets, stage 1 (-15 %) on bosses (same rule as the
 *     shock: {@code #stormcaller:shock_resistant}). Spell Engine's own status effects skip bosses entirely.</li>
 *     <li><b>{@code stormcaller:rolling_thunder}</b> (Rolling Thunder): put before the damage of a Thunderstorm cloud.
 *     Every third hit pulse of a cloud doubles the lightning damage of that pulse, with a heavier bolt.</li>
 *     <li><b>Instant cast</b> (Eye of the Storm): while the effect {@code stormcaller:eye_of_the_storm} is on the caster,
 *     spells in {@code #stormcaller:eye_of_the_storm} are cast instantly (Spell Engine {@link InstantCast}).</li>
 * </ul>
 */
public final class SkillMechanics {
    public static final ResourceLocation CHARGED_GROUND = Stormcaller.id("charged_ground");
    public static final ResourceLocation GROUND_FIELD_SPELL = Stormcaller.id("charged_ground_field");
    public static final ResourceLocation STATIC_RAIN = Stormcaller.id("static_rain");
    public static final ResourceLocation ROLLING_THUNDER = Stormcaller.id("rolling_thunder");
    public static final TagKey<Spell> EYE_OF_THE_STORM = TagKey.create(SpellRegistry.KEY, Stormcaller.id("eye_of_the_storm"));

    public static final int STATIC_RAIN_TICKS = 30;
    /** Every n-th hit pulse of a cloud strikes twice as hard. */
    public static final int ROLLING_THUNDER_EVERY = 3;
    public static final float ROLLING_THUNDER_FACTOR = 2.0F;

    private static final SpellHandlers.ImpactResult NO_EFFECT = new SpellHandlers.ImpactResult(false, false);

    /** Hits of this tick whose next lightning damage is doubled (Rolling Thunder). Server thread only. */
    private record Hit(LivingEntity target, Entity attacker) {}
    private static final List<Hit> DOUBLED = new ArrayList<>();

    private SkillMechanics() {}

    public static void init() {
        SpellHandlers.registerCustomImpact(CHARGED_GROUND, (spell, power, caster, target, context) -> NO_EFFECT);
        SpellHandlers.registerCustomImpact(STATIC_RAIN, (spell, power, caster, target, context) -> staticRain(caster, target));
        SpellHandlers.registerCustomImpact(ROLLING_THUNDER, (spell, power, caster, target, context) ->
                rollingThunder(caster, target, context.position()));
        SpellEvents.SPELL_CAST.register(SkillMechanics::onSpellCast);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, SkillMechanics::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> DOUBLED.clear());
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> DOUBLED.clear());
        InstantCast.register(StormcallerEffects.EYE_OF_THE_STORM, EYE_OF_THE_STORM);
    }

    // ------------------------------------------------------------------------------------------------ stash duration

    /** Server, release of a cast: lengthens the stashed effect by the {@code spawn_duration_add} of the modifiers. */
    private static void onSpellCast(SpellEvents.SpellCastEvent.Args args) {
        if (args.action() != SpellCast.Action.RELEASE || !(args.caster() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        Spell spell = args.spell().value();
        if (spell.deliver == null || spell.deliver.type != Spell.Delivery.Type.STASH_EFFECT || spell.deliver.stash_effect == null) {
            return;
        }
        float extra = 0.0F;
        for (Spell.Modifier modifier : SpellModifiers.of(player, args.spell())) {
            extra += modifier.spawn_duration_add;
        }
        int extraTicks = Math.round(extra * 20.0F);
        ResourceLocation effectId = ResourceLocation.tryParse(spell.deliver.stash_effect.id);
        if (extraTicks <= 0 || effectId == null) {
            return;
        }
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).map(h -> (Holder<MobEffect>) h).orElse(null);
        MobEffectInstance current = effect == null ? null : player.getEffect(effect);
        if (current != null) {
            player.addEffect(new MobEffectInstance(effect, current.getDuration() + extraTicks, current.getAmplifier(),
                    current.isAmbient(), current.isVisible(), current.showIcon()));
        }
    }

    // ------------------------------------------------------------------------------------------------ charged ground

    /** Server, a falling spell projectile (METEOR) lands at {@code landing}. */
    public static void onFallFinished(@Nullable Holder<Spell> spell, Entity projectile, Vec3 landing) {
        if (spell == null || !(projectile.level() instanceof ServerLevel level)
                || !(projectile instanceof net.minecraft.world.entity.projectile.Projectile owned)
                || !(owned.getOwner() instanceof LivingEntity caster)) {
            return;
        }
        boolean marked = SpellModifiers.extendedImpactsOf(caster, spell).impacts().stream().anyMatch(SkillMechanics::isChargedGround);
        if (!marked) {
            return;
        }
        Holder<Spell> field = SpellRegistry.from(level).getHolder(GROUND_FIELD_SPELL).map(h -> (Holder<Spell>) h).orElse(null);
        if (field == null) {
            return;
        }
        SpellExecution.ImpactContext context = new SpellExecution.ImpactContext()
                .power(SpellPower.getSpellPower(field.value().school, caster));
        // the falling projectile stops about 0.3 blocks above its target position
        CloudPlacer.placeCloud(level, caster, null, landing.subtract(0.0, 0.3, 0.0), field, context);
    }

    private static boolean isChargedGround(Spell.Impact impact) {
        return impact != null && impact.action != null && impact.action.type == Spell.Impact.Action.Type.CUSTOM
                && impact.action.custom != null && CHARGED_GROUND.toString().equals(impact.action.custom.handler);
    }

    // ------------------------------------------------------------------------------------------------ static rain

    private static SpellHandlers.ImpactResult staticRain(LivingEntity caster, @Nullable Entity target) {
        if (!(target instanceof LivingEntity living) || living.level().isClientSide() || !living.isAlive()) {
            return NO_EFFECT;
        }
        int stage = ShockHandler.isResistant(living) ? 0 : 1;
        living.addEffect(new MobEffectInstance(StormcallerEffects.STATIC_RAIN, STATIC_RAIN_TICKS, stage, false, false, true), caster);
        return new SpellHandlers.ImpactResult(true, false);
    }

    // ------------------------------------------------------------------------------------------------ rolling thunder

    private static SpellHandlers.ImpactResult rollingThunder(LivingEntity caster, @Nullable Entity target, @Nullable Vec3 ground) {
        if (!(target instanceof LivingEntity living) || ground == null || !(living.level() instanceof ServerLevel level)) {
            return NO_EFFECT;
        }
        List<StormCloudEntity> clouds = level.getEntitiesOfClass(StormCloudEntity.class, new AABB(ground, ground).inflate(0.05));
        if (clouds.isEmpty()) {
            return NO_EFFECT;
        }
        StormCloudEntity cloud = clouds.get(0);
        if (cloud.pulse(level.getGameTime()) % ROLLING_THUNDER_EVERY != 0) {
            return NO_EFFECT;
        }
        DOUBLED.add(new Hit(living, caster));
        // heavier bolt from the cloud, on top of the normal one
        Vec3 from = new Vec3(living.getX(), ground.y + cloud.cloudBase() + 0.2, living.getZ());
        Vec3 to = living.position().add(0.0, living.getBbHeight() * 0.6, 0.0);
        if (from.y > to.y + 0.5) {
            StormcallerFx.arc(level, from, to, ArcStyle.STRIKE, 1.0F);
        }
        StormcallerFx.flash(level, StormcallerFx.center(living), 1.1F);
        return NO_EFFECT;
    }

    /** Doubles the next lightning damage of a marked hit (runs before armor, like the bonus of "Shocked"). */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (DOUBLED.isEmpty() || !event.getSource().is(ShockHandler.LIGHTNING_DAMAGE)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        for (int i = 0; i < DOUBLED.size(); i++) {
            Hit hit = DOUBLED.get(i);
            if (hit.target() == event.getEntity() && hit.attacker() == attacker) {
                DOUBLED.remove(i);
                event.setAmount(event.getAmount() * ROLLING_THUNDER_FACTOR);
                Stormcaller.LOGGER.debug("Stormcaller: rolling thunder doubled the hit on {}", event.getEntity());
                return;
            }
        }
    }
}
