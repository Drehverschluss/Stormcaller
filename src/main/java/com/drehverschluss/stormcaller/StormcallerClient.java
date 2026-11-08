package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.client.StormcallerArmorRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// This class will not load on dedicated servers.
@Mod(value = Stormcaller.MODID, dist = Dist.CLIENT)
public class StormcallerClient {
    public StormcallerClient(IEventBus modEventBus) {
        modEventBus.addListener(FMLClientSetupEvent.class, event -> StormcallerArmorRenderers.register());
    }
}
