package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.Experience;
import net.puffish.skillsmod.api.SkillsAPI;

/**
 * Moving the Stormcaller branch between the own tab and the Class Skills tab in an existing world (config change, More
 * RPG Classes added or removed, fallback). Pufferfish's Skills keeps nodes of tabs and branches that are not loaded in the
 * saved data; they would keep counting as a class for the class lock. After every login and {@code /reload} (after
 * Pufferfish's Skills), only through its public API:
 * <ul>
 *     <li>branch in the own tab: our nodes in the Class Skills tab are removed;</li>
 *     <li>branch in the Class Skills tab: the nodes of the own tab are removed (its experience stays);</li>
 *     <li>a chat line if anything was removed (the points are free again, they are computed from the loaded nodes);</li>
 *     <li>on the switch to the own tab, its experience is raised once to that of the Class Skills tab (the own tab collects no
 *     experience while it is not loaded).</li>
 * </ul>
 * Nothing happens while neither variant is loaded (e.g. a broken data pack of Pufferfish's Skills), so no data is lost then.
 */
public final class BranchTransition {
    /** Last variant this player had, in the player's persistent data: "class" or "own". */
    private static final String LAST_VARIANT = Stormcaller.MODID + "_skill_variant";

    private BranchTransition() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                run(player);
            }
        });
        // /reload: Pufferfish's Skills loads in the same event (normal priority), this runs after it
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, OnDatapackSyncEvent.class, event -> {
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
        if (classActive == ownActive) {
            return; // nothing loaded (or both, which the hook never does): touch nothing
        }
        CompoundTag nbt = new CompoundTag();
        SkillsAPI.exportPlayerData(player, nbt);
        CompoundTag categories = nbt.getCompound("categories");
        int removed = classActive
                ? strip(categories, SkillTab.STORMCALLER_CATEGORY.toString(), false)
                : strip(categories, SkillTab.CLASS_CATEGORY.toString(), true);
        if (removed > 0) {
            SkillsAPI.importPlayerData(player, nbt);
            player.sendSystemMessage(Component.translatable("skill." + Stormcaller.MODID + ".moved." + (classActive ? "class" : "own"),
                    removed));
            Stormcaller.LOGGER.debug("Stormcaller skills: {} nodes of {} reset for {}", removed, classActive ? "the own tab" : "Class Skills",
                    player.getGameProfile().getName());
        }
        String variant = classActive ? "class" : "own";
        CompoundTag data = player.getPersistentData();
        String last = data.getString(LAST_VARIANT);
        if (ownActive && !"own".equals(last)) {
            takeOverExperience(player, last.isEmpty());
        }
        data.putString(LAST_VARIANT, variant);
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

    /** Removes saved nodes of one tab: only ours ({@code stormcaller_…}) or all of them. Returns how many. */
    private static int strip(CompoundTag categories, String key, boolean onlyOurs) {
        if (!categories.contains(key, Tag.TAG_COMPOUND)) {
            return 0;
        }
        CompoundTag category = categories.getCompound(key);
        ListTag kept = new ListTag();
        int removed = 0;
        for (Tag tag : category.getList("unlocked_skills", Tag.TAG_STRING)) {
            String id = tag.getAsString();
            if (!onlyOurs || id.startsWith(ClassBranchHook.PREFIX)) {
                removed++;
            } else {
                kept.add(StringTag.valueOf(id));
            }
        }
        category.put("unlocked_skills", kept);
        return removed;
    }
}
