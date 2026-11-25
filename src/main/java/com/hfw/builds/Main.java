package com.hfw.builds;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/** Servidor web sin dependencias (solo JDK): API REST + archivos estáticos de /static. */
public final class Main {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "webp", "image/webp",
            "ico", "image/x-icon");

    private final Catalog catalog;

    private Main(Catalog catalog) { this.catalog = catalog; }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        HttpServer server = start(port);
        System.out.println("Horizon builds: http://localhost:" + server.getAddress().getPort());
    }

    /** Arranca el servidor; con puerto 0 elige uno libre (lo usan las pruebas). */
    static HttpServer start(int port) throws IOException {
        Main app = new Main(Catalog.load());
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", app::handleApi);
        server.createContext("/", app::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        return server;
    }

    // ------------------------------------------------------------------ API
    private static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) throw new ApiException(405, "Método no permitido.");
            String[] parts = ex.getRequestURI().getPath().substring("/api/".length()).split("/");
            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());

            Object body;
            if (parts.length == 1 && parts[0].equals("meta")) {
                body = catalog.meta();
            } else if (parts.length == 1 && parts[0].equals("recommend")) {
                body = recommend(query.getOrDefault("phase", ""), query.getOrDefault("styles", ""));
            } else if (parts.length == 3 && parts[0].equals("builds")) {
                body = catalog.getBuild(parts[1], parts[2]).orElseThrow(
                        () -> new ApiException(404, "No existe una build para esa clase y fase."));
            } else if (parts.length == 1 && parts[0].equals("items")) {
                body = Map.of("items", catalog.listItems(query.get("category"), query.get("rarity"), query.get("origin"), query.get("q")));
            } else if (parts.length == 1 && parts[0].equals("valor-surges")) {
                body = catalog.valorSurges();
            } else {
                throw new ApiException(404, "No encontrado.");
            }
            send(ex, 200, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8));
        } catch (ApiException e) {
            send(ex, e.status, "application/json; charset=utf-8", Json.write(Map.of("error", e.getMessage())).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            e.printStackTrace();
            send(ex, 500, "application/json; charset=utf-8",
                    Json.write(Map.of("error", "Error interno al consultar los datos.")).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** GET /api/recommend?phase=late&styles=stealth,ranged */
    private Map<String, Object> recommend(String phase, String stylesParam) {
        Model.Meta meta = catalog.meta();
        if (meta.phases().stream().noneMatch(p -> p.code().equals(phase))) throw new ApiException(400, "Fase no válida.");

        Set<String> valid = new HashSet<>();
        meta.styles().forEach(s -> valid.add(s.code()));
        List<String> picked = Arrays.stream(stylesParam.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<String> unknown = picked.stream().filter(s -> !valid.contains(s)).toList();
        if (!unknown.isEmpty()) throw new ApiException(400, "Estilos no válidos: " + String.join(", ", unknown));

        Map<String, Model.GameClass> classes = new HashMap<>();
        meta.classes().forEach(c -> classes.put(c.code(), c));
        Map<String, Model.BuildSummary> builds = new HashMap<>();
        catalog.listBuilds(phase).forEach(b -> builds.put(b.classCode(), b));

        List<Object> results = new ArrayList<>();
        for (Recommender.Ranked r : Recommender.rank(meta.classes(), picked)) {
            Model.GameClass c = classes.get(r.code());
            Map<String, Object> cls = new LinkedHashMap<>();
            cls.put("code", c.code());
            cls.put("name", c.name());
            cls.put("tagline", c.tagline());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("class", cls); // "class" es palabra reservada en Java, por eso va en un Map
            row.put("score", r.score());
            row.put("match", r.match());
            row.put("build", builds.get(r.code()));
            results.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phase", phase);
        out.put("styles", picked);
        out.put("results", results);
        return out;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty()) return q;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }

    // ------------------------------------------------------------- estáticos
    private void handleStatic(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "Método no permitido.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = resourceName(ex.getRequestURI().getPath());
        try (InputStream in = name == null ? null : Main.class.getResourceAsStream("/static/" + name)) {
            if (in == null) {
                send(ex, 404, "text/plain; charset=utf-8", "No encontrado.".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String ext = name.substring(name.lastIndexOf('.') + 1);
            send(ex, 200, CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    /** Convierte la ruta pedida en un nombre de archivo dentro de /static, o null si no es seguro. */
    static String resourceName(String path) {
        String name = path.equals("/") ? "index.html" : path.substring(1);
        if (name.isEmpty() || name.contains("..") || name.contains("//") || name.startsWith("/")
                || !name.matches("[A-Za-z0-9_./-]+") || name.endsWith("/") || !name.contains(".")) return null;
        return name;
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}
