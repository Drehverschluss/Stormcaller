package com.drehverschluss.stormcaller;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

// This class will not load on dedicated servers.
@Mod(value = Stormcaller.MODID, dist = Dist.CLIENT)
public class StormcallerClient {
    public StormcallerClient() {
    }
}
