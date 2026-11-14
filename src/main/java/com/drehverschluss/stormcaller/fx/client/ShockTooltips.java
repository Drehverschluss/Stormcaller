package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import com.drehverschluss.stormcaller.shock.ShockImpacts;
import com.drehverschluss.stormcaller.trinket.BallLightning;
import com.drehverschluss.stormcaller.spell.MaxTargets;
import com.drehverschluss.stormcaller.spell.SkillMechanics;
import com.drehverschluss.stormcaller.spell.StoredCharge;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.impact.SpellEstimation;
import net.minecraft.world.item.ItemStack;
import net.spell_power.api.SpellPower;
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
 * Stormcaller spells also get tokens that Spell Engine does not have:
 * <ul>
 *     <li>{@code {ricochet_range_add}}: sum of {@code projectile_perks.ricochet_range} of the modifiers of a modifier
 *     spell (Long Arc),</li>
 *     <li>{@code {nova_stun}}: stun of Lightning Nova (server config plus {@code effect_duration_add} of the player's
 *     modifiers, e.g. Thunderclap),</li>
 *     <li>{@code {cloud_radius_add}}: sum of {@code cloud.radius_add} of the modifiers of a modifier spell (Stormweave
 *     set bonus),</li>
 *     <li>{@code {cloud_max_targets}}: most enemies one hit pulse of a cloud strikes ({@code stormcaller:max_targets/<n>},
 *     {@link MaxTargets}),</li>
 *     <li>{@code {static_rain_duration}}, {@code {conductor_cooldown}} (cooldown of Chain Lightning),</li>
 *     <li>{@code {field_duration|radius|damage|shock}}: the cloud spell {@code <id>_field} a spell places (Charged Ground).</li>
 *     <li>{@code {ball_*}}: Ball Lightning (Caged Ball Lightning relic): duration, pulse, range and target count from
 *     {@code trinkets.json}, damage and shock chance of the spells {@code ball_lightning_zap} and
 *     {@code ball_lightning_discharge}, cooldown of the passive. The shock chance is written in the text, so no shock
 *     line is added. The staff spell {@code ball_lightning_staff} reads its own helper spells
 *     ({@code ball_lightning_staff_zap|discharge}) and has {@code {ball_staff_max}} (server config).</li>
 *     <li>{@code {charge_*}}: Stored Charge of the Amber Lightning Staff ({@code [stored_charge]} of the server config):
 *     max, ricochet, decay, shock, {@code {charge_discharge_note}} (half sentence "its hits do not charge", empty with
 *     {@code discharge_hits_charge = true}).</li>
 * </ul>
 */
