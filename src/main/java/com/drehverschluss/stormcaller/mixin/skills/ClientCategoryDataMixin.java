package com.drehverschluss.stormcaller.mixin.skills;

import com.drehverschluss.stormcaller.compat.skilltree.client.ClassLockClient;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.client.config.skill.ClientSkillConfig;
import net.puffish.skillsmod.client.data.ClientCategoryData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skill screen of Pufferfish's Skills: shows the roots that the class lock blocks
 * ({@code compat.skilltree.ClassLock}) as locked, so the player sees it before clicking. Only the look: the server
 * enforces the lock on its own. {@code require = 0}: if a later version of Pufferfish's Skills changes this method, the
 * roots just look normal again (a blocked click still gets undone, with a toast).
 */
@Mixin(value = ClientCategoryData.class, remap = false)
public abstract class ClientCategoryDataMixin {
    @Inject(method = "getSkillState", at = @At("RETURN"), cancellable = true, require = 0)
    private void stormcaller$classLock(ClientSkillConfig skill, CallbackInfoReturnable<Skill.State> cir) {
        Skill.State state = cir.getReturnValue();
        if (skill.isRoot() && (state == Skill.State.AVAILABLE || state == Skill.State.AFFORDABLE)
                && ClassLockClient.rootsBlocked(((ClientCategoryData) (Object) this).getConfig().id())) {
            cir.setReturnValue(Skill.State.LOCKED);
        }
    }
}
