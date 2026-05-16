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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * [ — set Pos1 to the block the crosshair is aimed at (shows orange corner cube)
 * ] — set Pos2; once both are set, sends /copy automatically (shows yellow box)
 *
 * The server responds with "type a structure name in chat", same as always.
 */
@Environment(EnvType.CLIENT)
public final class KeyHandler {

    private static final KeyMapping KEY_POS1 = new KeyMapping(
            "key.copypaster.pos1", GLFW.GLFW_KEY_LEFT_BRACKET,  KeyMapping.Category.MISC);
    private static final KeyMapping KEY_POS2 = new KeyMapping(
            "key.copypaster.pos2", GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC);

    static void register() {
        KeyMappingHelper.registerKeyMapping(KEY_POS1);
        KeyMappingHelper.registerKeyMapping(KEY_POS2);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KEY_POS1.consumeClick()) handleKey(client, 1);
            while (KEY_POS2.consumeClick()) handleKey(client, 2);
        });
    }

    private static void handleKey(Minecraft client, int which) {
        if (client.player == null || client.level == null) return;

        if (!(client.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            client.player.sendOverlayMessage(
                    Component.literal("Look at a block first.").withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos pos = bhr.getBlockPos();

        if (which == 1) {
            GhostRenderer.selPos1 = pos;
            GhostRenderer.selPos2 = null;
            client.player.sendOverlayMessage(
                    Component.literal("Pos1 → " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
                             .withStyle(ChatFormatting.YELLOW));
        } else {
            if (GhostRenderer.selPos1 == null) {
                client.player.sendOverlayMessage(
                        Component.literal("Set Pos1 first ( [ key ).").withStyle(ChatFormatting.RED));
                return;
            }
            GhostRenderer.selPos2 = pos;
            BlockPos p1 = GhostRenderer.selPos1;
            client.player.sendOverlayMessage(
                    Component.literal("Pos2 → " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                            + "  — sending /copy …").withStyle(ChatFormatting.YELLOW));
            client.player.connection.sendCommand(String.format("copy %d %d %d %d %d %d",
                    p1.getX(), p1.getY(), p1.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private KeyHandler() {}
}
