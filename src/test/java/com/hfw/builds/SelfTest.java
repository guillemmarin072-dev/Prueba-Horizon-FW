package com.hfw.builds;

import com.sun.net.httpserver.HttpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pruebas sin dependencias: se ejecutan con ./test.sh (o test.bat). Termina con código distinto de 0 si algo falla. */
public final class SelfTest {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FALLO: " + what); }
    }

    private static void eq(Object actual, Object expected, String what) {
        check(java.util.Objects.equals(actual, expected), what + " (esperado " + expected + ", obtenido " + actual + ")");
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") private static List<Object> arr(Object o) { return (List<Object>) o; }

    public static void main(String[] args) throws Exception {
        json();
        catalog();
        recommender();
        api();
        System.out.println(failures == 0 ? "TODO OK (" + checks + " comprobaciones)" : failures + " fallos de " + checks + " comprobaciones");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- JSON
    private static void json() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texto", "Comillas \" barra \\ salto\n tab\t acentos áéñ élite ✓");
        m.put("lista", List.of(1L, 2.5, true, false));
        m.put("nulo", null);
        String s = Json.write(m);
        Object back = Json.parse(s);
        eq(back, Json.parse(s), "parse(write(x)) estable");
        eq(obj(back).get("texto"), m.get("texto"), "texto con caracteres especiales");
        eq(obj(back).get("lista"), List.of(1L, 2.5, true, false), "lista mixta");
        check(obj(back).containsKey("nulo") && obj(back).get("nulo") == null, "null conservado");
        eq(Json.parse("\"\\u00e9\\n\""), "é\n", "escape \\u");
        eq(Json.write(new Model.Surge("a", "S", "d", true)), "{\"name\":\"a\",\"tier\":\"S\",\"description\":\"d\",\"isPrimary\":true}", "record -> JSON");
        for (String bad : new String[]{"{", "[1,", "{\"a\" 1}", "tru", "\"abc", "[1] x", ""}) {
            boolean threw = false;
            try { Json.parse(bad); } catch (IllegalArgumentException e) { threw = true; }
            check(threw, "JSON inválido rechazado: '" + bad + "'");
        }
    }

    // ------------------------------------------------------------- catálogo
    private static void catalog() {
        Catalog c = Catalog.load(); // valida todas las referencias del JSON al cargar
        eq(c.buildCount(), 30, "builds cargadas");
        Set<String> used = new HashSet<>();
        for (Model.GameClass cls : c.meta().classes())
            for (Model.Phase ph : c.meta().phases()) {
                Model.BuildDetail b = c.getBuild(cls.code(), ph.code()).orElse(null);
                check(b != null, "existe " + cls.code() + "/" + ph.code());
                if (b == null) continue;
                String k = cls.code() + "/" + ph.code();
                check(b.items().size() >= 3, k + ": al menos 3 objetos");
                check(new HashSet<>(b.items()).size() == b.items().size(), k + ": objetos sin repetir");
                check(b.valorSurges().stream().filter(Model.Surge::isPrimary).count() == 1, k + ": exactamente un Valor Surge principal");
                check(b.valorSurges().get(0).isPrimary(), k + ": el principal va primero");
                check(!b.skills().isEmpty() || b.skillNote() != null, k + ": habilidades o nota");
                b.items().forEach(i -> used.add(i.name()));
            }
        eq(c.itemCount(), 162, "objetos en el catálogo");
        check(used.size() >= 70, "objetos usados por alguna build: " + used.size());

        // Catálogo de equipo: orígenes, tipos y stats de las capturas
        eq(c.listItems(null, null, "ngplus", null).size(), 8, "8 armas de New Game+");
        eq(c.listItems(null, null, "dlc", null).size(), 15, "15 objetos de Burning Shores");
        eq(c.listItems("Outfit", null, null, null).size(), 49, "49 armaduras");
        eq(c.listItems(null, null, null, "BLAST forge").stream().map(Model.Item::name).toList(), List.of("The Blast Forge"), "búsqueda por nombre sin distinguir mayúsculas");
        eq(c.listItems("Spear", null, null, null).size(), 3, "3 lanzas");
        eq(c.listItems(null, null, null, null).stream().filter(i -> !i.stats().isEmpty()).count(), 21L, "21 objetos con stats de las capturas");
        eq(c.listItems(null, null, null, null).stream().filter(i -> i.statsLevel() != null).count(), 20L, "20 con nivel de stats (el Specter Gauntlet no tiene)");
        eq(c.listItems(null, null, null, null).stream().filter(i -> !i.baseStats().isEmpty()).count(), 4L, "4 objetos con stats de nivel 0 y nivel 5");
        check(c.listItems(null, null, null, "gravesinger's").get(0).howToGet().contains("3000 Metal Shards"), "coste de Gravesinger's Lament");
        check(c.listItems(null, null, null, "quen marine").get(0).baseStats().get(0).contains("cuerpo a cuerpo 34"), "Quen Marine nivel 0");
        Model.Item blast = c.listItems(null, null, null, "The Blast Forge").get(0);
        eq(blast.origin(), "arena", "The Blast Forge viene de la Arena");
        eq(blast.statsLevel(), "max", "The Blast Forge a nivel 5");
        check(blast.stats().contains("+65% velocidad de carga"), "stats de The Blast Forge");
        eq(c.listItems(null, null, null, "quen marine").get(0).armorType(), "Infiltrator", "Quen Marine es de infiltrador");
        check(c.listItems(null, null, null, "tears of the").get(0).statsLevel().equals("base"), "Tears of the Land-god es del nivel base");

        // Un objeto mal definido debe rechazarse al cargar.
        for (String[] bad : new String[][]{{"origin", "luna"}, {"rarity", "Mítico"}, {"armorType", "Hunter"}}) {
            Map<String, Object> m = obj(Json.parse(new String(readResource("/hfw-data.json"), java.nio.charset.StandardCharsets.UTF_8)));
            String target = bad[0].equals("armorType") ? "Fire Hunter Bow" : "Nora Sentinel";
            obj(obj(m.get("items")).get(target)).put(bad[0], bad[1]);
            try { new Catalog(m); check(false, "objeto con " + bad[0] + " no válido debería rechazarse"); }
            catch (IllegalStateException e) { check(e.getMessage().contains(target), "error de objeto con " + bad[0] + ": " + e.getMessage()); }
        }

        // Un JSON mal editado debe fallar al cargar con un mensaje claro.
        Map<String, Object> broken = obj(Json.parse(new String(readResource("/hfw-data.json"), java.nio.charset.StandardCharsets.UTF_8)));
        obj(arr(broken.get("builds")).get(0)).put("items", List.of("Objeto que no existe"));
        try { new Catalog(broken); check(false, "referencia rota debería rechazarse"); }
        catch (IllegalStateException e) { check(e.getMessage().contains("Objeto que no existe"), "mensaje de error útil: " + e.getMessage()); }
    }

    private static byte[] readResource(String name) {
        try (var in = SelfTest.class.getResourceAsStream(name)) { return in.readAllBytes(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    // --------------------------------------------------------- recomendador
    private static void recommender() {
        List<Model.GameClass> cl = Catalog.load().meta().classes();
        java.util.function.Function<String[], String> top = st -> Recommender.rank(cl, List.of(st)).get(0).code();
        eq(top.apply(new String[]{"stealth"}), "infiltrator", "sigilo");
        eq(top.apply(new String[]{"melee"}), "warrior", "cuerpo a cuerpo");
        eq(top.apply(new String[]{"traps"}), "trapper", "trampas");
        eq(top.apply(new String[]{"machines"}), "machine-master", "máquinas");
        eq(top.apply(new String[]{"defensive"}), "survivor", "aguante");
        eq(top.apply(new String[]{"ranged"}), "hunter", "distancia");
        var r = Recommender.rank(cl, List.of("stealth", "ranged"));
        eq(r.stream().map(Recommender.Ranked::code).toList(),
                List.of("infiltrator", "hunter", "trapper", "warrior", "survivor", "machine-master"), "orden con sigilo+distancia");
        eq(r.get(0).match(), 83, "porcentaje 5/6");
        eq(r.get(1).match(), 67, "porcentaje 4/6");
        eq(Recommender.rank(cl, List.of()).get(0).match(), null, "sin estilos no hay porcentaje");
        eq(Recommender.rank(cl, List.of("stealth")), Recommender.rank(cl, List.of("stealth", "stealth")), "estilos repetidos");
    }

    // ------------------------------------------------------------------ API
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static int port;

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void api() throws Exception {
        HttpServer server = Main.start(0);
        port = server.getAddress().getPort();
        try {
            var r = get("/api/meta");
            eq(r.statusCode(), 200, "/api/meta");
            check(r.headers().firstValue("Content-Type").orElse("").contains("application/json"), "meta: Content-Type JSON");
            var meta = obj(Json.parse(r.body()));
            eq(arr(meta.get("classes")).size(), 6, "6 clases");
            eq(arr(meta.get("phases")).size(), 5, "5 fases");
            eq(arr(meta.get("styles")).size(), 6, "6 estilos");

            r = get("/api/recommend?phase=late&styles=stealth,ranged");
            eq(r.statusCode(), 200, "recommend");
            var rec = obj(Json.parse(r.body()));
            var results = arr(rec.get("results"));
            eq(results.size(), 6, "recommend: 6 resultados");
            eq(obj(obj(results.get(0)).get("class")).get("code"), "infiltrator", "recommend: la primera es infiltrator");
            check(results.stream().allMatch(x -> obj(x).get("build") != null), "recommend: todas con build");
            check(r.body().contains("élite"), "acentos correctos en la respuesta");

            r = get("/api/recommend?phase=early");
            eq(obj(arr(obj(Json.parse(r.body())).get("results")).get(0)).get("match"), null, "recommend sin estilos: match null");

            eq(get("/api/recommend?phase=nope").statusCode(), 400, "fase no válida -> 400");
            eq(get("/api/recommend?phase=early&styles=volar").statusCode(), 400, "estilo no válido -> 400");
            eq(obj(Json.parse(get("/api/recommend?phase=early&styles=volar,saltar").body())).get("error"), "Estilos no válidos: volar, saltar", "mensaje de error");

            int n = 0;
            for (String cls : List.of("hunter", "warrior", "survivor", "machine-master", "infiltrator", "trapper"))
                for (String ph : List.of("early", "mid", "late", "post", "ngplus")) {
                    r = get("/api/builds/" + cls + "/" + ph);
                    eq(r.statusCode(), 200, "build " + cls + "/" + ph);
                    var b = obj(Json.parse(r.body()));
                    check(arr(b.get("items")).size() >= 3 && !arr(b.get("valorSurges")).isEmpty(), "build completa " + cls + "/" + ph);
                    n++;
                }
            eq(n, 30, "30 builds servidas");
            var items = obj(Json.parse(get("/api/items").body()));
            eq(arr(items.get("items")).size(), 162, "/api/items: 162 objetos");
            eq(arr(obj(Json.parse(get("/api/items?origin=ngplus").body())).get("items")).size(), 8, "/api/items?origin=ngplus: 8");
            eq(arr(obj(Json.parse(get("/api/items?category=Boltblaster&rarity=Legendary").body())).get("items")).size(), 3, "/api/items: boltblasters legendarios");
            eq(arr(obj(Json.parse(get("/api/items?q=zzzz").body())).get("items")).size(), 0, "/api/items: sin resultados");
            var ng = obj(Json.parse(get("/api/items?q=Eye%20of%20the%20Storm").body()));
            eq(obj(arr(ng.get("items")).get(0)).get("statsLevel"), "max", "/api/items: statsLevel serializado");
            eq(arr(obj(arr(ng.get("items")).get(0)).get("stats")).size(), 4, "/api/items: stats serializados");
            eq(obj(arr(obj(Json.parse(get("/api/items?q=Tow%20Bow").body())).get("items")).get(0)).get("armorType"), null, "/api/items: armorType null en armas");
            eq(get("/api/builds/hunter/xxx").statusCode(), 404, "build inexistente -> 404");
            eq(get("/api/nada").statusCode(), 404, "ruta de API desconocida -> 404");
            eq(arr(Json.parse(get("/api/valor-surges").body())).size(), 12, "12 Valor Surges");

            var home = get("/");
            eq(home.statusCode(), 200, "página principal");
            check(home.body().contains("Horizon Forbidden West") && home.headers().firstValue("Content-Type").orElse("").startsWith("text/html"), "index.html");
            check(get("/app.js").headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"), "app.js con su tipo");
            check(get("/styles.css").headers().firstValue("Content-Type").orElse("").startsWith("text/css"), "styles.css con su tipo");
            // Cada imagen que usan app.js y styles.css debe existir y servirse como WebP.
            String js = get("/app.js").body() + get("/styles.css").body();
            var imgs = new java.util.TreeSet<String>();
            var m = java.util.regex.Pattern.compile("img/[a-z-]+\\.webp").matcher(js);
            while (m.find()) imgs.add(m.group());
            eq(imgs.size(), 13, "imágenes de fondo referenciadas");
            for (String img : imgs) {
                var ir = get("/" + img);
                check(ir.statusCode() == 200 && ir.headers().firstValue("Content-Type").orElse("").equals("image/webp"), "imagen servida como WebP: " + img);
            }
            eq(get("/no-existe.js").statusCode(), 404, "estático inexistente -> 404");
            eq(get("/hfw-data.json").statusCode(), 404, "el JSON de datos no se publica como estático");

            // Rutas peligrosas: nunca deben resolver a un archivo
            for (String p : new String[]{"/../Main.class", "/static/../hfw-data.json", "//etc/passwd", "/a/../../b.js", "/x%2e%2e/y.js", "/con%20espacio.js"})
                check(Main.resourceName(p) == null, "ruta rechazada: " + p);
            eq(Main.resourceName("/"), "index.html", "raíz -> index.html");
            eq(Main.resourceName("/styles.css"), "styles.css", "ruta normal");
        } finally {
            server.stop(0);
        }
    }
}
