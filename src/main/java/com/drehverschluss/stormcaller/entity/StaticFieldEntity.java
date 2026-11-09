package com.drehverschluss.stormcaller.entity;

import com.drehverschluss.stormcaller.fx.ArcStyle;
import com.drehverschluss.stormcaller.fx.StormcallerFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.entity.SpellCloud;

/**
 * Static field on the ground (Static Trap, Charged Ground): a Spell Engine {@link SpellCloud} (data, timing and impacts
 * from the spell) that only adds visuals, sent from the server with the lightning building blocks of {@link StormcallerFx}:
 * a flat ring of the field radius every {@link #RING_TICKS} ticks and a few small arcs close to the ground in between.
 * Nothing is rendered on the client besides that.
 * <p>
 * Spells use it with {@code "entity_type_id": "stormcaller:static_field"} in a CLOUD delivery.
 */
public class StaticFieldEntity extends SpellCloud {
    private static final int RING_TICKS = 10;
    private static final int ARC_TICKS = 4;
    private static final float ARC_SCALE = 0.55F;

    public StaticFieldEntity(EntityType<? extends SpellCloud> type, Level level) {
        super(type, level);
    }

    /** Radius of the field in blocks (Spell Engine syncs it as the bounding box width). */
    public float radius() {
        return getBbWidth() * 0.5F;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || !(level() instanceof ServerLevel server) || isDespawning()) {
            return;
        }
        if (tickCount == 1) {
            refreshDimensions();
        }
        Vec3 ground = position().add(0.0, 0.08, 0.0);
        if (tickCount % RING_TICKS == 1) {
            StormcallerFx.ring(server, ground, radius());
        }
        if (tickCount % ARC_TICKS == 2) {
            RandomSource random = server.getRandom();
            float r = radius();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double from = r * (0.2 + 0.6 * random.nextDouble());
            double turn = angle + (random.nextDouble() - 0.5) * 1.6;
            double to = r * (0.3 + 0.65 * random.nextDouble());
            Vec3 a = ground.add(Math.cos(angle) * from, 0.05, Math.sin(angle) * from);
            Vec3 b = ground.add(Math.cos(turn) * to, 0.05 + random.nextDouble() * 0.3, Math.sin(turn) * to);
            StormcallerFx.arc(server, a, b, ArcStyle.CHAIN, ARC_SCALE);
        }
    }
}
