package com.crazyhouse.copypaster.paste;

import com.crazyhouse.copypaster.CopyPasterCommands;
import com.crazyhouse.copypaster.CopyPasterMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Brigadier tree for {@code /paste <name>} with optional {@code at}, {@code rotate},
 * {@code mirror}, {@code noair}, and {@code confirm}.
 */
public final class PasteCommandTree {

    private PasteCommandTree() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        RequiredArgumentBuilder<CommandSourceStack, String> name = Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    CopyPasterMod.STORAGE.listNames().stream()
                            .filter(n -> n.startsWith(builder.getRemaining()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
        attachModifiers(name, new Flags());
        return Commands.literal("paste").then(name);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> attachModifiers(
            ArgumentBuilder<CommandSourceStack, ?> node, Flags flags) {
        node.executes(ctx -> run(ctx, flags));

        if (!flags.confirm) {
            node.then(Commands.literal("confirm").executes(ctx -> run(ctx, flags.withConfirm(true))));
        }

        if (!flags.noAir) {
            ArgumentBuilder<CommandSourceStack, ?> noAir = Commands.literal("noair");
            attachModifiers(noAir, flags.withNoAir(true));
            node.then(noAir);
        }

        if (flags.mirror == Mirror.NONE) {
            node.then(attachModifiers(
                    Commands.literal("mirror").then(Commands.literal("left_right")), flags.withMirror(Mirror.LEFT_RIGHT)));
            node.then(attachModifiers(
                    Commands.literal("mirror").then(Commands.literal("front_back")), flags.withMirror(Mirror.FRONT_BACK)));
        }

        if (flags.rotation == Rotation.NONE) {
            node.then(attachModifiers(
                    Commands.literal("rotate").then(Commands.literal("90")), flags.withRotation(Rotation.CLOCKWISE_90)));
            node.then(attachModifiers(
                    Commands.literal("rotate").then(Commands.literal("180")), flags.withRotation(Rotation.CLOCKWISE_180)));
            node.then(attachModifiers(
                    Commands.literal("rotate").then(Commands.literal("270")), flags.withRotation(Rotation.COUNTERCLOCKWISE_90)));
        }

        if (!flags.hasAt) {
            ArgumentBuilder<CommandSourceStack, ?> atZ = Commands.argument("z", IntegerArgumentType.integer());
            Flags atScoped = flags.copy();
            atScoped.hasAt = true;
            attachModifiers(atZ, atScoped);
            node.then(Commands.literal("at")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(atZ))));
        }

        return node;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Flags flags) throws CommandSyntaxException {
        Flags resolved = flags.resolveAt(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        PasteOptions opts = resolved.hasAt
                ? PasteOptions.at(name, resolved.at, resolved.rotation, resolved.mirror,
                resolved.noAir, resolved.confirm)
                : PasteOptions.playerRelative(name, resolved.rotation, resolved.mirror,
                resolved.noAir, resolved.confirm);
        return CopyPasterCommands.runPaste(ctx.getSource().getPlayerOrException(), opts);
    }

    private static final class Flags {
        BlockPos at;
        boolean hasAt;
        Rotation rotation = Rotation.NONE;
        Mirror mirror = Mirror.NONE;
        boolean noAir;
        boolean confirm;

        Flags withConfirm(boolean c) {
            Flags f = copy();
            f.confirm = c;
            return f;
        }

        Flags withNoAir(boolean n) {
            Flags f = copy();
            f.noAir = n;
            return f;
        }

        Flags withRotation(Rotation r) {
            Flags f = copy();
            f.rotation = r;
            return f;
        }

        Flags withMirror(Mirror m) {
            Flags f = copy();
            f.mirror = m;
            return f;
        }

        Flags resolveAt(CommandContext<CommandSourceStack> ctx) {
            if (hasAt) return this;
            try {
                int x = IntegerArgumentType.getInteger(ctx, "x");
                int y = IntegerArgumentType.getInteger(ctx, "y");
                int z = IntegerArgumentType.getInteger(ctx, "z");
                Flags f = copy();
                f.at = new BlockPos(x, y, z);
                f.hasAt = true;
                return f;
            } catch (IllegalArgumentException e) {
                return this;
            }
        }

        Flags copy() {
            Flags f = new Flags();
            f.at = at;
            f.hasAt = hasAt;
            f.rotation = rotation;
            f.mirror = mirror;
            f.noAir = noAir;
            f.confirm = confirm;
            return f;
        }
    }
}
