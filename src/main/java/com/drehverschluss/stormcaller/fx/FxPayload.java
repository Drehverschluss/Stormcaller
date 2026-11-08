package com.drehverschluss.stormcaller.fx;

import com.drehverschluss.stormcaller.Stormcaller;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: one lightning effect. The client builds the geometry itself, so the packet stays small.
 *
 * @param kind  what to show, see {@link Kind}
 * @param a     start of an arc, center of a flash, impact or ring, ground position of a strike
 * @param b     end of an arc (unused by the other kinds)
 * @param style {@link ArcStyle} ordinal (arcs only)
 * @param size  arc width scale, flash or impact size, ring radius, strike height
 * @param count number of arcs of an impact
 * @param seed  random seed for the zigzag, so every player sees the same shape
 */
public record FxPayload(Kind kind, Vec3 a, Vec3 b, int style, float size, int count, int seed) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(Stormcaller.id("fx"));
    public static final StreamCodec<FriendlyByteBuf, FxPayload> STREAM_CODEC = StreamCodec.of(FxPayload::write, FxPayload::read);

    public enum Kind { ARC, FLASH, IMPACT, STRIKE, RING }

    private static void write(FriendlyByteBuf buf, FxPayload p) {
        buf.writeByte(p.kind.ordinal());
        writeVec(buf, p.a);
        if (p.kind == Kind.ARC) {
            writeVec(buf, p.b);
            buf.writeByte(p.style);
        }
        buf.writeFloat(p.size);
        buf.writeByte(p.count);
        buf.writeInt(p.seed);
    }

    private static FxPayload read(FriendlyByteBuf buf) {
        Kind[] kinds = Kind.values();
        int kindId = buf.readByte();
        Kind kind = kindId >= 0 && kindId < kinds.length ? kinds[kindId] : Kind.FLASH;
        Vec3 a = readVec(buf);
        Vec3 b = a;
        int style = 0;
        if (kind == Kind.ARC) {
            b = readVec(buf);
            style = buf.readByte();
        }
        float size = buf.readFloat();
        int count = buf.readByte();
        int seed = buf.readInt();
        return new FxPayload(kind, a, b, style, size, count, seed);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
