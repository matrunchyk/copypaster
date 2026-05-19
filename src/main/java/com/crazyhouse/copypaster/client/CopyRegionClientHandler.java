package com.crazyhouse.copypaster.client;

import com.crazyhouse.copypaster.net.CopyRegionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class CopyRegionClientHandler {

    static void register() {
        if (!ClientPlayNetworking.registerGlobalReceiver(CopyRegionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handle(payload)))) {
            CopyPasterClientMod.LOGGER.error("Failed to register CopyRegionPayload receiver");
        }
    }

    private static void handle(CopyRegionPayload payload) {
        switch (payload.phase()) {
            case CopyRegionPayload.S2C_PENDING -> SelectionPreview.setPending(
                    payload.x1(), payload.y1(), payload.z1(),
                    payload.x2(), payload.y2(), payload.z2());
            case CopyRegionPayload.S2C_CLEAR -> SelectionPreview.clear();
            default -> { }
        }
    }

    private CopyRegionClientHandler() {}
}
