package com.drehverschluss.stormcaller.compat.skilltree;

import com.drehverschluss.stormcaller.fx.StormcallerFx;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server to client: state of the class lock between the Stormcaller tab and the Class Skills tab of Skill Tree
 * ({@link ClassLock}), for the skill screen (grey root, tooltip line) and a toast after a blocked unlock.
 * <p>
 * Forge 1.20.1: a message of the Stormcaller channel ({@link StormcallerFx#CHANNEL}) instead of a 1.21.1 payload. It is
 * registered always (like the payload on 1.21.1), so the message ids are the same on client and server.
 *
 * @param enabled            class lock switched on in the server config
 * @param stormcallerBlocked the player has a class in Class Skills, so the Stormcaller root is locked
 * @param classBlocked       the player follows the Stormcaller path, so the Class Skills roots are locked
 * @param classTree          the Stormcaller branch is in the Class Skills tab (no own tab, no class lock)
 * @param toast              0 = none, 1 = Stormcaller root was blocked, 2 = a class root was blocked
 */
public record ClassLockPayload(boolean enabled, boolean stormcallerBlocked, boolean classBlocked, boolean classTree, int toast) {
    public static final int TOAST_NONE = 0;
    public static final int TOAST_STORMCALLER = 1;
    public static final int TOAST_CLASS = 2;

    private static Consumer<ClassLockPayload> clientHandler = payload -> {};

    /** Called once from the mod constructor, after {@link StormcallerFx#registerPayloads}. */
    public static void register() {
        StormcallerFx.CHANNEL.messageBuilder(ClassLockPayload.class, StormcallerFx.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClassLockPayload::write).decoder(ClassLockPayload::read)
                .consumerMainThread((payload, context) -> clientHandler.accept(payload)).add();
    }

    public static void send(ServerPlayer player, ClassLockPayload payload) {
        StormcallerFx.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static void write(ClassLockPayload p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.enabled);
        buf.writeBoolean(p.stormcallerBlocked);
        buf.writeBoolean(p.classBlocked);
        buf.writeBoolean(p.classTree);
        buf.writeByte(p.toast);
    }

    private static ClassLockPayload read(FriendlyByteBuf buf) {
        return new ClassLockPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readByte());
    }

    /** Called once by the client mod class. */
    public static void setClientHandler(Consumer<ClassLockPayload> handler) {
        clientHandler = handler;
    }
}
