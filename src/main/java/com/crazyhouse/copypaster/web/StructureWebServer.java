package com.crazyhouse.copypaster.web;

import com.crazyhouse.copypaster.CopyPasterMod;
import com.crazyhouse.copypaster.service.StructureStorageService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StructureWebServer {

    private static final Gson GSON = new Gson();
    private static final Pattern STRUCTURE_NAME = Pattern.compile("^/api/structures/([a-zA-Z0-9_-]{1,64})(?:/download)?$");

    private final StructureModelService modelService;
    private HttpServer httpServer;
    private volatile MinecraftServer minecraftServer;

    public StructureWebServer(StructureStorageService storage) {
        this.modelService = new StructureModelService(storage);
    }

    public void start(MinecraftServer server) throws IOException {
        this.minecraftServer = server;
        String bind = CopyPasterServerConfig.webBind();
        int port = CopyPasterServerConfig.webPort();
        httpServer = HttpServer.create(new InetSocketAddress(bind, port), 0);
        httpServer.createContext("/", this::handleRoot);
        httpServer.createContext("/api", this::handleApi);
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "copypaster-web");
            t.setDaemon(true);
            return t;
        }));
        httpServer.start();
        CopyPasterMod.LOGGER.info("CopyPaster web UI: {} (Bearer token in config/copypaster-server.yml)",
            CopyPasterServerConfig.webUrlHint());
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        minecraftServer = null;
    }

    private void handleRoot(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                serveResource(exchange, "/copypaster/web/index.html", "text/html; charset=utf-8");
                return;
            }
            if (path.startsWith("/assets/")) {
                String resource = "/copypaster/web" + path;
                String contentType = path.endsWith(".js") ? "application/javascript"
                    : path.endsWith(".css") ? "text/css"
                    : path.endsWith(".svg") ? "image/svg+xml"
                    : "application/octet-stream";
                serveResource(exchange, resource, contentType);
            } else {
                sendError(exchange, 404, "Not found");
            }
        } catch (IOException e) {
            logExchangeError(exchange, e);
        }
    }

    private void handleApi(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();

            if (!checkAuth(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            if (path.equals("/api/structures") && "GET".equals(method)) {
                onServer(exchange, () -> handleListStructures(exchange));
                return;
            }

            if (path.equals("/api/blocks") && "GET".equals(method)) {
                String q = queryParam(exchange, "q");
                int limit = Math.min(parseInt(queryParam(exchange, "limit"), 50), 200);
                onServer(exchange, () -> handleSearchBlocks(exchange, q, limit));
                return;
            }

            Matcher m = STRUCTURE_NAME.matcher(path);
            if (m.matches()) {
                String name = m.group(1);
                if (path.endsWith("/download") && "GET".equals(method)) {
                    onServer(exchange, () -> handleDownload(exchange, name));
                    return;
                }
                switch (method) {
                    case "GET" -> onServer(exchange, () -> handleGetStructure(exchange, name));
                    case "PUT" -> onServer(exchange, () -> handlePutStructure(exchange, name));
                    case "DELETE" -> onServer(exchange, () -> handleDeleteStructure(exchange, name));
                    default -> sendError(exchange, 405, "Method not allowed");
                }
                return;
            }

            sendError(exchange, 404, "Not found");
        } catch (IOException e) {
            logExchangeError(exchange, e);
        }
    }

    private void onServer(HttpExchange exchange, ServerAction action) throws IOException {
        MinecraftServer server = minecraftServer;
        if (server == null) {
            sendError(exchange, 503, "Server not ready");
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        server.execute(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (IOException e) {
                done.completeExceptionally(e);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        });
        try {
            done.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            sendError(exchange, 504, "Timed out");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException io) {
                throw io;
            }
            CopyPasterMod.LOGGER.error("Web API error", cause);
            sendError(exchange, 500, cause.getMessage() != null ? cause.getMessage() : "Internal error");
        }
    }

    @FunctionalInterface
    private interface ServerAction {
        void run() throws IOException;
    }

    private void handleListStructures(HttpExchange exchange) throws IOException {
        List<StructureStorageService.StructureInfo> list = CopyPasterMod.STORAGE.listAll();
        sendJson(exchange, 200, GSON.toJson(list.stream().map(this::summaryJson).toList()));
    }

    private JsonObject summaryJson(StructureStorageService.StructureInfo info) {
        JsonObject o = new JsonObject();
        o.addProperty("name", info.name());
        o.addProperty("sizeX", info.sizeX());
        o.addProperty("sizeY", info.sizeY());
        o.addProperty("sizeZ", info.sizeZ());
        o.addProperty("creatorName", info.creatorName());
        o.addProperty("createdAt", info.createdAt());
        o.addProperty("dimension", info.dimension());
        return o;
    }

    private void handleGetStructure(HttpExchange exchange, String name) throws IOException {
        try {
            JsonObject model = modelService.exportModel(name, minecraftServer.registryAccess());
            sendJson(exchange, 200, GSON.toJson(model));
        } catch (IOException e) {
            sendError(exchange, 404, e.getMessage());
        }
    }

    private void handlePutStructure(HttpExchange exchange, String name) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        StructureEditRequest request = StructureEditRequest.fromJson(JsonParser.parseString(body).getAsJsonObject());
        try {
            modelService.applyEdits(name, request, minecraftServer.registryAccess());
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (IOException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void handleDeleteStructure(HttpExchange exchange, String name) throws IOException {
        if (!CopyPasterMod.STORAGE.nbtExists(name)) {
            sendError(exchange, 404, "Structure not found");
            return;
        }
        CopyPasterMod.STORAGE.delete(name);
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void handleDownload(HttpExchange exchange, String name) throws IOException {
        if (!CopyPasterMod.STORAGE.nbtExists(name)) {
            sendError(exchange, 404, "Structure not found");
            return;
        }
        byte[] data = modelService.readNbtBytes(name);
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition",
            "attachment; filename=\"" + name + ".nbt\"");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void handleSearchBlocks(HttpExchange exchange, String query, int limit) throws IOException {
        sendJson(exchange, 200, GSON.toJson(modelService.searchBlocks(query, limit)));
    }

    private boolean checkAuth(HttpExchange exchange) {
        String expected = CopyPasterServerConfig.webAuthToken();
        if (expected.isBlank()) return false;
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null) return false;
        if (auth.startsWith("Bearer ")) {
            return expected.equals(auth.substring(7).trim());
        }
        return false;
    }

    private void serveResource(HttpExchange exchange, String classpath, String contentType) throws IOException {
        try (InputStream is = StructureWebServer.class.getResourceAsStream(classpath)) {
            if (is == null) {
                sendError(exchange, 404, "Resource missing — run ./gradlew buildWeb");
                return;
            }
            byte[] bytes = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        sendJson(exchange, code, GSON.toJson(err));
    }

    private static void logExchangeError(HttpExchange exchange, IOException e) {
        CopyPasterMod.LOGGER.warn("Web request failed {} {}: {}",
            exchange.getRequestMethod(), exchange.getRequestURI(), e.getMessage());
    }
}
