package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.client.StormCloudRenderer;
import com.drehverschluss.stormcaller.client.StormElementalRenderer;
import com.drehverschluss.stormcaller.client.StormcallerArmorRenderers;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTab;
import com.drehverschluss.stormcaller.compat.skilltree.client.ClassLockClient;
import com.drehverschluss.stormcaller.compat.skilltree.client.SkillTreeClient;
import com.drehverschluss.stormcaller.entity.StormcallerEntities;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(StormcallerEntities.STORM_CLOUD, StormCloudRenderer::new);
            event.registerEntityRenderer(StormcallerEntities.STORM_ELEMENTAL, StormElementalRenderer::new);
        });
        ShockTooltips.register();
        FxClient.init();
        ShieldAura.register();
        ClassLockClient.init();
        if (SkillTab.enabled()) {
            SkillTreeClient.init();
        }
    }
}
