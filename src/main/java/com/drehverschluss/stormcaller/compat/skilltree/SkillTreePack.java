package com.drehverschluss.stormcaller.compat.skilltree;

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
 * The Stormcaller skill tab is a built-in data pack ({@code resourcepacks/skill_tree} in the jar): category
 * {@code stormcaller:stormcaller} for Pufferfish's Skills with its definitions, nodes and connections.
 * <p>
 * It is only added when Skill Tree (RPG Series) is loaded. Without Skill Tree there is no category at all, so
 * Pufferfish's Skills alone shows no empty tab. The pack is always active and sits at the bottom, so world data packs
 * (or KubeJS) can still replace single files of it.
 * <p>
 * No Skill Tree classes in here: only registered when the mod is present, but kept free of its imports anyway.
 * <p>
 * Forge 1.20.1: no {@code addPackFinders} helper, the pack is built from the path inside the mod file.
 * <p>
 * Its id sorts before {@code file/...} (world data packs) and {@code mod/...}: Minecraft 1.20.1 appends data packs
 * found for the first time in id order, the later ones on top, and keeps a listed pack where it is ("bottom" only
 * applies to packs that are not in the list yet). With an id starting with {@code builtin/} the tab stays below the
 * world data packs of a new world too, so they can replace its files (on 1.21.1 the pack id was
 * {@code mod/stormcaller:resourcepacks/skill_tree}).
 */
public final class SkillTreePack {
    private static final String PATH = "resourcepacks/skill_tree";
    private static final String ID = "builtin/" + Stormcaller.MODID + "_skill_tab";

    private SkillTreePack() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        Path root = ModList.get().getModFileById(Stormcaller.MODID).getFile().findResource(PATH);
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(ID, Component.literal("Stormcaller Skill Tab"), true,
                    name -> new PathPackResources(name, root, true), PackType.SERVER_DATA, Pack.Position.BOTTOM,
                    PackSource.BUILT_IN);
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }
}
