package com.drehverschluss.stormcaller.client;

import com.drehverschluss.stormcaller.Stormcaller;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

/**
 * Built-in resource pack "Stormcaller_Original_Armor" ({@code resourcepacks/stormcaller_original_armor} in the jar): the
 * robe look of 0.1 and 0.2 (models, textures, glow masks and icons of all four robes). Since 0.3.0 the mod itself ships
 * the classic look; players who prefer the old robes switch this pack on in the resource pack menu.
 * <p>
 * Off by default (like Programmer Art), placed on top when switched on, so it wins over the mod assets. Pack id:
 * {@code mod/stormcaller:resourcepacks/stormcaller_original_armor}. Client only; a resource pack changes what the viewer
 * sees, so every player picks the look for themselves. The glow layer stays the one of the renderers ({@code .glow()}),
 * the pack only brings its own glow masks.
 * <p>
 * Forge 1.20.1: no {@code addPackFinders} helper, the pack is built from the path inside the mod file.
 */
public final class OriginalArmorPack {
    private static final String PATH = "resourcepacks/stormcaller_original_armor";

    private OriginalArmorPack() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = ModList.get().getModFileById(Stormcaller.MODID).getFile().findResource(PATH);
        String id = "mod/" + Stormcaller.MODID + ":" + PATH;
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(id, Component.literal("Stormcaller_Original_Armor"), false,
                    name -> new PathPackResources(name, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }
}
