package com.drehverschluss.stormcaller.fx.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Short glows of the cloud puffs of an entity (Thunderstorm cloud, Storm Elemental): every arc or flash that starts in
 * or touches the puffs adds a glow at a world position, and the renderers light the puffs around it up for a few
 * ticks. "Hide Lightning Flashes" dims the glow.
 */
public final class PuffGlows {
    private static final int GLOW_TICKS = 5;

    private record Glow(Vec3 pos, float strength, float range, long tick) {}

    private static final Map<Integer, List<Glow>> GLOWS = new HashMap<>();
    private static long clientTick;

    private PuffGlows() {}

    /**
     * @param range distance in blocks at which the glow has faded out
     */
    public static void add(int entityId, Vec3 pos, float strength, float range) {
        GLOWS.computeIfAbsent(entityId, k -> new ArrayList<>()).add(new Glow(pos, strength, range, clientTick));
    }

    /** Brightness added to a puff of the entity at {@code pos} (0 = none, about 1 = right at a fresh flash). */
    public static float glowAt(int entityId, Vec3 pos, float partialTick) {
        List<Glow> glows = GLOWS.get(entityId);
        if (glows == null || glows.isEmpty()) {
            return 0.0F;
        }
        float total = 0.0F;
        for (Glow glow : glows) {
            float age = (clientTick - glow.tick()) + partialTick;
            float fade = Mth.clamp(1.0F - age / GLOW_TICKS, 0.0F, 1.0F);
            if (fade <= 0.0F) {
                continue;
            }
            double distance = glow.pos().distanceTo(pos);
            float near = (float) Mth.clamp(1.0 - distance / glow.range(), 0.0, 1.0);
            total += glow.strength() * fade * near * near;
        }
        float scale = Minecraft.getInstance().options.hideLightningFlash().get() ? 0.35F : 1.0F;
        return Math.min(1.0F, total) * scale;
    }

    static void clear() {
        GLOWS.clear();
    }

    /** End of the client tick: forget old glows and entities that are gone. */
    static void endTick(ClientLevel level) {
        clientTick++;
        Iterator<Map.Entry<Integer, List<Glow>>> it = GLOWS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Glow>> entry = it.next();
            entry.getValue().removeIf(glow -> clientTick - glow.tick() > GLOW_TICKS);
            if (entry.getValue().isEmpty() || level.getEntity(entry.getKey()) == null) {
                it.remove();
            }
        }
    }
}
