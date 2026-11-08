package com.drehverschluss.stormcaller;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Stormcaller.MODID)
public class Stormcaller {
    public static final String MODID = "stormcaller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Stormcaller(IEventBus modEventBus, ModContainer modContainer) {
    }
}
