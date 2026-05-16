package com.crazyhouse.copypaster;

import com.crazyhouse.copypaster.model.PendingCopy;
import com.crazyhouse.copypaster.model.UndoSnapshot;
import com.crazyhouse.copypaster.net.GhostPayload;
import com.crazyhouse.copypaster.service.StructureStorageService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class CopyPasterMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("copypaster");
    public static final Map<UUID, PendingCopy> PENDING = new ConcurrentHashMap<>();
    public static final Map<String, UndoSnapshot> UNDOS = new ConcurrentHashMap<>();
    public static StructureStorageService STORAGE;

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    public void onInitialize() {
        Path dataDir = FabricLoader.getInstance().getGameDir().resolve("copypaster");
        STORAGE = new StructureStorageService(dataDir);

        // Register the S2C ghost packet (used by copy_paster_client to preview paste location)
        PayloadTypeRegistry.clientboundPlay().register(GhostPayload.TYPE, GhostPayload.CODEC);

        CopyPasterCommands.register();

        // Intercept chat to collect structure name after /copy
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            PendingCopy pending = PENDING.get(sender.getUUID());
            if (pending == null) return true; // not waiting for a name, pass through

            String text = message.signedContent().trim();

            if (text.equalsIgnoreCase("cancel")) {
                PENDING.remove(sender.getUUID());
                sender.sendSystemMessage(Component.literal("Copy cancelled.").withStyle(ChatFormatting.GREEN));
                return false;
            }

            if (System.currentTimeMillis() - pending.createdAt() > 60_000L) {
                PENDING.remove(sender.getUUID());
                sender.sendSystemMessage(Component.literal(
                    "Copy session expired. Run /copy again.").withStyle(ChatFormatting.RED));
                return false;
            }

            if (!VALID_NAME.matcher(text).matches()) {
                sender.sendSystemMessage(Component.literal(
                    "Invalid name '" + text + "'. Use a-z A-Z 0-9 _ - (max 64). Try again or type cancel."
                ).withStyle(ChatFormatting.RED));
                return false;
            }

            PENDING.remove(sender.getUUID());
            final String name = text;
            try {
                STORAGE.save(sender, name, pending);
                sender.sendSystemMessage(Component.literal(
                    "Structure '" + name + "' saved.").withStyle(ChatFormatting.GREEN));
                LOGGER.info("[COPY] {} saved '{}'", sender.getName().getString(), name);
            } catch (Exception e) {
                sender.sendSystemMessage(Component.literal(
                    "Failed to save: " + e.getMessage()).withStyle(ChatFormatting.RED));
                LOGGER.error("Failed to save structure '{}': {}", name, e.getMessage(), e);
            }
            return false; // suppress broadcast
        });

        // Remove pending copy when player disconnects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            PENDING.remove(handler.getPlayer().getUUID())
        );

        LOGGER.info("CopyPaster enabled.");
    }
}
