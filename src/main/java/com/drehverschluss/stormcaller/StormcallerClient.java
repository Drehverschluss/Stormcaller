package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.client.OriginalArmorPack;
import com.drehverschluss.stormcaller.client.StormCloudRenderer;
import com.drehverschluss.stormcaller.client.StormElementalRenderer;
import com.drehverschluss.stormcaller.client.StormcallerArmorRenderers;
import com.drehverschluss.stormcaller.entity.StormcallerEntities;
import com.drehverschluss.stormcaller.fx.client.FxClient;
import com.drehverschluss.stormcaller.fx.client.ShieldAura;
import com.drehverschluss.stormcaller.fx.client.ShockAura;
import com.drehverschluss.stormcaller.fx.client.ShockTooltips;
import com.drehverschluss.stormcaller.fx.client.StormbringerStaffRenderer;
import com.drehverschluss.stormcaller.fx.client.StormcallerClientConfig;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client setup. Forge 1.20.1 has no client-only {@code @Mod} class: {@link Stormcaller} calls {@link #init} through
 * {@code DistExecutor} on the client only, so this class never loads on a dedicated server.
 */
public final class StormcallerClient {
    private StormcallerClient() {}

    public static void init(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, StormcallerClientConfig.SPEC);
        modEventBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> {
            StormcallerArmorRenderers.register();
            // Spell Engine keeps these in plain maps: register on the main thread
            event.enqueueWork(ShockAura::register);
            // Stormbringer Staff: the hand renderer (1.21.1: RegisterClientExtensionsEvent)
            event.enqueueWork(StormbringerStaffRenderer::attach);
        });
        // Stormbringer Staff: 3D model with crystal arcs in the hand
        modEventBus.addListener(EventPriority.NORMAL, false, ModelEvent.RegisterAdditional.class, StormbringerStaffRenderer::registerModels);
        modEventBus.addListener(EventPriority.NORMAL, false, ModelEvent.ModifyBakingResult.class, StormbringerStaffRenderer::wrapModel);
        // The HUD of Stored Charge (StoredChargeHud) comes from the effect itself (StormcallerEffects.STORED_CHARGE)
        // the robe look of 0.1/0.2 as a built-in resource pack, off by default
        modEventBus.addListener(EventPriority.NORMAL, false, AddPackFindersEvent.class, OriginalArmorPack::onAddPackFinders);
        modEventBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(StormcallerEntities.STORM_CLOUD, StormCloudRenderer::new);
            event.registerEntityRenderer(StormcallerEntities.STORM_ELEMENTAL, StormElementalRenderer::new);
            // the static field draws itself through server visuals (ring and arcs)
            event.registerEntityRenderer(StormcallerEntities.STATIC_FIELD, net.minecraft.client.renderer.entity.NoopRenderer::new);
            // the ball lightning is drawn by fx.client.BallLightningFx
            event.registerEntityRenderer(StormcallerEntities.BALL_LIGHTNING, net.minecraft.client.renderer.entity.NoopRenderer::new);
        });
        ShockTooltips.register();
        FxClient.init();
        ShieldAura.register();
        // TODO BP5 (skill tree): ClassLockClient.init(); if (SkillTab.enabled()) SkillTreeClient.init();
    }
}
