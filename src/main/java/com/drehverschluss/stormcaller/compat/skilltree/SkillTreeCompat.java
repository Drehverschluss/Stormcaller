package com.drehverschluss.stormcaller.compat.skilltree;

import net.skill_tree_rpgs.utils.SkillHelper;

/**
 * Common part of the Skill Tree (RPG Series) support. Only called when Skill Tree is loaded ({@link SkillTab#enabled}).
 * <ul>
 *     <li>Respec: the Orb of Oblivion of Skill Tree resets the categories in {@code SkillHelper.RESET_CATEGORIES};
 *     the Stormcaller tab is added there.</li>
 *     <li>Class lock with the Class Skills tab ({@link ClassLock}).</li>
 * </ul>
 * The tab itself is data ({@link SkillTreePack}), the node tooltips come from the client
 * ({@code compat.skilltree.client.SkillTreeClient}).
 */
public final class SkillTreeCompat {
    private SkillTreeCompat() {}

    public static void init() {
        if (!SkillHelper.RESET_CATEGORIES.contains(SkillTab.STORMCALLER_CATEGORY)) {
            SkillHelper.RESET_CATEGORIES.add(SkillTab.STORMCALLER_CATEGORY);
        }
        ClassLock.init();
    }
}
