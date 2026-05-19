package com.crazyhouse.copypaster.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.ARGB;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client config: {@code config/copypaster.yml}
 */
@Environment(EnvType.CLIENT)
public final class CopyPasterConfig {

    public enum HudAnchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static HudAnchor fromId(String id) {
            if (id == null || id.isBlank()) return TOP_CENTER;
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return TOP_CENTER;
            }
        }
    }

    private static final int DEFAULT_ALPHA = 180;
    private static final int DEFAULT_RED = 80;
    private static final int DEFAULT_GREEN = 160;
    private static final int DEFAULT_BLUE = 255;
    private static final HudAnchor DEFAULT_ANCHOR = HudAnchor.TOP_CENTER;
    private static final int DEFAULT_MAX_VOLUME = 32_768;

    private static volatile int selectionColorArgb =
            ARGB.color(DEFAULT_ALPHA, DEFAULT_RED, DEFAULT_GREEN, DEFAULT_BLUE);
    private static volatile HudAnchor hudAnchor = DEFAULT_ANCHOR;
    private static volatile int maxVolume = DEFAULT_MAX_VOLUME;

    static void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            writeDefaults(path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Object root = new Yaml().load(reader);
            if (!(root instanceof Map<?, ?> map)) return;
            Object highlight = map.get("selectionHighlight");
            if (highlight instanceof Map<?, ?> h) {
                int alpha = intVal(h.get("alpha"), DEFAULT_ALPHA);
                int red = intVal(h.get("red"), DEFAULT_RED);
                int green = intVal(h.get("green"), DEFAULT_GREEN);
                int blue = intVal(h.get("blue"), DEFAULT_BLUE);
                selectionColorArgb = ARGB.color(clamp(alpha), clamp(red), clamp(green), clamp(blue));
            }
            if (map.get("hudAnchor") instanceof String anchorId) {
                hudAnchor = HudAnchor.fromId(anchorId);
            }
            Object limits = map.get("limits");
            if (limits instanceof Map<?, ?> lim) {
                maxVolume = intVal(lim.get("maxVolume"), DEFAULT_MAX_VOLUME);
            }
        } catch (IOException e) {
            CopyPasterClientMod.LOGGER.warn("Failed to read {}: {}", path, e.getMessage());
        }
    }

    static void save(int alpha, int red, int green, int blue, HudAnchor anchor) {
        save(alpha, red, green, blue, anchor, maxVolume);
    }

    static void save(int alpha, int red, int green, int blue, HudAnchor anchor, int volumeLimit) {
        alpha = clamp(alpha);
        red = clamp(red);
        green = clamp(green);
        blue = clamp(blue);
        selectionColorArgb = ARGB.color(alpha, red, green, blue);
        hudAnchor = anchor == null ? DEFAULT_ANCHOR : anchor;
        maxVolume = Math.max(1, volumeLimit);
        writeYaml();
    }

    static int maxVolume() {
        return maxVolume;
    }

    static int selectionColorArgb() {
        return selectionColorArgb;
    }

    static int selectionFillArgb() {
        int a = Math.min(96, ARGB.alpha(selectionColorArgb));
        return ARGB.color(a, ARGB.red(selectionColorArgb),
                ARGB.green(selectionColorArgb), ARGB.blue(selectionColorArgb));
    }

    static int selectionAlpha() { return ARGB.alpha(selectionColorArgb); }
    static int selectionRed() { return ARGB.red(selectionColorArgb); }
    static int selectionGreen() { return ARGB.green(selectionColorArgb); }
    static int selectionBlue() { return ARGB.blue(selectionColorArgb); }

    static HudAnchor hudAnchor() {
        return hudAnchor;
    }

    static void setHudAnchor(HudAnchor anchor) {
        hudAnchor = anchor == null ? DEFAULT_ANCHOR : anchor;
    }

  /** Panel top-left (x, y) for the given panel size and screen size. */
    static int[] hudPanelOrigin(int panelW, int panelH, int screenW, int screenH) {
        int margin = 8;
        int x = switch (hudAnchor) {
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> margin;
            case TOP_CENTER, MIDDLE_CENTER, BOTTOM_CENTER -> (screenW - panelW) / 2;
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> screenW - panelW - margin;
        };
        int y = switch (hudAnchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> margin;
            case MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT -> (screenH - panelH) / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenH - panelH - margin;
        };
        return new int[]{x, y};
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("copypaster.yml");
    }

    private static void writeDefaults(Path path) {
        save(DEFAULT_ALPHA, DEFAULT_RED, DEFAULT_GREEN, DEFAULT_BLUE, DEFAULT_ANCHOR);
    }

    private static void writeYaml() {
        Map<String, Object> highlight = new LinkedHashMap<>();
        highlight.put("alpha", selectionAlpha());
        highlight.put("red", selectionRed());
        highlight.put("green", selectionGreen());
        highlight.put("blue", selectionBlue());
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxVolume", maxVolume);
        root.put("selectionHighlight", highlight);
        root.put("hudAnchor", hudAnchor.id());
        root.put("limits", limits);

        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            try (Writer writer = Files.newBufferedWriter(path)) {
                new Yaml(opts).dump(root, writer);
            }
        } catch (IOException e) {
            CopyPasterClientMod.LOGGER.warn("Failed to write {}: {}", path, e.getMessage());
        }
    }

    private static int intVal(Object o, int fallback) {
        if (o instanceof Number n) return n.intValue();
        return fallback;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private CopyPasterConfig() {}
}
