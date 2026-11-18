package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.compat.WizardMerchantTrades;
import com.drehverschluss.stormcaller.compat.WizardsTierOne;
import com.drehverschluss.stormcaller.compat.skilltree.ClassLockPayload;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTab;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTreeCompat;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTreePack;
import com.drehverschluss.stormcaller.compat.curios.CuriosCompat;
import com.drehverschluss.stormcaller.config.StormcallerConfig;
import com.drehverschluss.stormcaller.entity.StormcallerEntities;
import com.drehverschluss.stormcaller.fx.FxSpellHooks;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import com.drehverschluss.stormcaller.item.StormcallerItems;
import com.drehverschluss.stormcaller.shock.ShockHandler;
import com.drehverschluss.stormcaller.shock.StormcallerEffects;
import com.drehverschluss.stormcaller.spell.NearestTarget;
import com.drehverschluss.stormcaller.spell.SkillMechanics;
import com.drehverschluss.stormcaller.spell.StaticShield;
import com.drehverschluss.stormcaller.spell.StoredCharge;
import com.drehverschluss.stormcaller.trinket.BallLightning;
import com.drehverschluss.stormcaller.trinket.StormcallerTrinkets;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(Stormcaller.MODID)
public class Stormcaller {
    public static final String MODID = "stormcaller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Stormcaller() {
        // Forge 1.20.1: no constructor injection, the mod bus and the config registration come from the loading contexts
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, StormcallerConfig.registeredSpec());
        StormcallerAttributes.register(modEventBus);
        StormcallerEffects.register(modEventBus);
        StormcallerItems.init();
        StormcallerTrinkets.init();
        modEventBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, StormcallerItems::onRegister);
        modEventBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, StaticShield::onRegister);
        modEventBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, StormcallerTrinkets::onRegister);
        modEventBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, StormcallerEntities::onRegister);
        StaticShield.init();
        // Lightning Spell Power of the Wizards tier 1 equipment: written into the item modifiers of Spell Engine with the
        // defaults at setup, then again whenever the server config loads, reloads or its values change (WizardsTierOne)
        modEventBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, event -> event.enqueueWork(WizardsTierOne::apply));
        modEventBus.addListener(EventPriority.NORMAL, false, ModConfigEvent.Loading.class, WizardsTierOne::onConfig);
        modEventBus.addListener(EventPriority.NORMAL, false, ModConfigEvent.Reloading.class, WizardsTierOne::onConfig);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, WizardsTierOne::onTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, WizardsTierOne::onTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, VillagerTradesEvent.class, WizardMerchantTrades::onVillagerTrades);
        ShockHandler.register();
        StormcallerFx.registerPayloads();
        // class lock message: always registered (same message ids on client and server), like the payload on 1.21.1
        ClassLockPayload.register();
        FxSpellHooks.register();
        NearestTarget.init();
        BallLightning.init();
        StoredCharge.init();
        SkillMechanics.init();
        modEventBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class,
                event -> event.enqueueWork(SkillMechanics::commonSetup));
        if (SkillTab.enabled()) {
            // Stormcaller skill tab: built-in data pack, respec, class lock (Skill Tree classes only behind this check)
            modEventBus.addListener(EventPriority.NORMAL, false, AddPackFindersEvent.class, SkillTreePack::onAddPackFinders);
            SkillTreeCompat.init();
            modEventBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class,
                    event -> event.enqueueWork(SkillTreeCompat::commonSetup));
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StormcallerClient.init(modEventBus));
        LOGGER.info("Stormcaller {} loaded (rings, amulets and relics: {}, skill tab: {})",
                ModLoadingContext.get().getActiveContainer().getModInfo().getVersion(),
                CuriosCompat.isLoaded() ? "on" : "off, needs Curios API",
                SkillTab.enabled() ? "on" : "off, needs Skill Tree");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    /**
     * Fixed UUID for an attribute modifier (Forge 1.20.1 identifies modifiers by UUID, 1.21.1 by id). Derived from the
     * modifier id of the 1.21.1 version, so it is the same in every world and on every start.
     */
    public static UUID uuid(String modifierId) {
        return UUID.nameUUIDFromBytes((MODID + ":" + modifierId).getBytes(StandardCharsets.UTF_8));
    }
}
