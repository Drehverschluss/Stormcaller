package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.compat.WizardsTierOne;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import com.drehverschluss.stormcaller.shock.ShockHandler;
import com.drehverschluss.stormcaller.shock.StormcallerEffects;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(Stormcaller.MODID)
public class Stormcaller {
    public static final String MODID = "stormcaller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Stormcaller(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, StormcallerConfig.SPEC);
        StormcallerAttributes.register(modEventBus);
        StormcallerEffects.register(modEventBus);
        StormcallerItems.init();
        modEventBus.addListener(RegisterEvent.class, StormcallerItems::onRegister);
        NeoForge.EVENT_BUS.addListener(ItemAttributeModifierEvent.class, WizardsTierOne::onItemAttributeModifiers);
        ShockHandler.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
