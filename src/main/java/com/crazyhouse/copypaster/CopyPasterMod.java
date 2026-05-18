package com.crazyhouse.copypaster;

import com.crazyhouse.copypaster.model.PendingCopy;
import com.crazyhouse.copypaster.model.UndoSnapshot;
import com.crazyhouse.copypaster.net.CopyPasterNetworking;
import com.crazyhouse.copypaster.net.CopySelectPayload;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionResult;
import com.crazyhouse.copypaster.service.StructureStorageService;
import com.crazyhouse.copypaster.web.CopyPasterServerConfig;
import com.crazyhouse.copypaster.web.StructureWebServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class CopyPasterMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("copypaster");
    public static final Map<UUID, PendingCopy> PENDING = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> SELECTING = new ConcurrentHashMap<>();
    public static final Map<String, UndoSnapshot> UNDOS = new ConcurrentHashMap<>();
    public static final long SESSION_TIMEOUT_MS = 60_000L;
    public static StructureStorageService STORAGE;
    private static StructureWebServer WEB_SERVER;

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    public void onInitialize() {
        Path dataDir = FabricLoader.getInstance().getGameDir().resolve("copypaster");
        STORAGE = new StructureStorageService(dataDir);
        CopyPasterServerConfig.load();

        CopyPasterNetworking.registerPayloadTypes();

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() && SELECTING.containsKey(player.getUUID())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        ServerPlayNetworking.registerGlobalReceiver(CopySelectPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                CopyPasterCommands.handleCopySelect(context.player(), payload)));

        CopyPasterCommands.register();

        // Intercept chat to collect structure name after /copy
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            PendingCopy pending = PENDING.get(sender.getUUID());
            if (pending == null) return true; // not waiting for a name, pass through

            String text = message.signedContent().trim();

            if (text.equalsIgnoreCase("cancel")) {
                PENDING.remove(sender.getUUID());
                CopyPasterCommands.sendSelectionClear(sender);
                sender.sendSystemMessage(Component.translatable("copypaster.message.copy_cancelled")
                    .withStyle(ChatFormatting.GREEN));
                return false;
            }

            if (System.currentTimeMillis() - pending.createdAt() > SESSION_TIMEOUT_MS) {
                PENDING.remove(sender.getUUID());
                CopyPasterCommands.sendSelectionClear(sender);
                sender.sendSystemMessage(Component.translatable("copypaster.message.copy_session_expired")
                    .withStyle(ChatFormatting.RED));
                return false;
            }

            if (!VALID_NAME.matcher(text).matches()) {
                sender.sendSystemMessage(Component.translatable("copypaster.message.invalid_name", text)
                    .withStyle(ChatFormatting.RED));
                return false;
            }

            PENDING.remove(sender.getUUID());
            final String name = text;
            try {
                STORAGE.save(sender, name, pending);
                CopyPasterCommands.sendSelectionClear(sender);
                sender.sendSystemMessage(Component.translatable("copypaster.message.structure_saved", name)
                    .withStyle(ChatFormatting.GREEN));
                LOGGER.info("[COPY] {} saved '{}'", sender.getName().getString(), name);
            } catch (Exception e) {
                sender.sendSystemMessage(Component.translatable("copypaster.message.failed_save", e.getMessage())
                    .withStyle(ChatFormatting.RED));
                LOGGER.error("Failed to save structure '{}': {}", name, e.getMessage(), e);
            }
            return false; // suppress broadcast
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            PENDING.remove(id);
            if (SELECTING.remove(id) != null) {
                CopyPasterCommands.sendSelectionClear(handler.getPlayer());
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!CopyPasterServerConfig.webEnabled()) return;
            try {
                WEB_SERVER = new StructureWebServer(STORAGE);
                WEB_SERVER.start(server);
            } catch (IOException e) {
                LOGGER.error("Failed to start CopyPaster web UI: {}", e.getMessage(), e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (WEB_SERVER != null) {
                WEB_SERVER.stop();
                WEB_SERVER = null;
            }
        });

        LOGGER.info("CopyPaster enabled.");
    }
}
