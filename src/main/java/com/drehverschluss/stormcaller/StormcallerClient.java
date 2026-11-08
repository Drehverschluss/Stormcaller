package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.client.StormcallerArmorRenderers;
import com.drehverschluss.stormcaller.fx.client.FxClient;
import com.drehverschluss.stormcaller.fx.client.ShieldAura;
import com.drehverschluss.stormcaller.fx.client.ShockAura;
import com.drehverschluss.stormcaller.fx.client.ShockTooltips;
import com.drehverschluss.stormcaller.fx.client.StormcallerClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// This class will not load on dedicated servers.
@Mod(value = Stormcaller.MODID, dist = Dist.CLIENT)
public class StormcallerClient {
    public StormcallerClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, StormcallerClientConfig.SPEC);
        modEventBus.addListener(FMLClientSetupEvent.class, event -> {
            StormcallerArmorRenderers.register();
            // Spell Engine keeps these in plain maps: register on the main thread
            event.enqueueWork(ShockAura::register);
        });
        ShockTooltips.register();
        FxClient.init();
        ShieldAura.register();
    }
}
