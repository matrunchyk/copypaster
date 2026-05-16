package com.crazyhouse.copypaster.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PendingCopy(
    UUID playerId,
    ResourceKey<Level> dimension,
    BlockPos corner1,
    BlockPos corner2,
    int sizeX,
    int sizeY,
    int sizeZ,
    BlockPos offset,    // playerPos - corner1 at /copy time
    long createdAt      // System.currentTimeMillis()
) {}
