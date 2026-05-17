package com.crazyhouse.copypaster.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * {@code [} — start corner, {@code ]} — end corner (order does not matter).
 * While selecting, the box follows the crosshair for the end corner.
 * {@code ]} with both corners set runs coord {@code /copy} (each press updates the region).
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
        } else {
            SelectionPreview.setAnchorEnd(pos);
            if (SelectionPreview.hasBothAnchors()) {
                submitCoordCopy(client);
            }
        }
    }

    private static void submitCoordCopy(Minecraft client) {
        BlockPos a = SelectionPreview.corner1();
        BlockPos b = SelectionPreview.corner2();
        if (a == null || b == null) return;
        SelectionPreview.lockEndCorner();
        client.player.connection.sendCommand(String.format("copy %d %d %d %d %d %d",
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
    }

    private KeyHandler() {}
}
