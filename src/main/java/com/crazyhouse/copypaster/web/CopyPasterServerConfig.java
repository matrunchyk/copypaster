package com.crazyhouse.copypaster.web;

import com.crazyhouse.copypaster.CopyPasterMod;
import net.fabricmc.loader.api.FabricLoader;
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
import java.util.UUID;

/**
 * Server config: {@code config/copypaster-server.yml}
 */
public final class CopyPasterServerConfig {

    private static volatile boolean webEnabled;
    private static volatile int webPort = 8792;
    private static volatile String webBind = "127.0.0.1";
    private static volatile String webAuthToken = "";
    private static volatile String webPublicHost = "";

    private CopyPasterServerConfig() {}

    public static void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            writeDefaults(path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Object root = new Yaml().load(reader);
            if (!(root instanceof Map<?, ?> map)) return;
            Object web = map.get("web");
            if (!(web instanceof Map<?, ?> w)) return;
            webEnabled = boolVal(w.get("enabled"), false);
            webPort = intVal(w.get("port"), 8792);
            if (w.get("bind") instanceof String bind && !bind.isBlank()) {
                webBind = bind.trim();
            }
            if (w.get("authToken") instanceof String token && !token.isBlank()) {
                webAuthToken = token.trim();
            }
            if (w.get("publicHost") instanceof String host) {
                webPublicHost = host.trim();
            }
        } catch (IOException e) {
            CopyPasterMod.LOGGER.warn("Failed to read {}: {}", path, e.getMessage());
        }
        if (webAuthToken.isBlank()) {
            webAuthToken = UUID.randomUUID().toString();
            writeYaml();
        }
    }

    public static boolean webEnabled() {
        return webEnabled;
    }

    public static int webPort() {
        return webPort;
    }

    public static String webBind() {
        return webBind;
    }

    public static String webAuthToken() {
        return webAuthToken;
    }

    public static String webPublicHost() {
        return webPublicHost;
    }

    public static String webUrlHint() {
        String host = webPublicHost.isBlank() ? webBind : webPublicHost;
        if ("0.0.0.0".equals(host)) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + webPort + "/";
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("copypaster-server.yml");
    }

    private static void writeDefaults(Path path) {
        webEnabled = false;
        webPort = 8792;
        webBind = "127.0.0.1";
        webAuthToken = UUID.randomUUID().toString();
        webPublicHost = "";
        writeYaml();
    }

    private static void writeYaml() {
        Map<String, Object> web = new LinkedHashMap<>();
        web.put("enabled", webEnabled);
        web.put("port", webPort);
        web.put("bind", webBind);
        web.put("authToken", webAuthToken);
        web.put("publicHost", webPublicHost);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("web", web);

        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            try (Writer writer = Files.newBufferedWriter(path)) {
                new Yaml(opts).dump(root, writer);
            }
        } catch (IOException e) {
            CopyPasterMod.LOGGER.warn("Failed to write {}: {}", path, e.getMessage());
        }
    }

    private static int intVal(Object o, int fallback) {
        if (o instanceof Number n) return n.intValue();
        return fallback;
    }

    private static boolean boolVal(Object o, boolean fallback) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) {
            return Boolean.parseBoolean(s.trim().toLowerCase(Locale.ROOT));
        }
        return fallback;
    }
}
