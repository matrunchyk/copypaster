package com.crazyhouse.copypaster.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bidirectional copy-selection session control.
 *
 * <p>S2C: {@link #S2C_START}, {@link #S2C_CANCEL}, {@link #S2C_PENDING}
 * <p>C2S: {@link #C2S_COMPLETE}, {@link #C2S_CANCEL}
 */
public record CopySelectPayload(byte phase,
                                int x1, int y1, int z1,
                                int x2, int y2, int z2)
        implements CustomPacketPayload {

    public static final byte S2C_START = 0;
    public static final byte S2C_CANCEL = 1;
    public static final byte C2S_COMPLETE = 2;
    public static final byte C2S_CANCEL = 3;
    /** Region fixed; waiting for structure name in chat. */
    public static final byte S2C_PENDING = 4;

    public static final Type<CopySelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("copypaster", "copy_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopySelectPayload> CODEC =
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
                    buf -> new CopySelectPayload(
                            buf.readByte(),
                            buf.readInt(), buf.readInt(), buf.readInt(),
                            buf.readInt(), buf.readInt(), buf.readInt()
                    )
            );

    public static CopySelectPayload s2cStart() {
        return new CopySelectPayload(S2C_START, 0, 0, 0, 0, 0, 0);
    }

    public static CopySelectPayload s2cCancel() {
        return new CopySelectPayload(S2C_CANCEL, 0, 0, 0, 0, 0, 0);
    }

    public static CopySelectPayload s2cPending(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CopySelectPayload(S2C_PENDING, x1, y1, z1, x2, y2, z2);
    }

    public static CopySelectPayload c2sComplete(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CopySelectPayload(C2S_COMPLETE, x1, y1, z1, x2, y2, z2);
    }

    public static CopySelectPayload c2sCancel() {
        return new CopySelectPayload(C2S_CANCEL, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
