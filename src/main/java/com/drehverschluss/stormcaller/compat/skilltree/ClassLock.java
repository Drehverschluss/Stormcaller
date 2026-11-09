package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.SkillsAPI;
import org.jetbrains.annotations.Nullable;

/**
 * Class lock: the Stormcaller tab counts as a class. A player with a class (a root) in the Class Skills tab of Skill
 * Tree cannot take the Stormcaller root, and the other way round. Switch: {@code [skill_tree] class_lock} in
 * {@code stormcaller-server.toml}.
 * <p>
 * Pufferfish's Skills has no event that can cancel an unlock. So the unlock event undoes it right away through the
 * public API ({@code Skill.lock}): the point comes back, the rewards are removed again (Pufferfish's Skills updates the
 * rewards of the node after the event, with the count of unlocked nodes, which is 0 by then). The player gets a toast.
 * The skill screen shows the blocked roots as locked ({@code ClientCategoryDataMixin}), so normally nobody clicks them.
 * <p>
 * "Has a class" = has any unlocked node in that tab; the first node of a tab is always its root.
 */
public final class ClassLock {
    /** Last state sent per player, as bits (see {@link #bits}). */
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private ClassLock() {}

    static void init() {
        SkillsAPI.registerSkillUnlockEvent(ClassLock::onUnlock);
        SkillsAPI.registerSkillLockEvent((player, categoryId, skillId) -> {
            if (other(categoryId) != null) {
                sync(player, ClassLockPayload.TOAST_NONE, true);
            }
        });
        // login, config changes, commands, other mods: compare once per second, send on change
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
                sync(player, ClassLockPayload.TOAST_NONE, false);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SENT.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> SENT.clear());
    }

    public static boolean enabled() {
        return StormcallerConfig.get(StormcallerConfig.SKILL_TREE_CLASS_LOCK);
    }

    private static void onUnlock(ServerPlayer player, ResourceLocation categoryId, String skillId) {
        ResourceLocation other = other(categoryId);
        if (other == null) {
            return;
        }
        int toast = ClassLockPayload.TOAST_NONE;
        if (enabled() && unlockedCount(player, categoryId) == 1 && unlockedCount(player, other) > 0) {
            // the first node of this tab (its root) while the other tab already has a class: undo
            SkillsAPI.getCategory(categoryId).flatMap(category -> category.getSkill(skillId))
                    .ifPresent(skill -> skill.lock(player));
            toast = SkillTab.STORMCALLER_CATEGORY.equals(categoryId)
                    ? ClassLockPayload.TOAST_STORMCALLER : ClassLockPayload.TOAST_CLASS;
            Stormcaller.LOGGER.debug("Stormcaller: class lock blocked {} {} for {}", categoryId, skillId,
                    player.getGameProfile().getName());
        }
        sync(player, toast, true);
    }

    /** Sends the lock state if it changed (or always with a toast or {@code force}). */
    private static void sync(ServerPlayer player, int toast, boolean force) {
        boolean enabled = enabled();
        boolean stormcallerBlocked = unlockedCount(player, SkillTab.CLASS_CATEGORY) > 0;
        boolean classBlocked = unlockedCount(player, SkillTab.STORMCALLER_CATEGORY) > 0;
        int bits = bits(enabled, stormcallerBlocked, classBlocked);
        Integer last = SENT.get(player.getUUID());
        if (!force && toast == ClassLockPayload.TOAST_NONE && last != null && last == bits) {
            return;
        }
        SENT.put(player.getUUID(), bits);
        PacketDistributor.sendToPlayer(player, new ClassLockPayload(enabled, stormcallerBlocked, classBlocked, toast));
    }

    private static int bits(boolean enabled, boolean stormcallerBlocked, boolean classBlocked) {
        return (enabled ? 1 : 0) | (stormcallerBlocked ? 2 : 0) | (classBlocked ? 4 : 0);
    }

    /** The tab on the other side of the lock, or null if the category takes no part in it. */
    @Nullable
    private static ResourceLocation other(ResourceLocation categoryId) {
        if (SkillTab.STORMCALLER_CATEGORY.equals(categoryId)) {
            return SkillTab.CLASS_CATEGORY;
        }
        if (SkillTab.CLASS_CATEGORY.equals(categoryId)) {
            return SkillTab.STORMCALLER_CATEGORY;
        }
        return null;
    }

    private static long unlockedCount(ServerPlayer player, ResourceLocation categoryId) {
        return SkillsAPI.getCategory(categoryId).map(category -> countUnlocked(category, player)).orElse(0L);
    }

    private static long countUnlocked(Category category, ServerPlayer player) {
        return category.streamUnlockedSkills(player).count();
    }
}
