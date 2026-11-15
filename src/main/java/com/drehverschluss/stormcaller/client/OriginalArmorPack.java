package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Built-in resource pack "Stormcaller_Original_Armor" ({@code resourcepacks/stormcaller_original_armor} in the jar): the
 * robe look of 0.1 and 0.2 (models, textures, glow masks and icons of all four robes). Since 0.3.0 the mod itself ships
 * the classic look; players who prefer the old robes switch this pack on in the resource pack menu.
 * <p>
 * Off by default (like Programmer Art), placed on top when switched on, so it wins over the mod assets. Pack id:
 * {@code mod/stormcaller:resourcepacks/stormcaller_original_armor}. Client only; a resource pack changes what the viewer
 * sees, so every player picks the look for themselves. The glow layer stays the one of the renderers ({@code .glow()}),
 * the pack only brings its own glow masks.
 */
public final class OriginalArmorPack {
    private OriginalArmorPack() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addPackFinders(Stormcaller.id("resourcepacks/stormcaller_original_armor"), PackType.CLIENT_RESOURCES,
                Component.literal("Stormcaller_Original_Armor"), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }
}
