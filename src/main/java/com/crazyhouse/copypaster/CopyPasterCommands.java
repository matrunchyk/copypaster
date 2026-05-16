package com.crazyhouse.copypaster;

import com.crazyhouse.copypaster.model.PendingCopy;
import com.crazyhouse.copypaster.model.UndoSnapshot;
import com.crazyhouse.copypaster.net.GhostPayload;
import com.crazyhouse.copypaster.service.StructureStorageService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.util.RandomSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public final class CopyPasterCommands {
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private CopyPasterCommands() {}

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /copy <x1> <y1> <z1> <x2> <y2> <z2>
            dispatcher.register(Commands.literal("copy")
                .requires(CopyPasterCommands::isOp)
                .then(Commands.argument("x1", IntegerArgumentType.integer())
                .then(Commands.argument("y1", IntegerArgumentType.integer())
                .then(Commands.argument("z1", IntegerArgumentType.integer())
                .then(Commands.argument("x2", IntegerArgumentType.integer())
                .then(Commands.argument("y2", IntegerArgumentType.integer())
                .then(Commands.argument("z2", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
                        int y1 = IntegerArgumentType.getInteger(ctx, "y1");
                        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
                        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
                        int y2 = IntegerArgumentType.getInteger(ctx, "y2");
                        int z2 = IntegerArgumentType.getInteger(ctx, "z2");
                        return doCopy((ServerPlayer) ctx.getSource().getEntity(), x1, y1, z1, x2, y2, z2);
                    }))))))));

            // /paste <name>  /paste <name> confirm
            dispatcher.register(Commands.literal("paste")
                .requires(CopyPasterCommands::isOp)
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        CopyPasterMod.STORAGE.listNames().stream()
                            .filter(n -> n.startsWith(builder.getRemaining()))
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> doPaste(
                        (ServerPlayer) ctx.getSource().getEntity(),
                        StringArgumentType.getString(ctx, "name"), false))
                    .then(Commands.literal("confirm")
                        .executes(ctx -> doPaste(
                            (ServerPlayer) ctx.getSource().getEntity(),
                            StringArgumentType.getString(ctx, "name"), true)))));

            // /pasteundo <id>
            dispatcher.register(Commands.literal("pasteundo")
                .requires(CopyPasterCommands::isOp)
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        CopyPasterMod.UNDOS.keySet().stream()
                            .filter(id -> id.startsWith(builder.getRemaining()))
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> doUndo(
                        (ServerPlayer) ctx.getSource().getEntity(),
                        StringArgumentType.getString(ctx, "id")))));

            // /copylist
            dispatcher.register(Commands.literal("copylist")
                .requires(CopyPasterCommands::isOp)
                .executes(ctx -> doList((ServerPlayer) ctx.getSource().getEntity())));

            // /copyinfo <name>
            dispatcher.register(Commands.literal("copyinfo")
                .requires(CopyPasterCommands::isOp)
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        CopyPasterMod.STORAGE.listNames().stream()
                            .filter(n -> n.startsWith(builder.getRemaining()))
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> doInfo(
                        (ServerPlayer) ctx.getSource().getEntity(),
                        StringArgumentType.getString(ctx, "name")))));

            // /copydelete <name>
            dispatcher.register(Commands.literal("copydelete")
                .requires(CopyPasterCommands::isOp)
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        CopyPasterMod.STORAGE.listNames().stream()
                            .filter(n -> n.startsWith(builder.getRemaining()))
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> doDelete(
                        (ServerPlayer) ctx.getSource().getEntity(),
                        StringArgumentType.getString(ctx, "name")))));
        });
    }

    // ── Command implementations ───────────────────────────────────────────────

    private static int doCopy(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;

        if (volume > CopyPasterMod.STORAGE.maxVolume()) {
            player.sendSystemMessage(Component.literal(
                "Region too large: " + volume + " blocks. Max: " + CopyPasterMod.STORAGE.maxVolume()
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos corner1 = new BlockPos(minX, minY, minZ);
        BlockPos playerPos = player.blockPosition();
        BlockPos offset = new BlockPos(
            playerPos.getX() - corner1.getX(),
            playerPos.getY() - corner1.getY(),
            playerPos.getZ() - corner1.getZ()
        );

        CopyPasterMod.PENDING.put(player.getUUID(), new PendingCopy(
            player.getUUID(),
            ((ServerLevel) player.level()).dimension(),
            corner1,
            new BlockPos(maxX, maxY, maxZ),
            sizeX, sizeY, sizeZ,
            offset,
            System.currentTimeMillis()
        ));

        player.sendSystemMessage(row("Selected: ",
            player.level().dimension().toString() +
            " (" + minX + "," + minY + "," + minZ + ") → (" + maxX + "," + maxY + "," + maxZ + ")" +
            "  " + sizeX + "×" + sizeY + "×" + sizeZ + " = " + volume));
        player.sendSystemMessage(row("Offset:   ",
            "x=" + offset.getX() + " y=" + offset.getY() + " z=" + offset.getZ()));
        player.sendSystemMessage(Component.literal("Type the structure name in chat, or type ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("cancel").withStyle(ChatFormatting.RED))
            .append(Component.literal(".").withStyle(ChatFormatting.YELLOW)));
        return 1;
    }

    private static int doPaste(ServerPlayer player, String name, boolean confirm) {
        StructureStorageService storage = CopyPasterMod.STORAGE;

        if (!storage.metaExists(name)) {
            player.sendSystemMessage(Component.literal(
                "No structure '" + name + "'. Use /copylist to see available structures."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!storage.nbtExists(name)) {
            player.sendSystemMessage(Component.literal(
                "NBT file missing for '" + name + "'."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        try {
            StructureStorageService.StructureInfo meta = storage.loadMeta(name);
            ServerLevel level = (ServerLevel) player.level();
            BlockPos playerPos = player.blockPosition();

            int pasteX = playerPos.getX() - meta.offsetX();
            int pasteY = playerPos.getY() - meta.offsetY();
            int pasteZ = playerPos.getZ() - meta.offsetZ();
            BlockPos pasteOrigin = new BlockPos(pasteX, pasteY, pasteZ);

            // Overwrite warning
            if (!confirm) {
                int nonAir = storage.countNonAir(level, pasteOrigin, meta.sizeX(), meta.sizeY(), meta.sizeZ());
                if (nonAir > 0) {
                    // Send ghost preview to any copy_paster_client installed on the player's machine
                    ServerPlayNetworking.send(player, new GhostPayload(
                            true,
                            pasteX, pasteY, pasteZ,
                            meta.sizeX(), meta.sizeY(), meta.sizeZ()));
                    player.sendSystemMessage(Component.literal(
                        nonAir + " non-air blocks would be overwritten. Run "
                    ).withStyle(ChatFormatting.RED)
                    .append(Component.literal("/paste " + name + " confirm").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" to proceed.").withStyle(ChatFormatting.RED)));
                    return 0;
                }
            }

            // Clear any ghost that was shown for a previous /paste preview
            ServerPlayNetworking.send(player, new GhostPayload(false, 0, 0, 0, 0, 0, 0));

            // Capture undo snapshot
            List<UndoSnapshot.BlockSnapshot> snaps = storage.captureRegion(
                level, pasteOrigin, meta.sizeX(), meta.sizeY(), meta.sizeZ());
            String pasteId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CopyPasterMod.UNDOS.put(pasteId, new UndoSnapshot(
                pasteId, player.getUUID(), player.getName().getString(), name, snaps));

            // Place structure
            StructureTemplate template = storage.loadTemplate(name,
                ((net.minecraft.server.MinecraftServer) player.level().getServer()).registryAccess());
            StructurePlaceSettings settings = new StructurePlaceSettings();
            template.placeInWorld(level, pasteOrigin, pasteOrigin, settings, RandomSource.create(), 2);

            player.sendSystemMessage(Component.literal(
                "Pasted '" + name + "' at (" + pasteX + "," + pasteY + "," + pasteZ + "). Undo ID: "
            ).withStyle(ChatFormatting.GREEN)
            .append(Component.literal(pasteId).withStyle(ChatFormatting.WHITE)));

            CopyPasterMod.LOGGER.info("[PASTE] {} pasted '{}' at ({},{},{}) id={}{}",
                player.getName().getString(), name, pasteX, pasteY, pasteZ, pasteId,
                confirm ? " (confirm)" : "");
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Failed to paste: " + e.getMessage()).withStyle(ChatFormatting.RED));
            CopyPasterMod.LOGGER.error("Paste '{}' failed: {}", name, e.getMessage(), e);
            return 0;
        }
        return 1;
    }

    private static int doUndo(ServerPlayer player, String pasteId) {
        UndoSnapshot snap = CopyPasterMod.UNDOS.remove(pasteId);
        if (snap == null) {
            player.sendSystemMessage(Component.literal(
                "No undo record for '" + pasteId + "'. Undo history is lost on server restart."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        CopyPasterMod.STORAGE.restoreRegion(level, snap);
        player.sendSystemMessage(Component.literal(
            "Undone — restored " + snap.blocks().size() + " blocks (was paste of '" + snap.structureName() + "')."
        ).withStyle(ChatFormatting.GREEN));
        CopyPasterMod.LOGGER.info("[UNDO] {} undid paste {} ('{}' by {})",
            player.getName().getString(), pasteId, snap.structureName(), snap.playerName());
        return 1;
    }

    private static int doList(ServerPlayer player) {
        List<StructureStorageService.StructureInfo> list = CopyPasterMod.STORAGE.listAll();
        if (list.isEmpty()) {
            player.sendSystemMessage(Component.literal("No saved structures.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }
        player.sendSystemMessage(Component.literal("Saved structures:").withStyle(ChatFormatting.YELLOW));
        for (StructureStorageService.StructureInfo m : list) {
            String date = m.createdAt().length() >= 10 ? m.createdAt().substring(0, 10) : m.createdAt();
            player.sendSystemMessage(Component.literal("  " + m.name())
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("  " + m.sizeX() + "×" + m.sizeY() + "×" + m.sizeZ()
                    + "  by " + m.creatorName() + "  " + date).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int doInfo(ServerPlayer player, String name) {
        if (!CopyPasterMod.STORAGE.metaExists(name)) {
            player.sendSystemMessage(Component.literal("No metadata for '" + name + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            StructureStorageService.StructureInfo m = CopyPasterMod.STORAGE.loadMeta(name);
            player.sendSystemMessage(Component.literal("── " + m.name() + " ──").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(row("Size:    ", m.sizeX() + "×" + m.sizeY() + "×" + m.sizeZ()));
            player.sendSystemMessage(row("Dim:     ", m.dimension()));
            player.sendSystemMessage(row("Offset:  ", m.offsetX() + ", " + m.offsetY() + ", " + m.offsetZ()));
            player.sendSystemMessage(row("Creator: ", m.creatorName()));
            player.sendSystemMessage(row("Saved:   ", m.createdAt()));
            player.sendSystemMessage(row("NBT:     ", CopyPasterMod.STORAGE.nbtExists(name) ? "yes" : "MISSING"));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Failed to load info: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int doDelete(ServerPlayer player, String name) {
        if (!CopyPasterMod.STORAGE.nbtExists(name) && !CopyPasterMod.STORAGE.metaExists(name)) {
            player.sendSystemMessage(Component.literal("No structure '" + name + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            CopyPasterMod.STORAGE.delete(name);
            player.sendSystemMessage(Component.literal("Deleted '" + name + "'.").withStyle(ChatFormatting.GREEN));
            CopyPasterMod.LOGGER.info("[DELETE] {} deleted '{}'", player.getName().getString(), name);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Failed to delete: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isOp(net.minecraft.commands.CommandSourceStack source) {
        Entity e = source.getEntity();
        return e instanceof ServerPlayer sp &&
               source.getServer().getPlayerList().isOp(
                   new NameAndId(sp.getUUID(), sp.getName().getString()));
    }

    private static MutableComponent row(String label, String val) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
            .append(Component.literal(val).withStyle(ChatFormatting.WHITE));
    }
}
