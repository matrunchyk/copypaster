package com.crazyhouse.copypaster;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

public final class CopyPasterPermissions {

    public static final String COPY = "copypaster.copy";
    public static final String PASTE = "copypaster.paste";
    public static final String PASTE_UNDO = "copypaster.pasteundo";
    public static final String COPY_LIST = "copypaster.copylist";
    public static final String COPY_INFO = "copypaster.copyinfo";
    public static final String COPY_DELETE = "copypaster.copydelete";
    public static final String COPY_WEB = "copypaster.copyweb";

    private static Method permissionsCheck;
    private static boolean permissionsLookupDone;

    private CopyPasterPermissions() {}

    public static boolean has(ServerPlayer player, String node) {
        if (isOp(player)) {
            return true;
        }
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            return checkLuckPerms(player, node);
        }
        return false;
    }

    private static boolean checkLuckPerms(ServerPlayer player, String node) {
        Method check = permissionsCheckMethod();
        if (check != null) {
            try {
                return (boolean) check.invoke(null, player, node, false);
            } catch (ReflectiveOperationException e) {
                CopyPasterMod.LOGGER.warn("Permission check failed for {}: {}", node, e.getMessage());
            }
        }
        return false;
    }

    private static Method permissionsCheckMethod() {
        if (permissionsLookupDone) {
            return permissionsCheck;
        }
        permissionsLookupDone = true;
        try {
            Class<?> perms = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            permissionsCheck = perms.getMethod("check", Entity.class, String.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            CopyPasterMod.LOGGER.warn("LuckPerms is installed but fabric-permissions-api is unavailable: {}",
                    e.getMessage());
        }
        return permissionsCheck;
    }

    private static boolean isOp(ServerPlayer player) {
        return player.level().getServer().getPlayerList().isOp(
                new NameAndId(player.getUUID(), player.getName().getString()));
    }
}
