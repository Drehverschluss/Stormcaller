package com.drehverschluss.stormcaller.mixin.skills;

import net.minecraft.server.packs.resources.ResourceManager;
import net.puffish.skillsmod.config.reader.PackConfigReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The resource manager Pufferfish's Skills reads a data pack from (final data after all data packs and KubeJS). */
@Mixin(value = PackConfigReader.class, remap = false)
public interface PackConfigReaderAccessor {
    @Accessor("resourceManager")
    ResourceManager stormcaller$getResourceManager();
}
