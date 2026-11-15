package com.drehverschluss.stormcaller.mixin.skills;

import com.drehverschluss.stormcaller.compat.skilltree.ClassBranchHook;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.puffish.skillsmod.api.config.ConfigContext;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.reader.ConfigReader;
import net.puffish.skillsmod.config.reader.PackConfigReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pufferfish's Skills reads every category of a data pack through this class, from the final resource manager.
 * Adds the Stormcaller branch to the Class Skills tab of Skill Tree and leaves out the own tab then ({@link ClassBranchHook}).
 * Both injections are in one class and required: if an update changes one of the methods, the whole class is not applied
 * and Stormcaller keeps its own tab (see {@link SkillsMixinPlugin#hooksReady()}).
 */
@Mixin(value = ConfigReader.class, remap = false)
public abstract class ConfigReaderMixin {
    @Inject(method = "readCategory", at = @At("RETURN"), cancellable = true, require = 1)
    private void stormcaller$extendCategory(String namespace, String id, int position, ConfigContext context,
                                            CallbackInfoReturnable<Result<CategoryConfig, Problem>> cir) {
        ConfigReader self = (ConfigReader) (Object) this;
        if (SkillsMixinPlugin.hooksReady() && self instanceof PackConfigReader pack) {
            cir.setReturnValue(ClassBranchHook.afterReadCategory(self, ((PackConfigReaderAccessor) pack).stormcaller$getResourceManager(),
                    namespace, id, position, context, cir.getReturnValue()));
        }
    }

    @Inject(method = "readCategories", at = @At("HEAD"), cancellable = true, require = 1)
    private void stormcaller$filterCategories(String namespace, List<String> ids, int position, ConfigContext context,
                                              CallbackInfoReturnable<Result<Map<ResourceLocation, CategoryConfig>, Problem>> cir) {
        if (!SkillsMixinPlugin.hooksReady()) {
            return;
        }
        List<String> filtered = ClassBranchHook.filterOwnTab(namespace, ids);
        if (filtered != ids) {
            cir.setReturnValue(((ConfigReader) (Object) this).readCategories(namespace, filtered, position, context));
        }
    }
}
