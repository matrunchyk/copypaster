package com.crazyhouse.copypaster.paste;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Resolved paste parameters for one /paste invocation.
 */
public record PasteOptions(
        String name,
        BlockPos absoluteOrigin,
        Rotation rotation,
        Mirror mirror,
        boolean skipAir,
        boolean confirm
) {
    public boolean usesAbsoluteOrigin() {
        return absoluteOrigin != null;
    }

    public static PasteOptions playerRelative(String name, Rotation rotation, Mirror mirror,
            boolean skipAir, boolean confirm) {
        return new PasteOptions(name, null, rotation, mirror, skipAir, confirm);
    }

    public static PasteOptions at(String name, BlockPos origin, Rotation rotation, Mirror mirror,
            boolean skipAir, boolean confirm) {
        return new PasteOptions(name, origin, rotation, mirror, skipAir, confirm);
    }

    public PasteOptions withConfirm() {
        return new PasteOptions(name, absoluteOrigin, rotation, mirror, skipAir, true);
    }
}