public final class ShockTooltips {
    private static final String CHANCE_KEY = "spell.tooltip.stormcaller.shock_chance";
    private static final String ALWAYS_KEY = "spell.tooltip.stormcaller.shock_always";
    private static final String DISCHARGE_NOTE_KEY = "spell.tooltip.stormcaller.stored_charge.discharge_no_charge";
    private static final String RICOCHET_RANGE_ADD = "{ricochet_range_add}";
    private static final String NOVA_STUN = "{nova_stun}";
    private static final String CLOUD_RADIUS_ADD = "{cloud_radius_add}";
    private static final String CLOUD_MAX_TARGETS = "{cloud_max_targets}";
    private static final String STATIC_RAIN_DURATION = "{static_rain_duration}";
    private static final String CONDUCTOR_COOLDOWN = "{conductor_cooldown}";
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
                TooltipTokens.registerCustom(id, args -> append(extraTokens(args.description(), args.spellEntry(), args.player()),
                        args.spellEntry().value()));
                OWN.add(id);
            }
        });
    }

    private static String extraTokens(String description, Holder<Spell> entry, Player player) {
        Spell spell = entry.value();
        if (description.contains(RICOCHET_RANGE_ADD) && spell.modifiers != null) {
            float range = 0.0F;
            for (Spell.Modifier modifier : spell.modifiers) {
                if (modifier.projectile_perks != null) {
                    range += modifier.projectile_perks.ricochet_range;
                }
            }
            description = description.replace(RICOCHET_RANGE_ADD, TooltipTokens.formattedNumber(range));
        }
        if (description.contains(CLOUD_RADIUS_ADD) && spell.modifiers != null) {
            float radius = 0.0F;
            for (Spell.Modifier modifier : spell.modifiers) {
                if (modifier.cloud != null) {
                    radius += modifier.cloud.radius_add;
                }
            }
            description = description.replace(CLOUD_RADIUS_ADD, TooltipTokens.formattedNumber(radius));
        }
        if (description.contains(CLOUD_MAX_TARGETS)) {
            description = description.replace(CLOUD_MAX_TARGETS, Integer.toString(MaxTargets.of(spell)));
        }
        if (description.contains(NOVA_STUN)) {
            // stun of Lightning Nova from the server config, plus Thunderclap and other effect_duration_add modifiers
            float seconds = (float) StormcallerConfig.get(StormcallerConfig.NOVA_STUN_SECONDS);
            if (player != null) {
                for (Spell.Modifier modifier : SpellModifiers.of(player, entry)) {
                    seconds += modifier.effect_duration_add;
                }
            }
            description = description.replace(NOVA_STUN, TooltipTokens.formattedNumber(seconds));
        }
        if (description.contains(STATIC_RAIN_DURATION)) {
            description = description.replace(STATIC_RAIN_DURATION, TooltipTokens.formattedNumber(SkillMechanics.STATIC_RAIN_TICKS / 20.0F));
        }
        if (description.contains(CONDUCTOR_COOLDOWN)) {
            Spell chain = spell(Stormcaller.id("chain_lightning"));
            float cooldown = chain == null || chain.cost.cooldown == null ? 0.0F : chain.cost.cooldown.duration;
            description = description.replace(CONDUCTOR_COOLDOWN, TooltipTokens.formattedNumber(cooldown));
        }
        if (description.contains("{field_")) {
            description = fieldTokens(description, entry, player);
        }
        if (description.contains("{ball_")) {
            description = ballTokens(description, entry, player);
        }
        if (description.contains("{charge_")) {
            description = description
                    .replace("{charge_max}", Integer.toString(StoredCharge.maxCharges()))
                    .replace("{charge_ricochet}", Integer.toString(StoredCharge.extraRicochets()))
                    .replace("{charge_decay}", TooltipTokens.formattedNumber((float) StoredCharge.decaySeconds()))
                    .replace("{charge_shock}", percent(StoredCharge.dischargeShockChance()))
                    .replace("{charge_discharge_note}", StoredCharge.dischargeHitsCharge() ? ""
                            : Language.getInstance().getOrDefault(DISCHARGE_NOTE_KEY, "; its hits do not charge"));
        }
        return description;
    }

    /** Tokens of the Ball Lightning description, see the class comment. */
    private static String ballTokens(String description, Holder<Spell> entry, Player player) {
        Spell spell = entry.value();
        BallLightning.Config config = BallLightning.config();
        boolean staff = entry.is(BallLightning.STAFF_SPELL);
        Spell zap = spell(staff ? BallLightning.STAFF_ZAP_SPELL : BallLightning.ZAP_SPELL);
        Spell discharge = spell(staff ? BallLightning.STAFF_DISCHARGE_SPELL : BallLightning.DISCHARGE_SPELL);
        // staff: like Spell Engine's {damage}, the real power while the staff is in the main hand, otherwise only the
        // staff's own bonus (the tooltip of a staff in the inventory)
        ItemStack weapon = staff ? stormbringerStack(player) : ItemStack.EMPTY;
        float cooldown = spell.cost == null || spell.cost.cooldown == null ? 0.0F : spell.cost.cooldown.duration;
        return description
                .replace("{ball_duration}", TooltipTokens.formattedNumber(config.duration_seconds))
                .replace("{ball_pulse}", TooltipTokens.formattedNumber(config.pulse_seconds))
                .replace("{ball_range}", TooltipTokens.formattedNumber(config.range))
                .replace("{ball_targets}", Integer.toString(config.max_targets))
                .replace("{ball_cooldown}", TooltipTokens.formattedNumber(cooldown))
                .replace("{ball_zap_damage}", damageOf(zap, player, weapon))
                .replace("{ball_zap_shock}", shockOf(zap))
                .replace("{ball_discharge_damage}", damageOf(discharge, player, weapon))
                .replace("{ball_discharge_shock}", shockOf(discharge))
                .replace("{ball_staff_max}", Integer.toString(BallLightning.maxStaffBalls()));
    }

    /** 0..1 as "10%" / "12.5%". */
    private static String percent(double value) {
        double percent = Math.round(value * 1000.0) / 10.0;
        String number = percent == Math.rint(percent) ? Integer.toString((int) percent) : Double.toString(percent);
        return number + "%";
    }

    /** The Stormbringer Staff the player holds, else a fresh one (its own bonus only), else empty. */
    private static ItemStack stormbringerStack(Player player) {
        var entry = com.drehverschluss.stormcaller.item.StormcallerItems.staffStormbringer;
        if (entry == null || entry.item() == null) {
            return ItemStack.EMPTY;
        }
        if (player != null && player.getMainHandItem().is(entry.item())) {
            return player.getMainHandItem();
        }
        return new ItemStack(entry.item());
    }

    /**
     * Damage of the first DAMAGE impact of a spell without crits. With a weapon: Spell Engine's estimation for that stack
     * (real power when it is equipped, else only its flat bonus), otherwise the player's spell power.
     */
    private static String damageOf(Spell spell, Player player, ItemStack weapon) {
        if (spell == null) {
            return "?";
        }
        if (player != null && !weapon.isEmpty()) {
            var damage = SpellEstimation.estimate(spell, player, weapon).damage();
            if (!damage.isEmpty()) {
                return TooltipTokens.formattedNumber((float) damage.get(0).min());
            }
        }
        float coefficient = 0.0F;
        for (Spell.Impact impact : spell.impacts) {
            if (impact.action != null && impact.action.type == Spell.Impact.Action.Type.DAMAGE && impact.action.damage != null) {
                coefficient = impact.action.damage.spell_power_coefficient;
                break;
            }
        }
        double power = player == null ? 0.0 : SpellPower.getSpellPower(spell.school, player).baseValue();
        return TooltipTokens.formattedNumber((float) (coefficient * power));
    }

    /** Base shock chance of a spell as "10%" (without the shock chance of the player). */
    private static String shockOf(Spell spell) {
        double percent = spell == null ? 0.0 : Math.round(ShockImpacts.baseChance(spell).orElse(0.0) * 1000.0) / 10.0;
        String number = percent == Math.rint(percent) ? Integer.toString((int) percent) : Double.toString(percent);
        return number + "%";
    }

    /**
     * {@code {field_duration}}, {@code {field_radius}}, {@code {field_damage}}, {@code {field_shock}}: values of the cloud
     * spell {@code <spell id>_field} that the spell places (Charged Ground -> charged_ground_field).
     */
    private static String fieldTokens(String description, Holder<Spell> entry, Player player) {
        ResourceLocation id = entry.unwrapKey().map(k -> k.location()).orElse(null);
        Spell field = id == null ? null : spell(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_field"));
        if (field == null || field.deliver == null || field.deliver.clouds == null || field.deliver.clouds.isEmpty()) {
            return description;
        }
        Spell.Delivery.Cloud cloud = field.deliver.clouds.get(0);
        float coefficient = 0.0F;
        for (Spell.Impact impact : field.impacts) {
            if (impact.action != null && impact.action.type == Spell.Impact.Action.Type.DAMAGE && impact.action.damage != null) {
                coefficient = impact.action.damage.spell_power_coefficient;
                break;
            }
        }
        double power = player == null ? 0.0 : SpellPower.getSpellPower(field.school, player).baseValue();
        double shock = ShockImpacts.baseChance(field).orElse(0.0) * 100.0;
        return description
                .replace("{field_duration}", TooltipTokens.formattedNumber(cloud.time_to_live_seconds))
                .replace("{field_radius}", TooltipTokens.formattedNumber(cloud.volume.radius))
                .replace("{field_damage}", TooltipTokens.formattedNumber((float) (coefficient * power)))
                .replace("{field_shock}", TooltipTokens.formattedNumber((float) shock));
    }

    private static Spell spell(ResourceLocation id) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return SpellRegistry.from(level).getOptional(id).orElse(null);
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
