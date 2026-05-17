package com.crazyhouse.copypaster.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Inline 3×3 grid for {@link CopyPasterConfig.HudAnchor} in Cloth Config. */
@Environment(EnvType.CLIENT)
public final class HudAnchorGridEntry extends AbstractConfigListEntry<CopyPasterConfig.HudAnchor> {

    private static final int CELL = 22;
    private static final int GAP = 4;
    private static final int GRID = CELL * 3 + GAP * 2;
    private static final int LABEL_ROWS = 28;

    private final Supplier<CopyPasterConfig.HudAnchor> initialGetter;
    private CopyPasterConfig.HudAnchor value;
    private int layoutX;
    private int layoutY;
    private int layoutWidth;

    public HudAnchorGridEntry(Component title, Supplier<CopyPasterConfig.HudAnchor> initialGetter) {
        super(title, false);
        this.initialGetter = initialGetter;
        this.value = initialGetter.get();
    }

    @Override
    public CopyPasterConfig.HudAnchor getValue() {
        return value;
    }

    @Override
    public Optional<CopyPasterConfig.HudAnchor> getDefaultValue() {
        return Optional.of(CopyPasterConfig.HudAnchor.TOP_CENTER);
    }

    @Override
    public boolean isEdited() {
        return value != initialGetter.get();
    }

    @Override
    public void save() {
        CopyPasterConfig.setHudAnchor(value);
    }

    @Override
    public int getItemHeight() {
        return LABEL_ROWS + GRID + 8;
    }

    @Override
    public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
        return Collections.emptyList();
    }

    @Override
    public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
        return Collections.emptyList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int mouseX, int mouseY, int delta, boolean hovered, float alpha) {
        layoutX = x;
        layoutY = y;
        layoutWidth = width;

        var font = Minecraft.getInstance().font;
        graphics.text(font, getFieldName(), x, y + 2, 0xFFFFFFFF);

        Component current = Component.translatable("copypaster.config.hud_picker_current",
                Component.translatable("copypaster.config.hud_anchor." + value.id()));
        graphics.text(font, current, x, y + 14, 0xFFAAAAAA);

        int gridX = gridOriginX();
        int gridY = gridOriginY();

        for (CopyPasterConfig.HudAnchor anchor : CopyPasterConfig.HudAnchor.values()) {
            int col = anchor.ordinal() % 3;
            int row = anchor.ordinal() / 3;
            int cx = gridX + col * (CELL + GAP);
            int cy = gridY + row * (CELL + GAP);
            boolean selected = anchor == value;
            boolean hot = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            int fill = selected ? 0xFF4A90D9 : (hot ? 0xFF666666 : 0xFF404040);
            graphics.fill(cx, cy, cx + CELL, cy + CELL, fill);
            if (selected) {
                graphics.fill(cx, cy, cx + CELL, cy + 1, 0xFFFFFFFF);
                graphics.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, 0xFFFFFFFF);
                graphics.fill(cx, cy, cx + 1, cy + CELL, 0xFFFFFFFF);
                graphics.fill(cx + CELL - 1, cy, cx + CELL, cy + CELL, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return false;
        }
        CopyPasterConfig.HudAnchor picked = anchorAt(event.x(), event.y());
        if (picked == null) {
            return false;
        }
        value = picked;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    private int gridOriginX() {
        return layoutX + Math.max(0, (layoutWidth - GRID) / 2);
    }

    private int gridOriginY() {
        return layoutY + LABEL_ROWS;
    }

    private CopyPasterConfig.HudAnchor anchorAt(double mouseX, double mouseY) {
        int gridX = gridOriginX();
        int gridY = gridOriginY();
        for (CopyPasterConfig.HudAnchor anchor : CopyPasterConfig.HudAnchor.values()) {
            int col = anchor.ordinal() % 3;
            int row = anchor.ordinal() / 3;
            int cx = gridX + col * (CELL + GAP);
            int cy = gridY + row * (CELL + GAP);
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                return anchor;
            }
        }
        return null;
    }

    @Override
    public boolean isRequiresRestart() {
        return false;
    }

    @Override
    public void setRequiresRestart(boolean requiresRestart) {
    }
}
