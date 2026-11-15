package com.drehverschluss.stormcaller.mixin.skills;

import com.drehverschluss.stormcaller.compat.skilltree.ClassBranchHook;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Start of every load of Pufferfish's Skills (server start, /reload): resets the state of {@link ClassBranchHook}. */
@Mixin(value = SkillsMod.class, remap = false)
public abstract class SkillsModMixin {
    @Inject(method = "loadModConfig", at = @At("HEAD"), require = 1)
    private void stormcaller$loadStart(MinecraftServer server, CallbackInfo ci) {
        ClassBranchHook.onLoadStart();
    }
}
