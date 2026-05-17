package com.crazyhouse.copypaster.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for copy-region preview (interactive /copy, keybind corners, pending name).
 */
@Environment(EnvType.CLIENT)
public final class SelectionPreview {

    public enum Phase { NONE, SELECTING, PENDING }

    public record BlockCount(Block block, int count) {}

    public record EntityCount(String labelId, int count, ItemStack icon) {}

    private static Phase phase = Phase.NONE;
    private static BlockPos anchorStart = null;
    private static BlockPos anchorEnd = null;
    private static BlockPos lastHover = null;
    private static BlockPos minInclusive = null;
    private static BlockPos maxInclusive = null;
    private static List<BlockCount> blockCounts = List.of();
    private static List<EntityCount> entityCounts = List.of();
    private static int cachedVolume = 0;
    private static long lastScanGameTime = -1;
    /** When true, end corner no longer tracks crosshair (waiting for server / name). */
    private static boolean endLocked = false;

    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int MAX_BLOCK_LINES = 12;
    private static final int MAX_ENTITY_LINES = 4;

    private SelectionPreview() {}

    public static Phase phase() {
        return phase;
    }

    public static boolean isActive() {
        return phase != Phase.NONE;
    }

    public static boolean isSelecting() {
        return phase == Phase.SELECTING;
    }

    public static boolean hasStart() {
        return anchorStart != null;
    }

    public static boolean hasBothAnchors() {
        return anchorStart != null && anchorEnd != null;
    }

    /** Start corner ( {@code [} ). */
    public static BlockPos corner1() {
        return anchorStart;
    }

    /** End corner ( {@code ]} or crosshair while selecting ). */
    public static BlockPos corner2() {
        return anchorEnd;
    }

