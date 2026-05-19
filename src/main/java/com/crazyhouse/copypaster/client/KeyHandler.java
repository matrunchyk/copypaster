package com.crazyhouse.copypaster.client;

import com.crazyhouse.copypaster.net.CopyRegionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * {@code [} — corner 1, {@code ]} — corner 2 and send region to server for naming in chat.
 */
@Environment(EnvType.CLIENT)
public final class KeyHandler {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("copypaster", "copypaster"));

    private static final KeyMapping KEY_START = new KeyMapping(
            "key.copypaster.pos1", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            CATEGORY);
    private static final KeyMapping KEY_END = new KeyMapping(
            "key.copypaster.pos2", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    static void register() {
        KeyMappingHelper.registerKeyMapping(KEY_START);
        KeyMappingHelper.registerKeyMapping(KEY_END);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null && SelectionPreview.isSelecting()) {
                SelectionPreview.updateHover(SelectionPreview.resolveSolidHover(client));
                SelectionPreview.tick(client);
            }
            while (KEY_START.consumeClick()) handleKey(client, true);
            while (KEY_END.consumeClick()) handleKey(client, false);
        });
    }

    private static void handleKey(Minecraft client, boolean startCorner) {
        if (client.player == null || client.level == null) return;
        if (SelectionPreview.phase() == SelectionPreview.Phase.PENDING) return;

        BlockPos pos = SelectionPreview.resolveSolidHover(client);
        if (pos == null) {
            client.player.sendSystemMessage(
                    Component.translatable("copypaster.overlay.look_at_block")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        if (startCorner) {
            SelectionPreview.setAnchorStart(pos);
            client.player.sendSystemMessage(
                    Component.translatable("copypaster.overlay.pos1",
                                    pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN));
        } else {
            if (!SelectionPreview.hasStart()) {
                client.player.sendSystemMessage(
                        Component.translatable("copypaster.overlay.set_pos1_first")
                                .withStyle(ChatFormatting.RED));
                return;
            }
            SelectionPreview.setAnchorEnd(pos);
            client.player.sendSystemMessage(
                    Component.translatable("copypaster.overlay.pos2_sending",
                                    pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN));
            submitRegion(client);
        }
    }

    private static void submitRegion(Minecraft client) {
        BlockPos a = SelectionPreview.corner1();
        BlockPos b = SelectionPreview.corner2();
        if (a == null || b == null) return;

        if (!ClientPlayNetworking.canSend(CopyRegionPayload.TYPE)) {
            client.player.sendSystemMessage(
                    Component.translatable("copypaster.message.client_not_connected")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        ClientPlayNetworking.send(CopyRegionPayload.c2sRequest(
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
    }

    private KeyHandler() {}
}
