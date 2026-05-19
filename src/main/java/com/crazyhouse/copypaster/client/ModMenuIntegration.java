package com.crazyhouse.copypaster.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::buildScreen;
    }

    private static net.minecraft.client.gui.screens.Screen buildScreen(net.minecraft.client.gui.screens.Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("copypaster.config.title"));
        ConfigEntryBuilder entry = builder.entryBuilder();

        ConfigCategory highlight = builder.getOrCreateCategory(
                Component.translatable("copypaster.config.category_highlight"));
        var alpha = entry.startIntSlider(
                        Component.translatable("copypaster.config.alpha"),
                        CopyPasterConfig.selectionAlpha(), 0, 255)
                .setDefaultValue(180)
                .build();
        var red = entry.startIntSlider(
                        Component.translatable("copypaster.config.red"),
                        CopyPasterConfig.selectionRed(), 0, 255)
                .setDefaultValue(80)
                .build();
        var green = entry.startIntSlider(
                        Component.translatable("copypaster.config.green"),
                        CopyPasterConfig.selectionGreen(), 0, 255)
                .setDefaultValue(160)
                .build();
        var blue = entry.startIntSlider(
                        Component.translatable("copypaster.config.blue"),
                        CopyPasterConfig.selectionBlue(), 0, 255)
                .setDefaultValue(255)
                .build();
        highlight.addEntry(alpha);
        highlight.addEntry(red);
        highlight.addEntry(green);
        highlight.addEntry(blue);

        ConfigCategory hud = builder.getOrCreateCategory(
                Component.translatable("copypaster.config.category_hud"));
        var anchorPicker = new HudAnchorGridEntry(
                Component.translatable("copypaster.config.hud_position"),
                CopyPasterConfig::hudAnchor);
        hud.addEntry(anchorPicker);

        ConfigCategory limits = builder.getOrCreateCategory(
                Component.translatable("copypaster.config.category_limits"));
        var maxVol = entry.startIntField(
                        Component.translatable("copypaster.config.max_volume"),
                        CopyPasterConfig.maxVolume())
                .setDefaultValue(32_768)
                .setMin(1)
                .setMax(1_000_000)
                .build();
        limits.addEntry(maxVol);

        builder.setSavingRunnable(() -> CopyPasterConfig.save(
                alpha.getValue(), red.getValue(), green.getValue(), blue.getValue(),
                anchorPicker.getValue(), maxVol.getValue()));

        return builder.build();
    }
}
