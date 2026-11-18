package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.config.StormcallerConfig;
import net.minecraftforge.fml.ModList;

/**
 * Which variant of the Stormcaller skills the config asks for ({@code [skill_tree] mode}). Whether the branch really is in
 * the Class Skills tab decides {@link ClassBranchHook} while Pufferfish's Skills loads (fallback: own tab). Free of Skill Tree
 * and Pufferfish's Skills classes.
 */
public final class SkillTreeVariant {
    /** More RPG Classes - Skill Tree: replaces the Class Skills tab of Skill Tree with its own circle. */
    public static final String MORE_RPG_CLASSES = "mrpgc_skill_tree";

    /**
     * @param classTree try the Class Skills tab
     * @param reason    for the log
     * @param respace   layout respace (otherwise gap)
     */
    public record Choice(boolean classTree, String reason, boolean respace) {}

    private SkillTreeVariant() {}

    public static boolean moreRpgClasses() {
        return ModList.get().isLoaded(MORE_RPG_CLASSES);
    }

    /**
     * @param on     put the Lightning Bolt nodes into the Weapon Skills tab
     * @param reason for the log
     */
    public record WeaponChoice(boolean on, String reason) {}

    /** Lightning Bolt nodes: {@code weapon_skills} and (no More RPG Classes or mode class_tree). */
    public static WeaponChoice weapon() {
        if (!StormcallerConfig.get(StormcallerConfig.SKILL_TREE_WEAPON_SKILLS)) {
            return new WeaponChoice(false, "weapon_skills = false");
        }
        if (moreRpgClasses() && !"class_tree".equals(StormcallerConfig.get(StormcallerConfig.SKILL_TREE_MODE))) {
            return new WeaponChoice(false, "More RPG Classes - Skill Tree is installed and mode is not class_tree");
        }
        return new WeaponChoice(true, moreRpgClasses() ? "Weapon Skills of More RPG Classes - Skill Tree" : "Weapon Skills");
    }

    public static Choice choose() {
        String mode = StormcallerConfig.get(StormcallerConfig.SKILL_TREE_MODE);
        boolean respace = !"gap".equals(StormcallerConfig.get(StormcallerConfig.SKILL_TREE_CLASS_TREE_LAYOUT));
        if ("own_tab".equals(mode)) {
            return new Choice(false, "mode own_tab", respace);
        }
        if ("class_tree".equals(mode)) {
            return new Choice(true, moreRpgClasses() ? "mode class_tree, circle of More RPG Classes - Skill Tree" : "mode class_tree",
                    respace);
        }
        if (moreRpgClasses()) {
            return new Choice(false, "mode auto, More RPG Classes - Skill Tree is installed", respace);
        }
        return new Choice(true, "mode auto", respace);
    }
}
