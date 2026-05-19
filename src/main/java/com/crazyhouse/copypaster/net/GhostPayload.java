package com.crazyhouse.copypaster.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C paste preview: axis-aligned world bounds (handles rotation/mirror on server).
 * {@code active=false} clears the ghost.
 */
public record GhostPayload(boolean active,
                            int minX, int minY, int minZ,
                            int maxX, int maxY, int maxZ)
        implements CustomPacketPayload {

    public static final Type<GhostPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("copypaster", "ghost"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GhostPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeBoolean(value.active());
                        buf.writeInt(value.minX());
                        buf.writeInt(value.minY());
                        buf.writeInt(value.minZ());
                        buf.writeInt(value.maxX());
                        buf.writeInt(value.maxY());
                        buf.writeInt(value.maxZ());
                    },
                    buf -> new GhostPayload(
                            buf.readBoolean(),
                            buf.readInt(), buf.readInt(), buf.readInt(),
                            buf.readInt(), buf.readInt(), buf.readInt()
                    )
            );

    public static GhostPayload clear() {
        return new GhostPayload(false, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
