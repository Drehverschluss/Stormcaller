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

        SPEC = builder.build();
    }

    private StormcallerConfig() {}

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Value of a config entry, or its default while the server config is not loaded (e.g. in the main menu). */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
