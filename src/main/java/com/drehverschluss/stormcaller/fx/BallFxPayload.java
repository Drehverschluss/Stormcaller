package com.drehverschluss.stormcaller.fx;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: one visual of a ball lightning ({@code entity.BallLightningEntity}). The arcs follow the ball and the
 * target on the client, so the packet carries both entity ids; {@code pos} is the fallback when an entity is not known
 * (any more) on the client.
 *
 * @param kind     what to show
 * @param ballId   entity id of the ball
 * @param targetId entity id of the target, -1 if none
 * @param pos      ball center when the server sent it
 * @param seed     random seed for the zigzag, so every player sees the same shape
 */
public record BallFxPayload(Kind kind, int ballId, int targetId, Vec3 pos, int seed) implements CustomPacketPayload {
    public static final Type<BallFxPayload> TYPE = new Type<>(Stormcaller.id("ball_fx"));
    public static final StreamCodec<FriendlyByteBuf, BallFxPayload> STREAM_CODEC = StreamCodec.of(BallFxPayload::write, BallFxPayload::read);

    public enum Kind {
        /** Lightning from the ball to an enemy nearby. */
        ZAP,
        /** Strong discharge into an enemy the ball passes through. */
        DISCHARGE,
        /** End of the ball: short burst. */
        POP,
        /** The ball appears. */
        SPAWN
    }

    private static void write(FriendlyByteBuf buf, BallFxPayload p) {
        buf.writeByte(p.kind.ordinal());
        buf.writeVarInt(p.ballId);
        buf.writeVarInt(p.targetId + 1);
        buf.writeDouble(p.pos.x);
        buf.writeDouble(p.pos.y);
        buf.writeDouble(p.pos.z);
        buf.writeInt(p.seed);
    }

    private static BallFxPayload read(FriendlyByteBuf buf) {
        Kind[] kinds = Kind.values();
        int kindId = buf.readByte();
        Kind kind = kindId >= 0 && kindId < kinds.length ? kinds[kindId] : Kind.POP;
        int ball = buf.readVarInt();
        int target = buf.readVarInt() - 1;
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new BallFxPayload(kind, ball, target, pos, buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
