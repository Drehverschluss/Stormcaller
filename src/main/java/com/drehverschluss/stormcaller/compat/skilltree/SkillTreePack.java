package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The Stormcaller skill tab is a built-in data pack ({@code resourcepacks/skill_tree} in the jar): category
 * {@code stormcaller:stormcaller} for Pufferfish's Skills with its definitions, nodes and connections.
 * <p>
 * It is only added when Skill Tree (RPG Series) is loaded. Without Skill Tree there is no category at all, so
 * Pufferfish's Skills alone shows no empty tab. The pack is always active and sits at the bottom, so world data packs
 * (or KubeJS) can still replace single files of it.
 * <p>
 * No Skill Tree classes in here: only registered when the mod is present, but kept free of its imports anyway.
 */
public final class SkillTreePack {
    private SkillTreePack() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addPackFinders(Stormcaller.id("resourcepacks/skill_tree"), PackType.SERVER_DATA,
                Component.literal("Stormcaller Skill Tab"), PackSource.BUILT_IN, true, Pack.Position.BOTTOM);
    }
}
