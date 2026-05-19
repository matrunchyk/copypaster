package com.crazyhouse.copypaster;

import com.crazyhouse.copypaster.model.PendingCopy;
import com.crazyhouse.copypaster.model.UndoSnapshot;
import com.crazyhouse.copypaster.net.CopyRegionPayload;
import com.crazyhouse.copypaster.net.GhostPayload;
import com.crazyhouse.copypaster.paste.PasteCommandTree;
import com.crazyhouse.copypaster.paste.PasteGeometry;
import com.crazyhouse.copypaster.paste.PasteOptions;
import com.crazyhouse.copypaster.paste.SkipAirStructureProcessor;
import com.crazyhouse.copypaster.service.StructureStorageService;
import com.crazyhouse.copypaster.web.CopyPasterServerConfig;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CopyPasterCommands {
    private static final Map<UUID, PasteOptions> PENDING_PASTE = new ConcurrentHashMap<>();

    private CopyPasterCommands() {}

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(PasteCommandTree.build()
                    .requires(src -> src.getEntity() instanceof ServerPlayer sp
                            && CopyPasterPermissions.has(sp, CopyPasterPermissions.PASTE)));

            dispatcher.register(Commands.literal("pasteundo")
                    .requires(src -> src.getEntity() instanceof ServerPlayer sp
                            && CopyPasterPermissions.has(sp, CopyPasterPermissions.PASTE_UNDO))
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

            dispatcher.register(Commands.literal("copylist")
                    .requires(src -> src.getEntity() instanceof ServerPlayer sp
                            && CopyPasterPermissions.has(sp, CopyPasterPermissions.COPY_LIST))
                    .executes(ctx -> doList((ServerPlayer) ctx.getSource().getEntity())));

            dispatcher.register(Commands.literal("copyinfo")
                    .requires(src -> src.getEntity() instanceof ServerPlayer sp
                            && CopyPasterPermissions.has(sp, CopyPasterPermissions.COPY_INFO))
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

            dispatcher.register(Commands.literal("copyweb")
                    .requires(src -> src.getEntity() instanceof ServerPlayer sp
                            && CopyPasterPermissions.has(sp, CopyPasterPermissions.COPY_WEB))
                    .executes(ctx -> doCopyWeb((ServerPlayer) ctx.getSource().getEntity())));

            dispatcher.register(Commands.literal("copydelete")
                    .requires(src -> src.getEntity() instanceof ServerPlayer sp
                            && CopyPasterPermissions.has(sp, CopyPasterPermissions.COPY_DELETE))
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

    static void handleCopyRegion(ServerPlayer player, CopyRegionPayload payload) {
        if (payload.phase() != CopyRegionPayload.C2S_REQUEST) return;
        if (!CopyPasterPermissions.has(player, CopyPasterPermissions.COPY)) {
            player.sendSystemMessage(Component.translatable("copypaster.message.no_permission_copy")
                    .withStyle(ChatFormatting.RED));
            sendSelectionClear(player);
            return;
        }
        doCopy(player, payload.x1(), payload.y1(), payload.z1(),
                payload.x2(), payload.y2(), payload.z2());
    }

    static int doCopy(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (CopyPasterMod.PENDING.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("copypaster.message.already_waiting_name")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

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

    public static int runPaste(ServerPlayer player, PasteOptions options) {
        if (options.confirm()) {
            PasteOptions pending = PENDING_PASTE.remove(player.getUUID());
            if (pending != null && pending.name().equals(options.name())) {
                options = pending.withConfirm();
            }
        } else {
            PENDING_PASTE.put(player.getUUID(), options);
        }

        StructureStorageService storage = CopyPasterMod.STORAGE;
        String name = options.name();

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
            BlockPos pasteOrigin = resolvePasteOrigin(player, meta, options);
            Rotation rotation = options.rotation();
            Mirror mirror = options.mirror();

            AABB bounds = PasteGeometry.worldBounds(
                    pasteOrigin, meta.sizeX(), meta.sizeY(), meta.sizeZ(), rotation, mirror);

            if (!options.confirm()) {
                int nonAir = storage.countNonAirInBox(level, bounds);
                if (nonAir > 0) {
                    sendGhost(player, bounds);
                    player.sendSystemMessage(Component.translatable("copypaster.message.overwrite_warning",
                                    nonAir, buildConfirmHint(name, options))
                            .withStyle(ChatFormatting.RED));
                    return 0;
                }
            }

            clearGhost(player);
            PENDING_PASTE.remove(player.getUUID());

            List<UndoSnapshot.BlockSnapshot> snaps = storage.captureRegionInBox(level, bounds);
            String pasteId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CopyPasterMod.UNDOS.put(pasteId, new UndoSnapshot(
                    pasteId, player.getUUID(), player.getName().getString(), name, snaps));

            StructureTemplate template = storage.loadTemplate(name, level.getServer().registryAccess());
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(mirror)
                    .setIgnoreEntities(false);
            if (options.skipAir()) {
                settings.addProcessor(SkipAirStructureProcessor.INSTANCE);
            }
            template.placeInWorld(level, pasteOrigin, pasteOrigin, settings, RandomSource.create(), 2);

            int px = pasteOrigin.getX(), py = pasteOrigin.getY(), pz = pasteOrigin.getZ();
            String coords = px + "," + py + "," + pz;
            player.sendSystemMessage(Component.translatable("copypaster.message.pasted", name, coords, pasteId)
                    .withStyle(ChatFormatting.GREEN));

            CopyPasterMod.LOGGER.info("[PASTE] {} pasted '{}' at ({},{},{}) id={}{}",
                    player.getName().getString(), name, px, py, pz, pasteId,
                    options.confirm() ? " (confirm)" : "");
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("copypaster.message.failed_paste", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            CopyPasterMod.LOGGER.error("Paste '{}' failed: {}", name, e.getMessage(), e);
            return 0;
        }
        return 1;
    }

    private static BlockPos resolvePasteOrigin(ServerPlayer player, StructureStorageService.StructureInfo meta,
            PasteOptions options) {
        if (options.usesAbsoluteOrigin()) {
            return options.absoluteOrigin();
        }
        BlockPos playerPos = player.blockPosition();
        return new BlockPos(
                playerPos.getX() - meta.offsetX(),
                playerPos.getY() - meta.offsetY(),
                playerPos.getZ() - meta.offsetZ()
        );
    }

    private static Component buildConfirmHint(String name, PasteOptions options) {
        StringBuilder cmd = new StringBuilder("/paste ").append(name);
        if (options.usesAbsoluteOrigin()) {
            BlockPos at = options.absoluteOrigin();
            cmd.append(" at ").append(at.getX()).append(' ').append(at.getY()).append(' ').append(at.getZ());
        }
        if (options.rotation() != Rotation.NONE) {
            int deg = switch (options.rotation()) {
                case CLOCKWISE_90 -> 90;
                case CLOCKWISE_180 -> 180;
                case COUNTERCLOCKWISE_90 -> 270;
                default -> 0;
            };
            if (deg != 0) cmd.append(" rotate ").append(deg);
        }
        if (options.mirror() == Mirror.LEFT_RIGHT) {
            cmd.append(" mirror left_right");
        } else if (options.mirror() == Mirror.FRONT_BACK) {
            cmd.append(" mirror front_back");
        }
        if (options.skipAir()) cmd.append(" noair");
        cmd.append(" confirm");
        return Component.literal(cmd.toString()).withStyle(ChatFormatting.WHITE);
    }

    private static void sendGhost(ServerPlayer player, AABB bounds) {
        if (!ServerPlayNetworking.canSend(player, GhostPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new GhostPayload(
                true,
                (int) bounds.minX, (int) bounds.minY, (int) bounds.minZ,
                (int) bounds.maxX, (int) bounds.maxY, (int) bounds.maxZ));
    }

    private static void clearGhost(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, GhostPayload.TYPE)) {
            ServerPlayNetworking.send(player, GhostPayload.clear());
        }
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

    private static int doCopyWeb(ServerPlayer player) {
        if (!CopyPasterServerConfig.webEnabled()) {
            player.sendSystemMessage(Component.translatable("copypaster.message.web_disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("copypaster.message.web_url",
                        Component.literal(CopyPasterServerConfig.webUrlHint()).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.translatable("copypaster.message.web_token_hint")
                .withStyle(ChatFormatting.GRAY));
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
        if (ServerPlayNetworking.canSend(player, CopyRegionPayload.TYPE)) {
            ServerPlayNetworking.send(player, CopyRegionPayload.s2cClear());
        }
    }

    private static void sendSelectionPending(ServerPlayer player,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (ServerPlayNetworking.canSend(player, CopyRegionPayload.TYPE)) {
            ServerPlayNetworking.send(player, CopyRegionPayload.s2cPending(
                    minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static MutableComponent row(String labelKey, String val) {
        return Component.translatable(labelKey).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(val).withStyle(ChatFormatting.WHITE));
    }
}
