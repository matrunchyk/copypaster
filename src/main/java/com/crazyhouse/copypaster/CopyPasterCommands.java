package com.crazyhouse.copypaster;

import com.crazyhouse.copypaster.model.PendingCopy;
import com.crazyhouse.copypaster.model.UndoSnapshot;
import com.crazyhouse.copypaster.net.CopySelectPayload;
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

import java.util.List;
import java.util.UUID;

public final class CopyPasterCommands {
    private CopyPasterCommands() {}

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("copy")
                .requires(CopyPasterCommands::isOp)
                .executes(ctx -> startInteractiveCopy((ServerPlayer) ctx.getSource().getEntity()))
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

    static void handleCopySelect(ServerPlayer player, CopySelectPayload payload) {
        if (!isOpPlayer(player)) return;

        if (isSelectionExpired(player)) return;

        switch (payload.phase()) {
            case CopySelectPayload.C2S_COMPLETE -> {
                if (CopyPasterMod.SELECTING.remove(player.getUUID()) == null) {
                    player.sendSystemMessage(Component.translatable("copypaster.message.no_active_selection")
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                doCopy(player, payload.x1(), payload.y1(), payload.z1(),
                        payload.x2(), payload.y2(), payload.z2());
            }
            case CopySelectPayload.C2S_CANCEL -> {
                CopyPasterMod.SELECTING.remove(player.getUUID());
                sendSelectionClear(player);
                player.sendSystemMessage(Component.translatable("copypaster.message.copy_selection_cancelled")
                    .withStyle(ChatFormatting.YELLOW));
            }
            default -> { }
        }
    }

    private static boolean isSelectionExpired(ServerPlayer player) {
        Long started = CopyPasterMod.SELECTING.get(player.getUUID());
        if (started == null) return false;
        if (System.currentTimeMillis() - started <= CopyPasterMod.SESSION_TIMEOUT_MS) return false;

        CopyPasterMod.SELECTING.remove(player.getUUID());
        if (ServerPlayNetworking.canSend(player, CopySelectPayload.TYPE)) {
            ServerPlayNetworking.send(player, CopySelectPayload.s2cCancel());
        }
        player.sendSystemMessage(Component.translatable("copypaster.message.copy_selection_expired")
            .withStyle(ChatFormatting.RED));
        return true;
    }

    private static int startInteractiveCopy(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, CopySelectPayload.TYPE)) {
            player.sendSystemMessage(Component.translatable("copypaster.message.interactive_requires_client",
                    Component.literal("/copy x1 y1 z1 x2 y2 z2").withStyle(ChatFormatting.WHITE),
                    Component.literal(".").withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (CopyPasterMod.PENDING.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("copypaster.message.already_waiting_name")
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (CopyPasterMod.SELECTING.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("copypaster.message.selection_in_progress")
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        CopyPasterMod.SELECTING.put(player.getUUID(), System.currentTimeMillis());
        ServerPlayNetworking.send(player, CopySelectPayload.s2cStart());
        return 1;
    }

    private static int doCopy(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;

        if (volume > CopyPasterMod.STORAGE.maxVolume()) {
            player.sendSystemMessage(Component.translatable("copypaster.message.region_too_large",
                    String.valueOf(volume), String.valueOf(CopyPasterMod.STORAGE.maxVolume()))
                .withStyle(ChatFormatting.RED));
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

        sendSelectionPending(player, minX, minY, minZ, maxX, maxY, maxZ);
        player.sendSystemMessage(Component.translatable("copypaster.message.name_prompt",
                Component.translatable("copypaster.word.cancel").withStyle(ChatFormatting.RED))
            .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int doPaste(ServerPlayer player, String name, boolean confirm) {
        StructureStorageService storage = CopyPasterMod.STORAGE;

        if (!storage.metaExists(name)) {
            player.sendSystemMessage(Component.translatable("copypaster.message.no_structure", name)
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!storage.nbtExists(name)) {
            player.sendSystemMessage(Component.translatable("copypaster.message.nbt_missing", name)
                .withStyle(ChatFormatting.RED));
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

            if (!confirm) {
                int nonAir = storage.countNonAir(level, pasteOrigin, meta.sizeX(), meta.sizeY(), meta.sizeZ());
                if (nonAir > 0) {
                    ServerPlayNetworking.send(player, new GhostPayload(
                            true,
                            pasteX, pasteY, pasteZ,
                            meta.sizeX(), meta.sizeY(), meta.sizeZ()));
                    player.sendSystemMessage(Component.translatable("copypaster.message.overwrite_warning",
                            nonAir,
                            Component.literal("/paste " + name + " confirm").withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.RED));
                    return 0;
                }
            }

            ServerPlayNetworking.send(player, new GhostPayload(false, 0, 0, 0, 0, 0, 0));

            List<UndoSnapshot.BlockSnapshot> snaps = storage.captureRegion(
                level, pasteOrigin, meta.sizeX(), meta.sizeY(), meta.sizeZ());
            String pasteId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CopyPasterMod.UNDOS.put(pasteId, new UndoSnapshot(
                pasteId, player.getUUID(), player.getName().getString(), name, snaps));

            StructureTemplate template = storage.loadTemplate(name,
                ((net.minecraft.server.MinecraftServer) player.level().getServer()).registryAccess());
            StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false);
            template.placeInWorld(level, pasteOrigin, pasteOrigin, settings, RandomSource.create(), 2);

            String coords = pasteX + "," + pasteY + "," + pasteZ;
            player.sendSystemMessage(Component.translatable("copypaster.message.pasted", name, coords, pasteId)
                .withStyle(ChatFormatting.GREEN));

            CopyPasterMod.LOGGER.info("[PASTE] {} pasted '{}' at ({},{},{}) id={}{}",
                player.getName().getString(), name, pasteX, pasteY, pasteZ, pasteId,
                confirm ? " (confirm)" : "");
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("copypaster.message.failed_paste", e.getMessage())
                .withStyle(ChatFormatting.RED));
            CopyPasterMod.LOGGER.error("Paste '{}' failed: {}", name, e.getMessage(), e);
            return 0;
        }
        return 1;
    }

    private static int doUndo(ServerPlayer player, String pasteId) {
        UndoSnapshot snap = CopyPasterMod.UNDOS.remove(pasteId);
        if (snap == null) {
            player.sendSystemMessage(Component.translatable("copypaster.message.no_undo", pasteId)
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        CopyPasterMod.STORAGE.restoreRegion(level, snap);
        player.sendSystemMessage(Component.translatable("copypaster.message.undone",
                snap.blocks().size(), snap.structureName())
            .withStyle(ChatFormatting.GREEN));
        CopyPasterMod.LOGGER.info("[UNDO] {} undid paste {} ('{}' by {})",
            player.getName().getString(), pasteId, snap.structureName(), snap.playerName());
        return 1;
    }

    private static int doList(ServerPlayer player) {
        List<StructureStorageService.StructureInfo> list = CopyPasterMod.STORAGE.listAll();
        if (list.isEmpty()) {
            player.sendSystemMessage(Component.translatable("copypaster.message.no_saved_structures")
                .withStyle(ChatFormatting.YELLOW));
            return 1;
        }
        player.sendSystemMessage(Component.translatable("copypaster.message.saved_structures_header")
            .withStyle(ChatFormatting.YELLOW));
        for (StructureStorageService.StructureInfo m : list) {
            String date = m.createdAt().length() >= 10 ? m.createdAt().substring(0, 10) : m.createdAt();
            player.sendSystemMessage(Component.literal("  " + m.name())
                .withStyle(ChatFormatting.WHITE)
                .append(Component.translatable("copypaster.message.list_entry_detail",
                        m.sizeX(), m.sizeY(), m.sizeZ(), m.creatorName(), date)
                    .withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int doInfo(ServerPlayer player, String name) {
        if (!CopyPasterMod.STORAGE.metaExists(name)) {
            player.sendSystemMessage(Component.translatable("copypaster.message.no_metadata", name)
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            StructureStorageService.StructureInfo m = CopyPasterMod.STORAGE.loadMeta(name);
            player.sendSystemMessage(Component.translatable("copypaster.message.info_header", m.name())
                .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(row("copypaster.label.size",
                m.sizeX() + "×" + m.sizeY() + "×" + m.sizeZ()));
            player.sendSystemMessage(row("copypaster.label.dim", m.dimension()));
            player.sendSystemMessage(row("copypaster.label.offset",
                m.offsetX() + ", " + m.offsetY() + ", " + m.offsetZ()));
            player.sendSystemMessage(row("copypaster.label.creator", m.creatorName()));
            player.sendSystemMessage(row("copypaster.label.saved", m.createdAt()));
            String nbtKey = CopyPasterMod.STORAGE.nbtExists(name)
                ? "copypaster.word.yes" : "copypaster.word.missing";
            player.sendSystemMessage(Component.translatable("copypaster.label.nbt").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(nbtKey).withStyle(ChatFormatting.WHITE)));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("copypaster.message.failed_load_info", e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int doDelete(ServerPlayer player, String name) {
        if (!CopyPasterMod.STORAGE.nbtExists(name) && !CopyPasterMod.STORAGE.metaExists(name)) {
            player.sendSystemMessage(Component.translatable("copypaster.message.no_structure_short", name)
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            CopyPasterMod.STORAGE.delete(name);
            player.sendSystemMessage(Component.translatable("copypaster.message.deleted", name)
                .withStyle(ChatFormatting.GREEN));
            CopyPasterMod.LOGGER.info("[DELETE] {} deleted '{}'", player.getName().getString(), name);
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("copypaster.message.failed_delete", e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    static void sendSelectionClear(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, CopySelectPayload.TYPE)) {
            ServerPlayNetworking.send(player, CopySelectPayload.s2cCancel());
        }
    }

    private static void sendSelectionPending(ServerPlayer player,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (ServerPlayNetworking.canSend(player, CopySelectPayload.TYPE)) {
            ServerPlayNetworking.send(player, CopySelectPayload.s2cPending(
                    minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static boolean isOp(net.minecraft.commands.CommandSourceStack source) {
        Entity e = source.getEntity();
        return e instanceof ServerPlayer sp && isOpPlayer(sp);
    }

    private static boolean isOpPlayer(ServerPlayer player) {
        return player.level().getServer().getPlayerList().isOp(
            new NameAndId(player.getUUID(), player.getName().getString()));
    }

    private static MutableComponent row(String labelKey, String val) {
        return Component.translatable(labelKey).withStyle(ChatFormatting.GRAY)
            .append(Component.literal(val).withStyle(ChatFormatting.WHITE));
    }
}
