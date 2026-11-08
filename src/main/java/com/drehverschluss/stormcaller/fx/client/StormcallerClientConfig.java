package com.drehverschluss.stormcaller.fx.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config of Stormcaller ({@code config/stormcaller-client.toml}). Only changes what this client draws.
 */
public final class StormcallerClientConfig {
    public enum Strength { REDUCED, FULL }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ARCS_ENABLED;
    public static final ModConfigSpec.EnumValue<Strength> STRENGTH;
    public static final ModConfigSpec.IntValue MAX_ARCS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Lightning visuals of the Stormcaller spells.").push("fx");
        ARCS_ENABLED = builder
                .comment("Draw lightning arcs: jumps between targets, arcs at impacts, the Lightning Nova ring and strikes.",
                         "Flashes, sparks and the bodies of flying projectiles are always drawn. Spells work the same either way.")
                .define("arcs_enabled", true);
        STRENGTH = builder
                .comment("FULL: branches, wide glow and sky flashes. REDUCED: thinner, no branches, fewer sparks, no sky flash.")
                .defineEnum("strength", Strength.FULL);
        MAX_ARCS = builder
                .comment("Most lightning arcs at the same time. When more arrive, the oldest ones are removed.")
                .defineInRange("max_arcs", 64, 0, 1024);
        builder.pop();
        SPEC = builder.build();
    }

    private StormcallerClientConfig() {}

    public static boolean arcsEnabled() {
        return SPEC.isLoaded() ? ARCS_ENABLED.get() : ARCS_ENABLED.getDefault();
    }

    public static boolean reduced() {
        return (SPEC.isLoaded() ? STRENGTH.get() : STRENGTH.getDefault()) == Strength.REDUCED;
    }

    public static int maxArcs() {
        return SPEC.isLoaded() ? MAX_ARCS.get() : MAX_ARCS.getDefault();
    }
}
