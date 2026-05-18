package com.crazyhouse.copypaster.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.crazyhouse.copypaster.service.StructureStorageService;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@Environment(EnvType.CLIENT)
public enum SelectionHudOverlay implements HudElement {
    INSTANCE;

    private static final int PANEL_BG = 0xA0000000;
    private static final int LINE = 0xFFAAAAAA;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFFFEE88;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!SelectionPreview.shouldRenderHud()) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        BlockPos min = SelectionPreview.minInclusive();
        BlockPos max = SelectionPreview.maxInclusive();
        if (min == null || max == null) return;

        int sx = SelectionPreview.sizeX();
        int sy = SelectionPreview.sizeY();
        int sz = SelectionPreview.sizeZ();
        int count = SelectionPreview.blockVolume();
        List<SelectionPreview.BlockCount> blocks = SelectionPreview.blockCounts();
        List<SelectionPreview.EntityCount> entities = SelectionPreview.entityCounts();
        int maxBlockLines = SelectionPreview.maxBlockLines();
        int maxEntityLines = SelectionPreview.maxEntityLines();
        int extraBlocks = Math.max(0, blocks.size() - maxBlockLines);
        int extraEntities = Math.max(0, entities.size() - maxEntityLines);

        String sizeLine = sx + "×" + sy + "×" + sz;
        String detailLine = count + " blocks, "
                + min.getX() + "×" + min.getY() + "×" + min.getZ() + ":"
                + max.getX() + "×" + max.getY() + "×" + max.getZ();

        Component hint = hintLine();
        int lineH = font.lineHeight;
        int iconSize = 16;
        int rowH = Math.max(lineH, iconSize + 2);
        int panelW = 240;
        int hintH = hint != null ? lineH + 4 : 0;
        int entityRows = Math.min(entities.size(), maxEntityLines);
        int entityHeader = entityRows > 0 ? rowH : 0;
        int panelH = (int) (lineH * 2.8f) + 8 + hintH + rowH
                + Math.min(blocks.size(), maxBlockLines) * rowH
                + (extraBlocks > 0 ? rowH : 0)
                + entityHeader + entityRows * rowH
                + (extraEntities > 0 ? rowH : 0);

        int[] origin = CopyPasterConfig.hudPanelOrigin(panelW, panelH, graphics.guiWidth(), graphics.guiHeight());
        int x = origin[0];
        int y = origin[1];
        int itemSeed = (int) (client.level != null ? client.level.getGameTime() : 0);

        graphics.fill(x - 4, y - 4, x + panelW + 4, y + panelH + 4, PANEL_BG);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + panelW / 2f, y);
        pose.scale(1.8f, 1.8f);
        graphics.centeredText(font, sizeLine, 0, 0, TEXT);
        pose.popMatrix();

        y += (int) (lineH * 2.2f);
        graphics.text(font, detailLine, x, y, TEXT);
        y += lineH + 2;

        if (hint != null) {
            graphics.text(font, hint, x, y, HINT);
            y += lineH + 4;
        }

        graphics.fill(x, y, x + panelW, y + 1, LINE);
        y += 5;

        int shown = 0;
        for (SelectionPreview.BlockCount entry : blocks) {
            if (shown >= maxBlockLines) break;
            ItemStack stack = new ItemStack(entry.block());
            graphics.item(stack, x, y + (rowH - iconSize) / 2, itemSeed);
            Component name = Component.translatable(entry.block().getDescriptionId());
            graphics.text(font, name, x + iconSize + 4, y + (rowH - lineH) / 2, TEXT);
            String qty = String.valueOf(entry.count());
            graphics.text(font, qty, x + panelW - font.width(qty) - 2, y + (rowH - lineH) / 2, TEXT);
            y += rowH;
            shown++;
        }

        if (extraBlocks > 0) {
            graphics.text(font,
                    Component.translatable("copypaster.overlay.blocks_more", extraBlocks),
                    x, y, LINE);
            y += rowH;
        }

        if (entityRows > 0) {
            graphics.fill(x, y, x + panelW, y + 1, LINE);
            y += 5;
            graphics.text(font,
                    Component.translatable("copypaster.hud.entities_header"),
                    x, y, LINE);
            y += rowH;
        }

        shown = 0;
        for (SelectionPreview.EntityCount entry : entities) {
            if (shown >= maxEntityLines) break;
            ItemStack stack = entry.icon();
            graphics.item(stack, x, y + (rowH - iconSize) / 2, itemSeed + shown);
            Component name = Component.translatable(entry.labelId());
            graphics.text(font, name, x + iconSize + 4, y + (rowH - lineH) / 2, TEXT);
            String qty = String.valueOf(entry.count());
            graphics.text(font, qty, x + panelW - font.width(qty) - 2, y + (rowH - lineH) / 2, TEXT);
            y += rowH;
            shown++;
        }

        if (extraEntities > 0) {
            graphics.text(font,
                    Component.translatable("copypaster.overlay.entities_more", extraEntities),
                    x, y, LINE);
        }
    }

    private static Component hintLine() {
        if (SelectionPreview.blockVolume() > StructureStorageService.MAX_VOLUME) {
            return Component.translatable("copypaster.overlay.region_too_large",
                    SelectionPreview.blockVolume(), StructureStorageService.MAX_VOLUME);
        }
        return switch (SelectionPreview.phase()) {
            case SELECTING -> SelectionPreview.hasStart()
                    ? Component.translatable("copypaster.hud.hint.corner2")
                    : Component.translatable("copypaster.hud.hint.corner1");
            case PENDING -> Component.translatable("copypaster.hud.hint.name");
            default -> null;
        };
    }
}