    public static boolean shouldRenderWorld() {
        if (phase == Phase.NONE) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ChatScreen) return false;
        return minInclusive != null && maxInclusive != null;
    }

    public static boolean shouldRenderHud() {
        return shouldRenderWorld();
    }

    public static boolean followsCrosshair() {
        return phase == Phase.SELECTING && anchorStart != null && !endLocked;
    }

    public static void lockEndCorner() {
        endLocked = true;
    }

    public static BlockPos minInclusive() {
        return minInclusive;
    }

    public static BlockPos maxInclusive() {
        return maxInclusive;
    }

    public static int sizeX() {
        return minInclusive == null ? 0 : maxInclusive.getX() - minInclusive.getX() + 1;
    }

    public static int sizeY() {
        return minInclusive == null ? 0 : maxInclusive.getY() - minInclusive.getY() + 1;
    }

    public static int sizeZ() {
        return minInclusive == null ? 0 : maxInclusive.getZ() - minInclusive.getZ() + 1;
    }

    public static int blockVolume() {
        return cachedVolume;
    }

    public static List<BlockCount> blockCounts() {
        return blockCounts;
    }

    public static List<EntityCount> entityCounts() {
        return entityCounts;
    }

    public static int maxBlockLines() {
        return MAX_BLOCK_LINES;
    }

    public static int maxEntityLines() {
        return MAX_ENTITY_LINES;
    }

    public static AABB selectionBounds() {
        if (minInclusive == null || maxInclusive == null) return null;
        return new AABB(
                minInclusive.getX(), minInclusive.getY(), minInclusive.getZ(),
                maxInclusive.getX() + 1.0, maxInclusive.getY() + 1.0, maxInclusive.getZ() + 1.0);
    }

    public static BlockPos resolveSolidHover(Minecraft client) {
        if (client.level == null) return null;
        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            if (!client.level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    public static void clear() {
        phase = Phase.NONE;
        anchorStart = null;
        anchorEnd = null;
        minInclusive = null;
        maxInclusive = null;
        lastHover = null;
        blockCounts = List.of();
        entityCounts = List.of();
        cachedVolume = 0;
        lastScanGameTime = -1;
        endLocked = false;
    }

    public static void beginSelecting() {
        phase = Phase.SELECTING;
        endLocked = false;
        anchorStart = null;
        anchorEnd = null;
        minInclusive = null;
        maxInclusive = null;
        lastHover = null;
        blockCounts = List.of();
        entityCounts = List.of();
        cachedVolume = 0;
        lastScanGameTime = -1;
    }

    /** Set start corner; does not clear end if already placed. */
    public static void setAnchorStart(BlockPos pos) {
        ensureSelecting();
        anchorStart = pos.immutable();
        if (anchorEnd == null) {
            anchorEnd = anchorStart;
        }
        lastHover = null;
        recomputeBounds();
        lastScanGameTime = -1;
        scanNow();
    }

    /** Set end corner; if no start yet, start is set to the same block. */
    public static void setAnchorEnd(BlockPos pos) {
        ensureSelecting();
        BlockPos p = pos.immutable();
        if (anchorStart == null) {
            anchorStart = p;
        }
        anchorEnd = p;
        lastHover = anchorEnd;
        recomputeBounds();
        lastScanGameTime = -1;
        scanNow();
    }

    public static void updateHover(BlockPos hover) {
        if (!followsCrosshair()) return;
        if (hover != null) {
            lastHover = hover.immutable();
            anchorEnd = lastHover;
        } else if (lastHover != null) {
            anchorEnd = lastHover;
        } else if (anchorEnd == null && anchorStart != null) {
            anchorEnd = anchorStart;
        }
        recomputeBounds();
    }

    public static void setPending(int x1, int y1, int z1, int x2, int y2, int z2) {
        phase = Phase.PENDING;
        anchorStart = new BlockPos(x1, y1, z1);
        anchorEnd = new BlockPos(x2, y2, z2);
        recomputeBounds();
        lastHover = null;
        lastScanGameTime = -1;
        scanNow();
    }

    public static void tick(Minecraft client) {
        if (phase == Phase.NONE || client.level == null) return;
        if (!shouldRenderWorld()) return;
        if (minInclusive == null || maxInclusive == null) return;

        long now = client.level.getGameTime();
        if (lastScanGameTime >= 0 && now - lastScanGameTime < SCAN_INTERVAL_TICKS) return;
        lastScanGameTime = now;
        rescan(client.level);
    }

    private static void ensureSelecting() {
        if (phase == Phase.NONE) {
            beginSelecting();
        }
    }

    private static void recomputeBounds() {
        if (anchorStart == null || anchorEnd == null) return;
        minInclusive = new BlockPos(
                Math.min(anchorStart.getX(), anchorEnd.getX()),
                Math.min(anchorStart.getY(), anchorEnd.getY()),
                Math.min(anchorStart.getZ(), anchorEnd.getZ()));
        maxInclusive = new BlockPos(
                Math.max(anchorStart.getX(), anchorEnd.getX()),
                Math.max(anchorStart.getY(), anchorEnd.getY()),
                Math.max(anchorStart.getZ(), anchorEnd.getZ()));
    }

    private static void scanNow() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            lastScanGameTime = client.level.getGameTime();
            rescan(client.level);
        }
    }

    private static void rescan(Level level) {
        int sx = sizeX(), sy = sizeY(), sz = sizeZ();
        cachedVolume = sx * sy * sz;
        if (cachedVolume <= 0) {
            blockCounts = List.of();
            entityCounts = List.of();
            return;
        }

        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (int y = minInclusive.getY(); y <= maxInclusive.getY(); y++) {
            for (int x = minInclusive.getX(); x <= maxInclusive.getX(); x++) {
                for (int z = minInclusive.getZ(); z <= maxInclusive.getZ(); z++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;
                    counts.merge(state.getBlock(), 1, Integer::sum);
                }
            }
        }
        blockCounts = counts.entrySet().stream()
                .map(e -> new BlockCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(BlockCount::count).reversed())
                .toList();

        AABB box = selectionBounds();
        if (box == null) {
            entityCounts = List.of();
            return;
        }
        Map<String, EntityCount> entityMap = new LinkedHashMap<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
            ItemStack icon = entity.getPickResult();
            if (icon.isEmpty()) continue;
            String key = entity.getType().getDescriptionId() + "|" + icon.getItem();
            entityMap.compute(key, (k, existing) -> {
                if (existing == null) {
                    return new EntityCount(entity.getType().getDescriptionId(), 1, icon.copy());
                }
                return new EntityCount(existing.labelId(), existing.count() + 1, existing.icon());
            });
        }
        entityCounts = entityMap.values().stream()
                .sorted(Comparator.comparingInt(EntityCount::count).reversed())
                .toList();
    }
}
