package com.crazyhouse.copypaster.client;

import com.crazyhouse.copypaster.net.CopySelectPayload;
import com.crazyhouse.copypaster.service.StructureStorageService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

/**
 * Interactive /copy: attack sets corners, use cancels, live preview via {@link SelectionPreview}.
 */
@Environment(EnvType.CLIENT)
public final class CopySelectionHandler {

    private static volatile boolean serverSelecting = false;
    private static long ignoreAttacksUntilTick = -1;

    static void register() {
        if (!ClientPlayNetworking.registerGlobalReceiver(CopySelectPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleServerMessage(payload)))) {
            CopyPasterClientMod.LOGGER.error("Failed to register CopySelectPayload receiver (already registered?)");
        }

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!serverSelecting || !world.isClientSide()) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            if (client.player != player) return InteractionResult.PASS;
            onAttackBlock(pos);
            return InteractionResult.FAIL;
        });

        ClientTickEvents.END_CLIENT_TICK.register(CopySelectionHandler::clientTick);
    }

    private static void clientTick(Minecraft client) {
        SelectionPreview.tick(client);

        if (SelectionPreview.followsCrosshair()) {
            SelectionPreview.updateHover(SelectionPreview.resolveSolidHover(client));
        }

        if (!serverSelecting || client.player == null) return;

        if (client.screen == null && client.options.keyUse.consumeClick()) {
            cancel(client, true);
            return;
        }

        refreshActionBarHint(client);
    }

    private static void refreshActionBarHint(Minecraft client) {
        if (client.gui == null) return;
        Component hint = SelectionPreview.hasStart()
                ? Component.translatable("copypaster.hud.hint.corner2")
                : Component.translatable("copypaster.hud.hint.corner1");
        client.gui.setOverlayMessage(hint, false);
    }

    static boolean isActive() {
        return serverSelecting;
    }

    private static void handleServerMessage(CopySelectPayload payload) {
        switch (payload.phase()) {
            case CopySelectPayload.S2C_START -> start();
            case CopySelectPayload.S2C_CANCEL -> stop(false);
            case CopySelectPayload.S2C_PENDING -> {
                serverSelecting = false;
                ignoreAttacksUntilTick = -1;
                SelectionPreview.setPending(
                        payload.x1(), payload.y1(), payload.z1(),
                        payload.x2(), payload.y2(), payload.z2());
            }
            default -> { }
        }
    }

    private static void start() {
        serverSelecting = true;
        ignoreAttacksUntilTick = -1;
        SelectionPreview.beginSelecting();
        refreshActionBarHint(Minecraft.getInstance());
    }

    private static void stop(boolean notify) {
        serverSelecting = false;
        ignoreAttacksUntilTick = -1;
        SelectionPreview.clear();
        if (notify) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.sendSystemMessage(
                        Component.translatable("copypaster.overlay.selection_cancelled")
                                .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static void onAttackBlock(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (!serverSelecting || client.player == null || client.level == null) return;

        long tick = client.level.getGameTime();
        if (tick <= ignoreAttacksUntilTick) return;

        if (!SelectionPreview.hasStart()) {
            SelectionPreview.setAnchorStart(pos);
            ignoreAttacksUntilTick = tick + 4;
            refreshActionBarHint(client);
            return;
        }

        BlockPos start = SelectionPreview.corner1();
        if (start == null) return;

        BlockPos end = pos.immutable();
        int x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
        int x2 = end.getX(), y2 = end.getY(), z2 = end.getZ();
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        if (volume > StructureStorageService.MAX_VOLUME) {
            client.player.sendSystemMessage(
                    Component.translatable("copypaster.overlay.region_too_large",
                            volume, StructureStorageService.MAX_VOLUME)
                            .withStyle(ChatFormatting.RED));
            return;
        }

        serverSelecting = false;
        ignoreAttacksUntilTick = -1;
        SelectionPreview.setAnchorEnd(end);
        SelectionPreview.lockEndCorner();
        ClientPlayNetworking.send(CopySelectPayload.c2sComplete(x1, y1, z1, x2, y2, z2));
    }

    private static void cancel(Minecraft client, boolean sendToServer) {
        stop(true);
        if (sendToServer) {
            ClientPlayNetworking.send(CopySelectPayload.c2sCancel());
        }
    }

    private CopySelectionHandler() {}
}
