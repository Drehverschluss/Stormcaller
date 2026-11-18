package com.drehverschluss.stormcaller.compat.skilltree;

import net.skill_tree_rpgs.utils.SkillHelper;

/**
 * Common part of the Skill Tree (RPG Series) support. Only called when Skill Tree is loaded ({@link SkillTab#enabled}).
 * <ul>
 *     <li>Respec: the Orb of Oblivion of Skill Tree resets the categories in {@code SkillHelper.RESET_CATEGORIES};
 *     the Stormcaller tab is added there ({@link #commonSetup}).</li>
 *     <li>Class lock with the Class Skills tab ({@link ClassLock}).</li>
 * </ul>
 * The tab itself is data ({@link SkillTreePack}), the node tooltips come from the client
 * ({@code compat.skilltree.client.SkillTreeClient}).
 */
public final class SkillTreeCompat {
    private SkillTreeCompat() {}

    /** Mod constructor: listeners only. */
    public static void init() {
        ClassLock.init();
        BranchTransition.init();
    }

    /**
     * Common setup (after registration). Forge 1.20.1: touching {@code SkillHelper} starts the static setup of Skill
     * Tree's node types, which looks up attributes in the registry; in the mod constructor they are not registered yet
     * (on 1.21.1 this ran in the constructor).
     */
    public static void commonSetup() {
        if (!SkillHelper.RESET_CATEGORIES.contains(SkillTab.STORMCALLER_CATEGORY)) {
            SkillHelper.RESET_CATEGORIES.add(SkillTab.STORMCALLER_CATEGORY);
        }
    }
}
