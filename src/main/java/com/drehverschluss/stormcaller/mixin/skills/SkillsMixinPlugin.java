package com.drehverschluss.stormcaller.mixin.skills;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the mixins of {@code stormcaller.skills.mixins.json} only when Pufferfish's Skills is installed (it is an
 * optional dependency, its classes are missing otherwise). Also notes which of the loading hooks were really applied:
 * the Class Skills branch is only added if all of them are there ({@link #hooksReady()}).
 */
public class SkillsMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> HOOKS = Set.of("ConfigReaderMixin", "PackConfigReaderAccessor", "SkillsModMixin");
    /** System properties: the plugin may live in another class loader than the game code that asks. */
    private static final String APPLIED = "stormcaller.skills.mixin.applied.";
    private boolean present;

    /** All mixins of the loading hook (Class Skills branch) are applied. */
    public static boolean hooksReady() {
        return HOOKS.stream().allMatch(name -> Boolean.getBoolean(APPLIED + name));
    }

    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList mods = LoadingModList.get();
        present = mods != null && mods.getModFileById("puffish_skills") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        System.setProperty(APPLIED + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1), "true");
    }
}
