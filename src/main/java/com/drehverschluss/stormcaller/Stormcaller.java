package com.drehverschluss.stormcaller;

import com.drehverschluss.stormcaller.attribute.StormcallerAttributes;
import com.drehverschluss.stormcaller.compat.WizardsTierOne;
import com.drehverschluss.stormcaller.compat.skilltree.ClassLockPayload;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTab;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTreeCompat;
import com.drehverschluss.stormcaller.compat.skilltree.SkillTreePack;
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
import com.drehverschluss.stormcaller.trinket.BallLightning;
import com.drehverschluss.stormcaller.trinket.StormcallerTrinkets;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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
        StormcallerTrinkets.init();
        modEventBus.addListener(RegisterEvent.class, StormcallerItems::onRegister);
        modEventBus.addListener(RegisterEvent.class, StaticShield::onRegister);
        modEventBus.addListener(RegisterEvent.class, StormcallerTrinkets::onRegister);
        modEventBus.addListener(RegisterEvent.class, StormcallerEntities::onRegister);
        StaticShield.init();
        NeoForge.EVENT_BUS.addListener(ItemAttributeModifierEvent.class, WizardsTierOne::onItemAttributeModifiers);
        ShockHandler.register();
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, StormcallerFx::registerPayloads);
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, ClassLockPayload::register);
        FxSpellHooks.register();
        NearestTarget.init();
        BallLightning.init();
        SkillMechanics.init();
        if (SkillTab.enabled()) {
            // Stormcaller skill tab: built-in data pack, respec, class lock (Skill Tree classes only behind this check)
            modEventBus.addListener(AddPackFindersEvent.class, SkillTreePack::onAddPackFinders);
            SkillTreeCompat.init();
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
