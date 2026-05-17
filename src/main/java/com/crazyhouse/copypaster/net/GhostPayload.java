package com.crazyhouse.copypaster.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C packet that tells the client where a /paste will land.
 * Sent when the player runs /paste name (no confirm) and there are
 * blocks to overwrite.  active=false clears the ghost on the client.
 *
 * Must match the client-side copy in copy_paster_client exactly.
 */
public record GhostPayload(boolean active,
                            int originX, int originY, int originZ,
                            int sizeX, int sizeY, int sizeZ)
        implements CustomPacketPayload {

    public static final Type<GhostPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("copypaster", "ghost"));

    /** StreamCodec<RegistryFriendlyByteBuf, GhostPayload> — encoder is (buf, value). */
    public static final StreamCodec<RegistryFriendlyByteBuf, GhostPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeBoolean(value.active());
                        buf.writeInt(value.originX());
                        buf.writeInt(value.originY());
                        buf.writeInt(value.originZ());
                        buf.writeInt(value.sizeX());
                        buf.writeInt(value.sizeY());
                        buf.writeInt(value.sizeZ());
                    },
                    buf -> new GhostPayload(
                            buf.readBoolean(),
                            buf.readInt(), buf.readInt(), buf.readInt(),
                            buf.readInt(), buf.readInt(), buf.readInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
