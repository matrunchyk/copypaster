package com.crazyhouse.copypaster.client;

import com.crazyhouse.copypaster.net.GhostPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class CopyPasterClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // PayloadTypeRegistry is handled in CopyPasterMod.onInitialize() which runs on both sides.

        ClientPlayNetworking.registerGlobalReceiver(GhostPayload.TYPE, (payload, context) -> {
            if (payload.active()) {
                GhostRenderer.ghostActive = true;
                GhostRenderer.ghostX  = payload.originX();
                GhostRenderer.ghostY  = payload.originY();
                GhostRenderer.ghostZ  = payload.originZ();
                GhostRenderer.ghostSX = payload.sizeX();
                GhostRenderer.ghostSY = payload.sizeY();
                GhostRenderer.ghostSZ = payload.sizeZ();
            } else {
                GhostRenderer.ghostActive = false;
            }
        });

        KeyHandler.register();
        GhostRenderer.register();
    }
}
