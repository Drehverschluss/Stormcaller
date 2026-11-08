package com.drehverschluss.stormcaller.shock;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Shock impacts: custom spell impacts that carry the base shock chance of a spell.
 * <p>
 * Put one behind the {@code DAMAGE} impact of a spell:
 * <pre>{"action": {"type": "CUSTOM", "custom": {"intent": "HARMFUL", "handler": "stormcaller:shock/15"}}}</pre>
 * The number after {@code shock/} is the base chance in percent (decimals allowed, e.g. {@code shock/12.5}). Each hit
 * rolls once with base chance + shock chance of the caster ({@link ShockHandler}).
 * <p>
 * Why the chance is part of the handler id: Spell Engine reads spells with Gson into fixed classes, so extra fields on
 * a custom impact are dropped, and a custom handler is not told which impact called it. The handler id is the only
 * piece of an impact that reaches us. Spell Engine's own {@code chance} field would roll separately before the handler
 * runs, which gives two rolls instead of one.
 * <p>
 * {@code stormcaller:nova_shock}: always shocks, with the stun duration of Lightning Nova ({@code nova_stun_seconds}).
 */
public final class ShockImpacts {
    public static final String PREFIX = Stormcaller.MODID + ":shock/";
    public static final ResourceLocation NOVA_IMPACT = Stormcaller.id("nova_shock");

    private ShockImpacts() {}

    static void register() {
        // whole percents are always there; other values are registered when the spells are known (server start)
        for (int percent = 0; percent <= 100; percent++) {
            registerChance(Integer.toString(percent));
        }
        SpellHandlers.registerCustomImpact(NOVA_IMPACT, (spell, power, caster, target, context) -> {
            if (!(target instanceof LivingEntity living) || living.level().isClientSide()) {
                return new SpellHandlers.ImpactResult(false, false);
            }
            int ticks = ShockHandler.seconds(StormcallerConfig.get(StormcallerConfig.NOVA_STUN_SECONDS));
            return result(ShockHandler.onShockImpact(living, caster, 1.0, ticks));
        });
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, ShockImpacts::onServerAboutToStart);
    }

    /** Registers the handler {@code stormcaller:shock/<value>} if it parses. */
    private static boolean registerChance(String value) {
        OptionalDouble percent = parsePercent(value);
        ResourceLocation id = ResourceLocation.tryParse(PREFIX + value);
        if (percent.isEmpty() || id == null) {
            return false;
        }
        double base = percent.getAsDouble() / 100.0;
        SpellHandlers.registerCustomImpact(id, (spell, power, caster, target, context) -> {
            if (!(target instanceof LivingEntity living) || living.level().isClientSide()) {
                return new SpellHandlers.ImpactResult(false, false);
            }
            int ticks = ShockHandler.seconds(StormcallerConfig.get(StormcallerConfig.SHOCK_STUN_SECONDS));
            return result(ShockHandler.onShockImpact(living, caster, base, ticks));
        });
        return true;
    }

    private static SpellHandlers.ImpactResult result(ShockHandler.Result result) {
        return new SpellHandlers.ImpactResult(result != ShockHandler.Result.NONE, false);
    }

    /** Registers shock handlers with values that are not whole percents, as used by the loaded spells. */
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Registry<Spell> spells = event.getServer().registryAccess().registry(SpellRegistry.KEY).orElse(null);
        if (spells == null) {
            return;
        }
        TreeSet<String> added = new TreeSet<>();
        for (Spell spell : spells) {
            for (Spell.Impact impact : impactsOf(spell)) {
                String handler = handlerOf(impact);
                if (handler != null && handler.startsWith(PREFIX) && !SpellHandlers.customImpact.containsKey(handler)) {
                    String value = handler.substring(PREFIX.length());
                    if (registerChance(value)) {
                        added.add(handler);
                    } else {
                        Stormcaller.LOGGER.warn("Stormcaller: invalid shock impact '{}', expected {}<percent>", handler, PREFIX);
                    }
                }
            }
        }
        if (!added.isEmpty()) {
            Stormcaller.LOGGER.info("Stormcaller: registered shock impacts {}", added);
        }
    }

    // ------------------------------------------------------------------------------------------------ reading spells

    /**
     * Base shock chance of a spell (0..1), read from its first shock impact; empty if the spell has none. Works on
     * both sides (the client has the spells too), used for the tooltip line.
     */
    public static OptionalDouble baseChance(Spell spell) {
        for (Spell.Impact impact : impactsOf(spell)) {
            String handler = handlerOf(impact);
            if (handler == null) {
                continue;
            }
            if (handler.equals(NOVA_IMPACT.toString())) {
                return OptionalDouble.of(1.0);
            }
            if (handler.startsWith(PREFIX)) {
                OptionalDouble percent = parsePercent(handler.substring(PREFIX.length()));
                if (percent.isPresent()) {
                    return OptionalDouble.of(Math.min(1.0, percent.getAsDouble() / 100.0));
                }
            }
        }
        return OptionalDouble.empty();
    }

    private static List<Spell.Impact> impactsOf(Spell spell) {
        List<Spell.Impact> impacts = new ArrayList<>();
        if (spell.impacts != null) {
            impacts.addAll(spell.impacts);
        }
        if (spell.modifiers != null) {
            for (Spell.Modifier modifier : spell.modifiers) {
                if (modifier != null && modifier.impacts != null) {
                    impacts.addAll(modifier.impacts);
                }
            }
        }
        return impacts;
    }

    @Nullable
    private static String handlerOf(@Nullable Spell.Impact impact) {
        if (impact == null || impact.action == null || impact.action.type != Spell.Impact.Action.Type.CUSTOM
                || impact.action.custom == null) {
            return null;
        }
        return impact.action.custom.handler;
    }

    private static OptionalDouble parsePercent(String value) {
        try {
            double percent = Double.parseDouble(value.trim().toLowerCase(Locale.ROOT));
            if (Double.isNaN(percent) || percent < 0.0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(percent);
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
