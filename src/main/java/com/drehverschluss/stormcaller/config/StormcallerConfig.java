package com.drehverschluss.stormcaller.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of Stormcaller ({@code config/stormcaller-server.toml}).
 * <p>
 * SERVER type on purpose: NeoForge syncs it to every client on login, so item tooltips on the client show the same
 * values the server uses for the real attributes.
 */
public final class StormcallerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue WIZARDS_TIER_ONE_ENABLED;
    public static final ModConfigSpec.DoubleValue WIZARD_ROBE_LIGHTNING;
    public static final ModConfigSpec.DoubleValue WIZARD_STAFF_LIGHTNING;

    public static final ModConfigSpec.DoubleValue SHOCK_STUN_SECONDS;
    public static final ModConfigSpec.DoubleValue NOVA_STUN_SECONDS;
    public static final ModConfigSpec.BooleanValue SHOCK_STUN_PLAYERS;
    public static final ModConfigSpec.DoubleValue RESISTANT_DAMAGE_PER_STAGE;
    public static final ModConfigSpec.IntValue RESISTANT_MAX_STAGES;
    public static final ModConfigSpec.DoubleValue RESISTANT_STAGE_SECONDS;
    public static final ModConfigSpec.BooleanValue SLOW_ENABLED;
    public static final ModConfigSpec.DoubleValue SLOW_AMOUNT;
    public static final ModConfigSpec.DoubleValue SLOW_SECONDS;
    public static final ModConfigSpec.DoubleValue SLOW_COOLDOWN_SECONDS;

    public static final ModConfigSpec.BooleanValue RICOCHET_FIX_ALL_SPELLS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Lightning values for the tier 1 equipment of Wizards (Wizard Robe and Wizard Staff).",
                        "They use the same modifier IDs as Spell Engine, so an entry for spell_power:lightning in",
                        "config/wizards/equipment_v2.json always wins and nothing is counted twice.",
                        "Changes need a server restart (players keep the old values until then).")
                .push("wizards_tier_one");
        WIZARDS_TIER_ONE_ENABLED = builder
                .comment("Add Lightning Spell Power to the Wizard Robe and the Wizard Staff.")
                .worldRestart()
                .define("enabled", true);
        WIZARD_ROBE_LIGHTNING = builder
                .comment("Lightning Spell Power per Wizard Robe piece (multiplier on base, 0.2 = +20%).")
                .worldRestart()
                .defineInRange("wizard_robe_lightning", 0.2, 0.0, 10.0);
        WIZARD_STAFF_LIGHTNING = builder
                .comment("Flat Lightning Spell Power on the Wizard Staff (main hand).")
                .worldRestart()
                .defineInRange("wizard_staff_lightning", 5.0, 0.0, 1000.0);
        builder.pop();

        builder.comment("Shock: every hit of spell_power:lightning damage may shock the target, with the shock chance",
                        "(stormcaller:shock_chance) of the caster, or of the owner for summons.",
                        "Normal targets are stunned (spell_engine:stun). Targets in #stormcaller:shock_resistant (bosses)",
                        "get the effect stormcaller:shocked instead: more lightning damage per stage and a short slow.",
                        "Targets in #stormcaller:shock_immune get nothing.")
                .push("shock");
        SHOCK_STUN_SECONDS = builder
                .comment("Stun duration of a shock on normal targets, in seconds.")
                .defineInRange("stun_seconds", 0.5, 0.0, 60.0);
        NOVA_STUN_SECONDS = builder
                .comment("Stun duration of Lightning Nova on normal targets, in seconds (it always stuns, no chance roll).")
                .defineInRange("nova_stun_seconds", 1.5, 0.0, 60.0);
        SHOCK_STUN_PLAYERS = builder
                .comment("true: players are stunned like normal targets. false: players get 'Shocked' like bosses.")
                .define("stun_players", false);
        RESISTANT_DAMAGE_PER_STAGE = builder
                .comment("'Shocked': extra lightning damage taken per stage (0.05 = +5%). Other damage types are not changed.")
                .defineInRange("resistant_damage_per_stage", 0.05, 0.0, 10.0);
        RESISTANT_MAX_STAGES = builder
                .comment("'Shocked': highest stage.")
                .defineInRange("resistant_max_stages", 5, 1, 255);
        RESISTANT_STAGE_SECONDS = builder
                .comment("'Shocked': duration in seconds. Every new shock adds a stage and refreshes the duration.")
                .defineInRange("resistant_stage_seconds", 8.0, 0.0, 600.0);
        SLOW_ENABLED = builder
                .comment("'Shocked': slow the target briefly on each shock.")
                .define("slow_enabled", true);
        SLOW_AMOUNT = builder
                .comment("Movement speed reduction of the slow (0.5 = -50%).")
                .defineInRange("slow_amount", 0.5, 0.0, 1.0);
        SLOW_SECONDS = builder
                .comment("Duration of the slow, in seconds.")
                .defineInRange("slow_seconds", 1.0, 0.0, 60.0);
        SLOW_COOLDOWN_SECONDS = builder
                .comment("After a slow ends, the same target cannot be slowed again for this many seconds. Stages still stack.")
                .defineInRange("slow_cooldown_seconds", 3.0, 0.0, 600.0);
        builder.pop();

        builder.comment("Ricochet fix: Spell Engine moves a projectile a full step right after it ricochets, without a hit",
                        "check, so fast projectiles fly through the next target. The fix keeps the projectile in place for",
                        "that one tick. It always applies to the spells in #stormcaller:fx/ricochet_fix.")
                .push("ricochet");
        RICOCHET_FIX_ALL_SPELLS = builder
                .comment("Apply the ricochet fix to the projectiles of all spells, also those of other mods.")
                .define("fix_all_spells", false);
        builder.pop();

        SPEC = builder.build();
    }

    private StormcallerConfig() {}

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
