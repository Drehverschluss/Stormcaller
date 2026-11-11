package com.drehverschluss.stormcaller.compat.curios;

import net.neoforged.fml.ModList;

/**
 * Gate to the Curios integration. This class has no Curios reference; {@link CuriosItems} and
 * {@link StormcallerCurioItem} are only touched after {@link #isLoaded()}, so Stormcaller loads without Curios
 * (the trinket items then do not exist).
 */
public final class CuriosCompat {
    public static final String MOD_ID = "curios";

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Registers the rings, amulets and relics. Only call when {@link #isLoaded()}. */
    public static void registerItems() {
        CuriosItems.register();
    }
}
