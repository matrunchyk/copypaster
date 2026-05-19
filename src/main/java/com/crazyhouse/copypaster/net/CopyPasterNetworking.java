package com.crazyhouse.copypaster.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers custom payload codecs once per JVM. Must run before play networking receivers
 * are bound (client entrypoint can run before the common mod initializer).
 */
public final class CopyPasterNetworking {

    private static boolean registered;

    private CopyPasterNetworking() {}

    public static synchronized void registerPayloadTypes() {
        if (registered) return;

        PayloadTypeRegistry.clientboundPlay().register(GhostPayload.TYPE, GhostPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CopyRegionPayload.TYPE, CopyRegionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CopyRegionPayload.TYPE, CopyRegionPayload.CODEC);

        registered = true;
    }
}
