package com.crazyhouse.copypaster.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * In-memory snapshot of blocks overwritten by a paste.
 * Restored via setBlock without neighbour updates to avoid physics cascades.
 * Cleared on server restart.
 */
public record UndoSnapshot(
    String pasteId,
    UUID playerId,
    String playerName,
    String structureName,
    List<BlockSnapshot> blocks
) {
    public record BlockSnapshot(BlockPos pos, BlockState state) {}
}
