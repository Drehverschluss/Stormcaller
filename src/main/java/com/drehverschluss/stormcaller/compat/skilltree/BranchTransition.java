package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.Experience;
import net.puffish.skillsmod.api.SkillsAPI;

/**
 * Moving the Stormcaller nodes between the own tab, the Class Skills tab and the Weapon Skills tab in an existing world.
 * Pufferfish's Skills keeps nodes of tabs and branches that are not loaded in the saved data. After every login and
 * {@code /reload} (after Pufferfish's Skills), only through its public API:
 * <ul>
 *     <li>branch in the Class Skills tab: the nodes of the own tab are removed (its experience stays). If another class was
 *     taken there meanwhile (during a fallback), our nodes in Class Skills go: one class only;</li>
 *     <li>branch in the own tab <b>on purpose</b> (config and mods ask for it): our nodes in the Class Skills tab are removed.
 *     After a fallback because of an error they stay saved and come back with the fix ({@link ClassLock} ignores them);</li>
 *     <li>Lightning Bolt nodes switched off on purpose: our nodes in the Weapon Skills tab are removed (kept after a fallback);</li>
 *     <li>a chat line for each kind of reset (the points are free again, they are computed from the loaded nodes);</li>
 *     <li>on the switch to the own tab, its experience is raised once to that of the Class Skills tab (the own tab collects no
 *     experience while it is not loaded). It only raises, two tabs never add up.</li>
 * </ul>
 * Nothing happens to a tab that is not loaded (e.g. a broken data pack of Pufferfish's Skills), so no data is lost then.
 */
public final class BranchTransition {
    /** Last variant this player had, in the player's persistent data: "class" or "own". */
    private static final String LAST_VARIANT = Stormcaller.MODID + "_skill_variant";
    private static final net.minecraft.resources.ResourceLocation WEAPON_CATEGORY =
            new net.minecraft.resources.ResourceLocation(ClassBranchHook.SKILL_TREE_NAMESPACE, ClassBranchHook.WEAPON_TAB);

