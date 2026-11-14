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
    public static final ModConfigSpec.DoubleValue SHOCKED_DAMAGE_PER_STAGE;
    public static final ModConfigSpec.IntValue SHOCKED_MAX_STAGES;
    public static final ModConfigSpec.DoubleValue SHOCKED_SECONDS;
    public static final ModConfigSpec.BooleanValue SLOW_ENABLED;
    public static final ModConfigSpec.DoubleValue SLOW_AMOUNT;
    public static final ModConfigSpec.DoubleValue SLOW_SECONDS;
    public static final ModConfigSpec.DoubleValue SLOW_COOLDOWN_SECONDS;

    public static final ModConfigSpec.BooleanValue RICOCHET_FIX_ALL_SPELLS;

    public static final ModConfigSpec.BooleanValue TRADES_WIZARD_MERCHANT;

    public static final ModConfigSpec.BooleanValue SKILL_TREE_CLASS_LOCK;

    public static final ModConfigSpec.IntValue BALL_MAX_STAFF_BALLS;

    public static final ModConfigSpec.IntValue STORED_CHARGE_MAX;
    public static final ModConfigSpec.IntValue STORED_CHARGE_EXTRA_RICOCHETS;
    public static final ModConfigSpec.DoubleValue STORED_CHARGE_DECAY_SECONDS;
    public static final ModConfigSpec.DoubleValue STORED_CHARGE_SHOCK_CHANCE;
    public static final ModConfigSpec.BooleanValue STORED_CHARGE_DISCHARGE_HITS_CHARGE;

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

        builder.comment("Shock: a hit with spell_power:lightning damage may shock the target. One roll per hit.",
                        "Stormcaller spells: base chance of the spell (shock impact in the spell JSON, e.g. stormcaller:shock/15)",
                        "plus the shock chance (stormcaller:shock_chance) of the caster, at most 100%.",
                        "Lightning damage of other mods: only the shock chance. For summons the shock chance of the owner counts.",
                        "Every shock gives one stage of stormcaller:shocked (more lightning damage taken) to every target.",
                        "On top, normal targets are stunned (spell_engine:stun), targets in #stormcaller:shock_resistant (bosses)",
                        "are slowed briefly instead. Targets in #stormcaller:shock_immune get nothing.")
                .push("shock");
        SHOCK_STUN_SECONDS = builder
                .comment("Stun duration of a shock on normal targets, in seconds.")
                .defineInRange("stun_seconds", 0.5, 0.0, 60.0);
        NOVA_STUN_SECONDS = builder
                .comment("Stun duration of Lightning Nova on normal targets, in seconds (it always shocks).")
                .defineInRange("nova_stun_seconds", 1.5, 0.0, 60.0);
        SHOCK_STUN_PLAYERS = builder
                .comment("true: players are stunned like normal targets. false: players are slowed like bosses.",
                         "Players get the 'Shocked' stages either way.")
                .define("stun_players", false);
        SHOCKED_DAMAGE_PER_STAGE = builder
                .comment("'Shocked' (all targets): extra lightning damage taken per stage (0.05 = +5%).",
                         "Other damage types are not changed.")
                .defineInRange("shocked_damage_per_stage", 0.05, 0.0, 10.0);
        SHOCKED_MAX_STAGES = builder
                .comment("'Shocked' (all targets): highest stage.")
                .defineInRange("shocked_max_stages", 5, 1, 255);
        SHOCKED_SECONDS = builder
                .comment("'Shocked' (all targets): duration in seconds. Every new shock adds a stage and refreshes the duration.",
                         "0 turns the stages off.")
                .defineInRange("shocked_seconds", 8.0, 0.0, 600.0);
        SLOW_ENABLED = builder
                .comment("Bosses (#stormcaller:shock_resistant): slow the target briefly on a shock instead of stunning it.")
                .define("slow_enabled", true);
        SLOW_AMOUNT = builder
                .comment("Bosses: movement speed reduction of the slow (0.5 = -50%).")
                .defineInRange("slow_amount", 0.5, 0.0, 1.0);
        SLOW_SECONDS = builder
                .comment("Bosses: duration of the slow, in seconds.")
                .defineInRange("slow_seconds", 1.0, 0.0, 60.0);
        SLOW_COOLDOWN_SECONDS = builder
                .comment("Bosses: after a slow ends, the same target cannot be slowed again for this many seconds.",
                         "Stages of 'Shocked' still stack in that time.")
                .defineInRange("slow_cooldown_seconds", 3.0, 0.0, 600.0);
        builder.pop();

        builder.comment("Ricochet fix: Spell Engine moves a projectile a full step right after it ricochets, without a hit",
                        "check, so fast projectiles fly through the next target. The fix keeps the projectile in place for",
                        "that one tick. It also looks for the next target around the target that was hit, not around the",
                        "projectile (which is still up to one step in front of it), so the full ricochet range counts in",
                        "every direction. It always applies to the spells in #stormcaller:fx/ricochet_fix.")
                .push("ricochet");
        RICOCHET_FIX_ALL_SPELLS = builder
                .comment("Apply the ricochet fix to the projectiles of all spells, also those of other mods.")
                .define("fix_all_spells", false);
        builder.pop();

        builder.comment("Villager trades.")
                .push("trades");
        TRADES_WIZARD_MERCHANT = builder
                .comment("The Wizard Merchant of Wizards also offers Lightning items, like the other schools: Lightning Rune",
                         "(level 1), Lightning Wand (level 2), enchanted Lightning Staff (level 5). false: no Lightning offers,",
                         "the merchant picks from the offers of the other schools only. Applies to offers rolled from now on;",
                         "merchants keep the offers they already have.")
                .define("wizard_merchant", true);
        builder.pop();

        builder.comment("Stormcaller skill tab (only with Skill Tree (RPG Series) installed).")
                .push("skill_tree");
        SKILL_TREE_CLASS_LOCK = builder
                .comment("Stormcaller counts as a class: a player with a class (root) in the Class Skills tab of Skill Tree",
                         "cannot take the Stormcaller root, and the other way round. Players who already have both keep them.")
                .define("class_lock", true);
        builder.pop();

        builder.comment("Ball Lightning. Chance, cooldown, damage and shock chance are in the spell files (data pack),",
                        "movement, lifetime, pulse, range and target count in config/stormcaller/trinkets.json.")
                .push("ball_lightning");
        BALL_MAX_STAFF_BALLS = builder
                .comment("Most balls of the Stormbringer Staff (spell stormcaller:ball_lightning_staff) one player can have at",
                         "once. Each staff ball lives its full time; when the limit is reached, casting again ends the oldest",
                         "staff ball. The ball of the Caged Ball Lightning relic counts on its own (always one at most).")
                .defineInRange("max_staff_balls", 3, 1, 10);
        builder.pop();

        builder.comment("Stored Charge, the passive of the Amber Lightning Staff (main hand). Every hit of an active",
                        "lightning spell with damage gives one charge (at most one per tick; the trigger is in the spell",
                        "file stormcaller:stored_charge). At full charge the next Lightning Bolt cast with the staff",
                        "discharges: it ricochets to more targets and its hits shock. Then the charge is empty.")
                .push("stored_charge");
        STORED_CHARGE_MAX = builder
                .comment("Charges for a full staff (stages of the effect 'Stored Charge').")
                .defineInRange("max_charges", 5, 1, 50);
        STORED_CHARGE_EXTRA_RICOCHETS = builder
                .comment("Extra ricochets of the discharging Lightning Bolt (added to the ricochets of the spell and its modifiers).")
                .defineInRange("extra_ricochets", 2, 0, 20);
        STORED_CHARGE_DECAY_SECONDS = builder
                .comment("The whole charge is lost this many seconds after the last hit that charged it (also a full one).")
                .defineInRange("decay_seconds", 15.0, 1.0, 600.0);
        STORED_CHARGE_SHOCK_CHANCE = builder
                .comment("Base shock chance of every hit of the discharging Lightning Bolt (1.0 = always shocks; bosses are",
                         "slowed instead of stunned, as with every shock). Replaces the base chance of the spell for those hits.")
                .defineInRange("discharge_shock_chance", 1.0, 0.0, 1.0);
        STORED_CHARGE_DISCHARGE_HITS_CHARGE = builder
                .comment("true: the hits of the discharging Lightning Bolt charge the staff again (in groups of 5 or more enemies it",
                         "is full again right away). false: they give no charge, the staff fills only through later hits.")
                .define("discharge_hits_charge", false);
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
