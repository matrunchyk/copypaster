package com.crazyhouse.copypaster.paste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * Rotated structure footprint for overwrite checks, undo capture, and client ghost.
 */
public final class PasteGeometry {

    private PasteGeometry() {}

    public static Vec3i rotatedSize(int sizeX, int sizeY, int sizeZ, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(sizeZ, sizeY, sizeX);
            default -> new Vec3i(sizeX, sizeY, sizeZ);
        };
    }

    public static AABB worldBounds(BlockPos origin, int sizeX, int sizeY, int sizeZ,
            Rotation rotation, Mirror mirror) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        int[] xs = sizeX <= 1 ? new int[]{0} : new int[]{0, sizeX - 1};
        int[] ys = sizeY <= 1 ? new int[]{0} : new int[]{0, sizeY - 1};
        int[] zs = sizeZ <= 1 ? new int[]{0} : new int[]{0, sizeZ - 1};

        for (int dx : xs) {
            for (int dy : ys) {
                for (int dz : zs) {
                    BlockPos world = origin.offset(
                            transformLocal(new BlockPos(dx, dy, dz), sizeX, sizeY, sizeZ, rotation, mirror));
                    minX = Math.min(minX, world.getX());
                    minY = Math.min(minY, world.getY());
                    minZ = Math.min(minZ, world.getZ());
                    maxX = Math.max(maxX, world.getX());
                    maxY = Math.max(maxY, world.getY());
                    maxZ = Math.max(maxZ, world.getZ());
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    public static BlockPos transformLocal(BlockPos local, int sizeX, int sizeY, int sizeZ,
            Rotation rotation, Mirror mirror) {
        int x = local.getX();
        int y = local.getY();
        int z = local.getZ();

        if (mirror == Mirror.LEFT_RIGHT) {
            x = sizeX - 1 - x;
        } else if (mirror == Mirror.FRONT_BACK) {
            z = sizeZ - 1 - z;
        }

        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(sizeZ - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    public static Rotation parseRotation(int degrees) {
        return switch (degrees) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Mirror parseMirror(String name) {
        return switch (name.toLowerCase()) {
            case "left_right", "lr" -> Mirror.LEFT_RIGHT;
            case "front_back", "fb" -> Mirror.FRONT_BACK;
            default -> Mirror.NONE;
        };
    }
}
