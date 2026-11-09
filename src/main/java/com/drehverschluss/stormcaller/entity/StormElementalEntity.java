package com.drehverschluss.stormcaller.entity;

import com.drehverschluss.stormcaller.fx.StormcallerFx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.entity.SummonedEntity;

/**
 * Storm Elemental: a hovering storm spirit summoned by the spell {@code stormcaller:storm_elemental}.
 * <p>
 * The behaviour (follow the owner, targets, lifespan, casting Lightning Bolt) comes completely from the spell JSON via
 * Spell Engine's {@link SummonedEntity}. This class only adds the lightning visuals on the server:
 * <ul>
 *     <li>appearing: a strike from the sky onto the spot, a flash at the core and a short ring,</li>
 *     <li>casting: a flash at the core when a spell is released ({@link #onSpellReleased}; Spell Engine calls it for
 *     every summon cast, while {@code SpellEvents.SPELL_CAST} only fires for players),</li>
 *     <li>vanishing (end of the lifespan or death): a flash and an impact with a few arcs.</li>
 * </ul>
 * The body is drawn by the client from cloud puffs ({@code client.StormElementalRenderer}, pose in
 * {@code fx.client.ElementalPose}); core, eyes and the arcs inside the body come from {@code fx.client.ElementalFx}.
 * It walks on the ground like the Frost Elemental (robust in caves, water and on stairs); the renderer lets the body
 * hover above it. Without an owner
 * ({@code /summon}) it has no behaviour and just stays, like an ownerless Frost Elemental.
 */
public class StormElementalEntity extends SummonedEntity {
    /** Height of the glowing core above the entity position, including the hover of the renderer. */
    public static final double CORE_HEIGHT = 1.3;
    private static final String SPAWN_FX_KEY = "StormcallerSpawnFx";

    private boolean spawnFxDone;
    private boolean vanishFxDone;

    // Client-only visual state, updated every client tick by fx.client.ElementalFx and read by the renderer.
    /** Arms reaching forward while casting, 0..1 (smoothed), with the value of the previous tick. */
    public float clientCast;
    public float clientCastPrev;
    /** Extra brightness of the core after a release, 0..1 (fades out), with the value of the previous tick. */
    public float clientCoreBoost;
    public float clientCoreBoostPrev;
    /** Tick at which the despawn phase was first seen (puffs drift apart), -1 before. */
    public int clientDespawnStart = -1;
    /** The current release animation was already turned into a core flash. */
    public boolean clientReleaseSeen;

    public StormElementalEntity(EntityType<? extends StormElementalEntity> type, Level level) {
        super(type, level);
    }

    public Vec3 corePosition() {
        return position().add(0.0, CORE_HEIGHT * getScale(), 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        if (!spawnFxDone && behaviour != null) {
            spawnFxDone = true;
            StormcallerFx.strike(server, position(), 14.0F);
            StormcallerFx.flash(server, corePosition(), 1.2F);
            StormcallerFx.ring(server, position().add(0.0, 0.15, 0.0), 1.8F);
        }
        if (isDespawning()) {
            vanish(server);
        }
    }

    @Override
    public void onSpellReleased(int variant, int durationTicks) {
        super.onSpellReleased(variant, durationTicks);
        if (level() instanceof ServerLevel server) {
            StormcallerFx.flash(server, corePosition(), 0.9F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            vanish(server);
        }
    }

    private void vanish(ServerLevel server) {
        if (vanishFxDone) {
            return;
        }
        vanishFxDone = true;
        StormcallerFx.flash(server, corePosition(), 1.6F);
        StormcallerFx.impact(server, corePosition(), 1.4F, 5);
    }

    /** It hovers: no footsteps. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean(SPAWN_FX_KEY, spawnFxDone);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        spawnFxDone = nbt.getBoolean(SPAWN_FX_KEY);
    }
}
