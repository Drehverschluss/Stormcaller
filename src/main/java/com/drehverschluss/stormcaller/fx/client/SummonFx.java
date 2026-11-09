package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.entity.StormCloudEntity;
import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Client tick for the lightning visuals of Stormcaller entities: the random arcs of storm clouds ({@link StormCloudFx}),
 * the state, arcs and glows of the Storm Elemental ({@link ElementalFx}) and the end of the puff glows
 * ({@link PuffGlows}).
 */
final class SummonFx {
    private SummonFx() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        if (level == null || camera == null || mc.isPaused()) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof StormCloudEntity cloud) {
                StormCloudFx.tickCloud(cloud, Math.sqrt(cloud.distanceToSqr(camera)));
            } else if (entity instanceof StormElementalEntity elemental) {
                ElementalFx.tick(elemental, Math.sqrt(elemental.distanceToSqr(camera)));
            }
        }
        PuffGlows.endTick(level);
    }
}
