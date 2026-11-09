package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.shock.ShockImpacts;
import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.tooltip.TooltipTokens;

/**
 * Adds the base shock chance to the description of every spell with a shock impact ({@link ShockImpacts}): "Shock
 * chance: 15%" or "Always shocks.". The value is read from the spell itself, so it follows datapack changes.
 * <p>
 * Uses Spell Engine's custom tooltip tokens (one per spell id). Spells that already have a custom token from another
 * mod are left alone.
 * <p>
 * Stormcaller spells also get tokens that Spell Engine does not have: {@code {ricochet_range_add}} (sum of
 * {@code projectile_perks.ricochet_range} of the modifiers of a modifier spell, e.g. Long Arc).
 */
public final class ShockTooltips {
    private static final String CHANCE_KEY = "spell.tooltip.stormcaller.shock_chance";
    private static final String ALWAYS_KEY = "spell.tooltip.stormcaller.shock_always";
    private static final String RICOCHET_RANGE_ADD = "{ricochet_range_add}";
    private static final Set<ResourceLocation> OWN = new HashSet<>();

    private ShockTooltips() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> scan());
    }

    /** The spells come from the server: look for shock impacts after joining. */
    private static void scan() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        SpellRegistry.stream(level).forEach(entry -> {
            ResourceLocation id = entry.key().location();
            boolean ownSpell = id.getNamespace().equals(Stormcaller.MODID);
            if (!ownSpell && ShockImpacts.baseChance(entry.value()).isEmpty()) {
                return;
            }
            if (OWN.contains(id) || TooltipTokens.customFor(id) == null) {
                TooltipTokens.registerCustom(id, args -> append(extraTokens(args.description(), args.spellEntry().value()),
                        args.spellEntry().value()));
                OWN.add(id);
            }
        });
    }

    private static String extraTokens(String description, Spell spell) {
        if (description.contains(RICOCHET_RANGE_ADD) && spell.modifiers != null) {
            float range = 0.0F;
            for (Spell.Modifier modifier : spell.modifiers) {
                if (modifier.projectile_perks != null) {
                    range += modifier.projectile_perks.ricochet_range;
                }
            }
            description = description.replace(RICOCHET_RANGE_ADD, TooltipTokens.formattedNumber(range));
        }
        return description;
    }

    private static String append(String description, Spell spell) {
        OptionalDouble chance = ShockImpacts.baseChance(spell);
        if (chance.isEmpty()) {
            return description;
        }
        double value = chance.getAsDouble();
        String line;
        if (value >= 1.0) {
            line = Language.getInstance().getOrDefault(ALWAYS_KEY, "Always shocks.");
        } else {
            double percent = Math.round(value * 1000.0) / 10.0;
            String number = percent == Math.rint(percent) ? Integer.toString((int) percent) : Double.toString(percent);
            // raw lang value with '%%': the description goes through Component.translatable, which turns '%%' into '%'
            line = Language.getInstance().getOrDefault(CHANCE_KEY, "Shock chance: {chance}%%").replace("{chance}", number);
            if (description.replace("%%", "").contains("%")) {
                // a single '%' is already in the text (e.g. "10% less damage"): Minecraft then shows the whole text
                // as it is, without turning '%%' into '%'
                line = line.replace("%%", "%");
            }
        }
        return description + " " + line;
    }
}
