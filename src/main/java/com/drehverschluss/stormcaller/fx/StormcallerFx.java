package com.drehverschluss.stormcaller.fx;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server API for the lightning visuals of Stormcaller. Every call sends one small packet to the players near the
 * effect (view distance, at least 64 blocks); the client draws it. Calls on a client level do nothing.
 * <p>
 * Building blocks:
 * <ul>
 *     <li>{@link #arc}: jagged lightning arc between two points, see {@link ArcStyle}</li>
 *     <li>{@link #flash}: short bright flash with a few sparks</li>
 *     <li>{@link #impact}: flash plus a few short arcs that jump off the point</li>
 *     <li>{@link #strike}: vertical strike from the sky (style {@link ArcStyle#STRIKE}) plus a flash on the ground</li>
 *     <li>{@link #ring}: lightning ring spreading out from a point (Lightning Nova)</li>
 * </ul>
 */
public final class StormcallerFx {
    /** Minimum send radius in blocks. */
    public static final double MIN_RADIUS = 64.0;

    /** Set by the client mod class. Common code never references client classes. */
    private static Consumer<FxPayload> clientHandler = payload -> {};
    private static Consumer<BallFxPayload> ballHandler = payload -> {};

    private StormcallerFx() {}

    public static void arc(ServerLevel level, Vec3 from, Vec3 to, ArcStyle style) {
        arc(level, from, to, style, 1.0F);
    }

    /** @param scale width multiplier (1 = normal) */
    public static void arc(ServerLevel level, Vec3 from, Vec3 to, ArcStyle style, float scale) {
        send(level, from.add(to).scale(0.5), new FxPayload(FxPayload.Kind.ARC, from, to, style.ordinal(), scale, 0, seed(level)));
    }

    /** @param size radius of the flash in blocks (about 0.5 small, 1 normal, 2 big) */
    public static void flash(ServerLevel level, Vec3 pos, float size) {
        send(level, pos, new FxPayload(FxPayload.Kind.FLASH, pos, pos, 0, size, 0, seed(level)));
    }

    /**
     * Flash plus short arcs that jump off the point in random directions.
     *
     * @param size flash size, also scales the arc length
     * @param arcs number of arcs (0 to 8)
     */
    public static void impact(ServerLevel level, Vec3 pos, float size, int arcs) {
        send(level, pos, new FxPayload(FxPayload.Kind.IMPACT, pos, pos, 0, size, Math.max(0, Math.min(8, arcs)), seed(level)));
    }

    /**
     * Lightning strike from the sky.
     *
     * @param groundPos where the strike hits
     * @param height    start height above the ground (about 16)
     */
    public static void strike(ServerLevel level, Vec3 groundPos, float height) {
        send(level, groundPos, new FxPayload(FxPayload.Kind.STRIKE, groundPos, groundPos, ArcStyle.STRIKE.ordinal(), height, 0, seed(level)));
    }

    /**
     * Ring of lightning that spreads out from the center.
     *
     * @param radius final radius in blocks
     */
    public static void ring(ServerLevel level, Vec3 center, float radius) {
        send(level, center, new FxPayload(FxPayload.Kind.RING, center, center, 0, radius, 0, seed(level)));
    }

    /** Visual of a ball lightning (zap, discharge, pop), see {@link BallFxPayload}. */
    public static void ball(ServerLevel level, Vec3 pos, BallFxPayload payload) {
        double radius = Math.max(MIN_RADIUS, level.getServer().getPlayerList().getViewDistance() * 16.0);
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, payload);
    }

    /** Center of an entity's bounding box. */
    public static Vec3 center(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static int seed(ServerLevel level) {
        return level.getRandom().nextInt();
    }

    private static void send(ServerLevel level, Vec3 pos, FxPayload payload) {
        double radius = Math.max(MIN_RADIUS, level.getServer().getPlayerList().getViewDistance() * 16.0);
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, payload);
    }

    // ------------------------------------------------------------------------------------------------ network

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(FxPayload.TYPE, FxPayload.STREAM_CODEC, (payload, context) -> clientHandler.accept(payload))
                .playToClient(BallFxPayload.TYPE, BallFxPayload.STREAM_CODEC, (payload, context) -> ballHandler.accept(payload));
    }

    /** Called once by the client mod class. */
    public static void setClientHandler(Consumer<FxPayload> handler) {
        clientHandler = handler;
    }

    /** Called once by the client mod class. */
    public static void setBallHandler(Consumer<BallFxPayload> handler) {
        ballHandler = handler;
    }
}
