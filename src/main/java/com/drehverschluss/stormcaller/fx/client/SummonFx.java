package com.drehverschluss.stormcaller.fx.client;

import com.drehverschluss.stormcaller.entity.StormCloudEntity;
import com.drehverschluss.stormcaller.entity.StormElementalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Client tick for the lightning visuals of Stormcaller entities: the random arcs of storm clouds ({@link StormCloudFx})
 * and the arcs on the body of the Storm Elemental.
 * <p>
 * Storm Elemental: a small arc that crawls over the body every {@link #ELEMENTAL_INTERVAL} ticks, sometimes a second one
 * in between, a bit wider and more frequent than the arcs of Static Shield (every 6 ticks). Nothing beyond
 * {@link #ELEMENTAL_MAX_DISTANCE} blocks. Uses the body arcs of {@link FxClient} (client config limits apply).
 */
final class SummonFx {
    private static final int ELEMENTAL_INTERVAL = 4;
    private static final double ELEMENTAL_MAX_DISTANCE = 48.0;
    private static final RandomSource RANDOM = RandomSource.create();

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
                elemental(elemental, camera);
            }
        }
        StormCloudFx.endTick(level);
    }

    private static void elemental(StormElementalEntity elemental, Entity camera) {
        if (elemental.isInvisible() || elemental.isRemoved() || elemental.distanceToSqr(camera) > ELEMENTAL_MAX_DISTANCE * ELEMENTAL_MAX_DISTANCE) {
            return;
        }
        int phase = Math.floorMod(elemental.tickCount + elemental.getId() * 3, ELEMENTAL_INTERVAL);
        boolean main = phase == 0;
        boolean extra = phase == ELEMENTAL_INTERVAL / 2 && RANDOM.nextFloat() < 0.35F;
        if (main || extra) {
            FxClient.addBodyArc(elemental, 0.25F, 0.95F, 1.0F, 1.25F, false, RANDOM);
        }
    }
}
