package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.Stormcaller;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to client: state of the class lock between the Stormcaller tab and the Class Skills tab of Skill Tree
 * ({@link ClassLock}), for the skill screen (grey root, tooltip line) and a toast after a blocked unlock.
 *
 * @param enabled            class lock switched on in the server config
 * @param stormcallerBlocked the player has a class in Class Skills, so the Stormcaller root is locked
 * @param classBlocked       the player follows the Stormcaller path, so the Class Skills roots are locked
 * @param toast              0 = none, 1 = Stormcaller root was blocked, 2 = a class root was blocked
 */
public record ClassLockPayload(boolean enabled, boolean stormcallerBlocked, boolean classBlocked, int toast)
        implements CustomPacketPayload {
    public static final Type<ClassLockPayload> TYPE = new Type<>(Stormcaller.id("class_lock"));
    public static final StreamCodec<FriendlyByteBuf, ClassLockPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.enabled);
                buf.writeBoolean(p.stormcallerBlocked);
                buf.writeBoolean(p.classBlocked);
                buf.writeByte(p.toast);
            },
            buf -> new ClassLockPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readByte()));

    public static final int TOAST_NONE = 0;
    public static final int TOAST_STORMCALLER = 1;
    public static final int TOAST_CLASS = 2;

    private static Consumer<ClassLockPayload> clientHandler = payload -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }

    /** Called once by the client mod class. */
    public static void setClientHandler(Consumer<ClassLockPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