    private BranchTransition() {}

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                run(player);
            }
        });
        // /reload: Pufferfish's Skills loads in the same event (normal priority), this runs after it
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() == null) {
                event.getPlayerList().getPlayers().forEach(BranchTransition::run);
            }
        });
    }

    /** The Stormcaller branch is in the Class Skills tab right now. */
    public static boolean classBranchActive() {
        return SkillsAPI.getCategory(SkillTab.CLASS_CATEGORY)
                .flatMap(category -> category.getSkill(ClassBranchHook.PREFIX + "root")).isPresent();
    }

    /** The own Stormcaller tab is loaded right now. */
    public static boolean ownTabActive() {
        return SkillsAPI.getCategory(SkillTab.STORMCALLER_CATEGORY).isPresent();
    }

    public static void run(ServerPlayer player) {
        boolean classActive = classBranchActive();
        boolean ownActive = ownTabActive();
        boolean weaponLoaded = SkillsAPI.getCategory(WEAPON_CATEGORY).isPresent();
        boolean weaponActive = weaponLoaded && SkillsAPI.getCategory(WEAPON_CATEGORY)
                .flatMap(category -> category.getSkill(ClassBranchHook.WEAPON_PREFIX + "root")).isPresent();
        CompoundTag nbt = new CompoundTag();
        SkillsAPI.exportPlayerData(player, nbt);
        CompoundTag categories = nbt.getCompound("categories");
        int ownRemoved = 0;
        int classRemoved = 0;
        int conflictRemoved = 0;
        int weaponRemoved = 0;
        if (classActive && !ownActive) {
            // branch in Class Skills: the own tab is not used any more
            ownRemoved = strip(categories, SkillTab.STORMCALLER_CATEGORY.toString(), null);
            // our nodes back after a fallback, but another class was taken meanwhile: one class only, ours goes
            if (hasOurs(categories, SkillTab.CLASS_CATEGORY.toString(), ClassBranchHook.PREFIX, true)
                    && hasOurs(categories, SkillTab.CLASS_CATEGORY.toString(), ClassBranchHook.PREFIX, false)) {
                conflictRemoved = strip(categories, SkillTab.CLASS_CATEGORY.toString(), ClassBranchHook.PREFIX);
            }
        } else if (ownActive && !classActive && !SkillTreeVariant.choose().classTree()) {
            // wanted switch to the own tab (not a fallback after an error): our nodes in Class Skills go
            classRemoved = strip(categories, SkillTab.CLASS_CATEGORY.toString(), ClassBranchHook.PREFIX);
        }
        if (weaponLoaded && !weaponActive && !SkillTreeVariant.weapon().on()) {
            // Lightning Bolt nodes switched off on purpose (not a fallback)
            weaponRemoved = strip(categories, WEAPON_CATEGORY.toString(), ClassBranchHook.WEAPON_PREFIX);
        }
        if (ownRemoved + classRemoved + conflictRemoved + weaponRemoved > 0) {
            SkillsAPI.importPlayerData(player, nbt);
            message(player, "moved.class", ownRemoved);
            message(player, "moved.own", classRemoved);
            message(player, "moved.conflict", conflictRemoved);
            message(player, "moved.weapon", weaponRemoved);
            Stormcaller.LOGGER.debug("Stormcaller skills: nodes reset for {}: own tab {}, Class Skills {} (+{} conflict), Weapon Skills {}",
                    player.getGameProfile().getName(), ownRemoved, classRemoved, conflictRemoved, weaponRemoved);
        }
        if (classActive == ownActive) {
            return; // nothing loaded: no experience, no marker
        }
        CompoundTag data = player.getPersistentData();
        String last = data.getString(LAST_VARIANT);
        if (ownActive && !"own".equals(last)) {
            takeOverExperience(player, last.isEmpty());
        }
        data.putString(LAST_VARIANT, classActive ? "class" : "own");
    }

    private static void message(ServerPlayer player, String key, int count) {
        if (count > 0) {
            player.sendSystemMessage(Component.translatable("skill." + Stormcaller.MODID + "." + key, count));
        }
    }

    /** Saved nodes of a tab: any of ours ({@code mine = true}) or any foreign one ({@code mine = false}). */
    private static boolean hasOurs(CompoundTag categories, String key, String prefix, boolean mine) {
        if (!categories.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        for (Tag tag : categories.getCompound(key).getList("unlocked_skills", Tag.TAG_STRING)) {
            if (tag.getAsString().startsWith(prefix) == mine) {
                return true;
            }
        }
        return false;
    }

    /** Raises the experience of the own tab to that of the Class Skills tab (switch to the own tab). */
    private static void takeOverExperience(ServerPlayer player, boolean firstTime) {
        Experience own = SkillsAPI.getCategory(SkillTab.STORMCALLER_CATEGORY).flatMap(Category::getExperience).orElse(null);
        Experience classes = SkillsAPI.getCategory(SkillTab.CLASS_CATEGORY).flatMap(Category::getExperience).orElse(null);
        if (own == null || classes == null) {
            return;
        }
        int ownTotal = own.getTotal(player);
        int classTotal = classes.getTotal(player);
        // first time with this version: only a fresh own tab (worlds that always had it keep their own experience)
        if (ownTotal < classTotal && (!firstTime || ownTotal == 0)) {
            own.setTotal(player, classTotal);
            Stormcaller.LOGGER.debug("Stormcaller skills: experience of the own tab {} -> {} for {}", ownTotal, classTotal,
                    player.getGameProfile().getName());
        }
    }

    /** Removes saved nodes of one tab: those starting with {@code prefix}, or all ({@code null}). Returns how many. */
    private static int strip(CompoundTag categories, String key, String prefix) {
        if (!categories.contains(key, Tag.TAG_COMPOUND)) {
            return 0;
        }
        CompoundTag category = categories.getCompound(key);
        ListTag kept = new ListTag();
        int removed = 0;
        for (Tag tag : category.getList("unlocked_skills", Tag.TAG_STRING)) {
            String id = tag.getAsString();
            if (prefix == null || id.startsWith(prefix)) {
                removed++;
            } else {
                kept.add(StringTag.valueOf(id));
            }
        }
        category.put("unlocked_skills", kept);
        return removed;
    }
}
