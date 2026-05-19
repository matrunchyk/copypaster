package com.crazyhouse.copypaster.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Keybind copy region: C2S request corners; S2C pending/clear for HUD sync.
 */
public record CopyRegionPayload(byte phase,
                                int x1, int y1, int z1,
                                int x2, int y2, int z2)
        implements CustomPacketPayload {

    public static final byte C2S_REQUEST = 0;
    public static final byte S2C_PENDING = 1;
    public static final byte S2C_CLEAR = 2;

    public static final Type<CopyRegionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("copypaster", "copy_region"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopyRegionPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeByte(value.phase());
                        buf.writeInt(value.x1());
                        buf.writeInt(value.y1());
                        buf.writeInt(value.z1());
                        buf.writeInt(value.x2());
                        buf.writeInt(value.y2());
                        buf.writeInt(value.z2());
                    },
                    buf -> new CopyRegionPayload(
                            buf.readByte(),
                            buf.readInt(), buf.readInt(), buf.readInt(),
                            buf.readInt(), buf.readInt(), buf.readInt()
                    )
            );

    public static CopyRegionPayload c2sRequest(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CopyRegionPayload(C2S_REQUEST, x1, y1, z1, x2, y2, z2);
    }

    public static CopyRegionPayload s2cPending(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CopyRegionPayload(S2C_PENDING, x1, y1, z1, x2, y2, z2);
    }

    public static CopyRegionPayload s2cClear() {
        return new CopyRegionPayload(S2C_CLEAR, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
