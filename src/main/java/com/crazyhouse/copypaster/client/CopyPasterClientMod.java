package com.crazyhouse.copypaster.client;

import com.crazyhouse.copypaster.net.CopyPasterNetworking;
import com.crazyhouse.copypaster.net.GhostPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class CopyPasterClientMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("copypaster-client");

    @Override
    public void onInitializeClient() {
        CopyPasterNetworking.registerPayloadTypes();
        CopyPasterConfig.load();

        if (!ClientPlayNetworking.registerGlobalReceiver(GhostPayload.TYPE, (payload, context) -> {
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
        })) {
            LOGGER.error("Failed to register GhostPayload receiver (already registered?)");
        }

        CopySelectionHandler.register();
        KeyHandler.register();
        GhostRenderer.register();

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("copypaster", "selection_preview"),
                SelectionHudOverlay.INSTANCE);
    }
}
