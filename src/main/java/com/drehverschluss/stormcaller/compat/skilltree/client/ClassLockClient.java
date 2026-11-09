package com.drehverschluss.stormcaller.compat.skilltree.client;

import com.drehverschluss.stormcaller.Stormcaller;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTab;
import com.drehverschluss.stormcaller.compat.skilltree.ClassLockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the class lock: the last state the server sent ({@link ClassLockPayload}), read by the skill screen
 * mixin and the root tooltip, and the toast after a blocked unlock. Free of Skill Tree and Pufferfish's Skills classes.
 */
public final class ClassLockClient {
    private static volatile ClassLockPayload state = new ClassLockPayload(false, false, false, ClassLockPayload.TOAST_NONE);

    private ClassLockClient() {}

    public static void init() {
        ClassLockPayload.setClientHandler(ClassLockClient::accept);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> state = new ClassLockPayload(false, false, false, ClassLockPayload.TOAST_NONE));
    }

    private static void accept(ClassLockPayload payload) {
        state = payload;
        if (payload.toast() != ClassLockPayload.TOAST_NONE) {
            Minecraft mc = Minecraft.getInstance();
            String body = payload.toast() == ClassLockPayload.TOAST_STORMCALLER ? "stormcaller" : "class";
            mc.execute(() -> SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("skill." + Stormcaller.MODID + ".class_lock.toast.title"),
                    Component.translatable("skill." + Stormcaller.MODID + ".class_lock.toast." + body)));
        }
    }

    public static boolean enabled() {
        return state.enabled();
    }

    public static boolean stormcallerBlocked() {
        return state.enabled() && state.stormcallerBlocked();
    }

    /** Should the roots of this skill category be shown as locked? */
    public static boolean rootsBlocked(ResourceLocation categoryId) {
        ClassLockPayload s = state;
        if (!s.enabled()) {
            return false;
        }
        if (SkillTab.STORMCALLER_CATEGORY.equals(categoryId)) {
            return s.stormcallerBlocked();
        }
        if (SkillTab.CLASS_CATEGORY.equals(categoryId)) {
            return s.classBlocked();
        }
        return false;
    }
}
