package com.hfw.builds;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Datos de la aplicación, cargados en memoria desde hfw-data.json.
 * Al cargar se valida que todas las referencias existan, para que un error al editar el JSON se vea al arrancar
 * y no al consultar una build concreta.
 */
final class Catalog {
    private static final Set<String> TIERS = Set.of("SS", "S", "A", "B");
    private static final Set<String> RARITIES = Set.of("Uncommon", "Rare", "Very Rare", "Legendary");
    private static final Set<String> ORIGINS = Set.of("base", "arena", "ngplus", "dlc", "preorder");
    private static final Set<String> ARMOR_TYPES = Set.of("Hunter", "Warrior", "Infiltrator", "Survivor", "Trapper", "Machine Master", "Mixed Gear");
    private static final Map<String, Integer> TIER_ORDER = Map.of("SS", 0, "S", 1, "A", 2, "B", 3);

    private final String sourceUrl;
    private final List<Model.Phase> phases = new ArrayList<>();
    private final List<Model.Style> styles = new ArrayList<>();
    private final List<Model.GameClass> classes = new ArrayList<>();
    private final Map<String, String> skillClass = new LinkedHashMap<>();
    private final Map<String, Model.SurgeListing> surges = new LinkedHashMap<>();
    private final Map<String, Model.Item> items = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> builds = new LinkedHashMap<>(); // clave "clase/fase"

    static Catalog load() {
        try (InputStream in = Catalog.class.getResourceAsStream("/hfw-data.json")) {
            if (in == null) throw new IllegalStateException("No se encuentra hfw-data.json en el classpath");
            return new Catalog(obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer hfw-data.json", e);
        }
    }

    Catalog(Map<String, Object> d) {
        sourceUrl = str(d.get("SOURCE_URL"), "SOURCE_URL");

        for (Object o : arr(d.get("phases"))) {
            Map<String, Object> p = obj(o);
            phases.add(new Model.Phase(str(p.get("code"), "fase.code"), str(p.get("name"), "fase.name"), str(p.get("hint"), "fase.hint")));
        }
        for (Object o : arr(d.get("styles"))) {
            Map<String, Object> s = obj(o);
            styles.add(new Model.Style(str(s.get("code"), "estilo.code"), str(s.get("name"), "estilo.name")));
        }
        Set<String> styleCodes = new java.util.HashSet<>();
        styles.forEach(s -> styleCodes.add(s.code()));

        Map<String, Object> weights = obj(d.get("classStyles"));
        for (Object o : arr(d.get("classes"))) {
            Map<String, Object> c = obj(o);
            String code = str(c.get("code"), "clase.code");
            Map<String, Integer> w = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : obj(weights.getOrDefault(code, Map.of())).entrySet()) {
                if (!styleCodes.contains(e.getKey())) throw bad("classStyles." + code + ": estilo desconocido '" + e.getKey() + "'");
                int n = ((Number) e.getValue()).intValue();
                if (n < 0 || n > 3) throw bad("classStyles." + code + "." + e.getKey() + ": el peso debe estar entre 0 y 3");
                w.put(e.getKey(), n);
            }
            classes.add(new Model.GameClass(code, str(c.get("name"), "clase.name"), str(c.get("tagline"), "clase.tagline"),
                    str(c.get("description"), "clase.description"), w));
        }
        Set<String> classCodes = new java.util.HashSet<>();
        classes.forEach(c -> classCodes.add(c.code()));
        Set<String> phaseCodes = new java.util.HashSet<>();
        phases.forEach(p -> phaseCodes.add(p.code()));

        for (Map.Entry<String, Object> e : obj(d.get("skills")).entrySet()) {
            if (!classCodes.contains(e.getKey())) throw bad("skills: clase desconocida '" + e.getKey() + "'");
            for (Object n : arr(e.getValue())) {
                if (skillClass.put(str(n, "habilidad"), e.getKey()) != null) throw bad("habilidad duplicada: " + n);
            }
        }

        for (Object o : arr(d.get("valorSurges"))) {
            Map<String, Object> v = obj(o);
            String name = str(v.get("name"), "valorSurge.name");
            String tier = str(v.get("tier"), name + ".tier");
            if (!TIERS.contains(tier)) throw bad("Valor Surge '" + name + "': nivel no válido '" + tier + "'");
            String cls = str(v.get("cls"), name + ".cls");
            if (!classCodes.contains(cls)) throw bad("Valor Surge '" + name + "': clase desconocida '" + cls + "'");
            surges.put(name, new Model.SurgeListing(name, tier, str(v.get("description"), name + ".description"), cls));
        }

        for (Map.Entry<String, Object> e : obj(d.get("items")).entrySet()) {
            String n = e.getKey();
            Map<String, Object> o = obj(e.getValue());
            String category = str(o.get("category"), n + ".category");
            String rarity = str(o.get("rarity"), n + ".rarity");
            String origin = str(o.get("origin"), n + ".origin");
            if (!RARITIES.contains(rarity)) throw bad("objeto '" + n + "': rareza no válida '" + rarity + "'");
            if (!ORIGINS.contains(origin)) throw bad("objeto '" + n + "': origen no válido '" + origin + "'");
            String armorType = o.get("armorType") == null ? null : str(o.get("armorType"), n + ".armorType");
            boolean outfit = category.equals("Outfit");
            if (outfit && (armorType == null || !ARMOR_TYPES.contains(armorType))) throw bad("objeto '" + n + "': una armadura necesita un armorType válido");
            if (!outfit && armorType != null) throw bad("objeto '" + n + "': solo las armaduras llevan armorType");
            String level = o.get("statsLevel") == null ? null : str(o.get("statsLevel"), n + ".statsLevel");
            if (level != null && !level.equals("max") && !level.equals("base")) throw bad("objeto '" + n + "': statsLevel debe ser max o base");
            List<String> stats = strings(o.getOrDefault("stats", List.of()));
            if (level != null && stats.isEmpty()) throw bad("objeto '" + n + "': tiene statsLevel pero no stats");
            List<String> baseStats = strings(o.getOrDefault("baseStats", List.of()));
            if (!baseStats.isEmpty() && stats.isEmpty()) throw bad("objeto '" + n + "': baseStats necesita también stats");
            String note = o.get("note") == null ? null : str(o.get("note"), n + ".note");
            items.put(n, new Model.Item(n, category, rarity, origin, armorType, str(o.get("howToGet"), n + ".howToGet"), level, stats, baseStats, note));
        }

        for (Object o : arr(d.get("builds"))) {
            Map<String, Object> b = obj(o);
            String cls = str(b.get("cls"), "build.cls");
            String phase = str(b.get("phase"), "build.phase");
            String key = cls + "/" + phase;
            if (!classCodes.contains(cls)) throw bad("build " + key + ": clase desconocida");
            if (!phaseCodes.contains(phase)) throw bad("build " + key + ": fase desconocida");
            if (builds.put(key, b) != null) throw bad("build duplicada: " + key);
            str(b.get("name"), key + ".name");
            str(b.get("summary"), key + ".summary");
            for (Object n : arr(b.get("items"))) if (!items.containsKey(n)) throw bad(key + ": objeto desconocido '" + n + "'");
            for (String f : new String[]{"skills", "follow"})
                for (Object n : arr(b.getOrDefault(f, List.of()))) if (!skillClass.containsKey(n)) throw bad(key + ": habilidad desconocida '" + n + "'");
            boolean primary = false;
            for (Object v : arr(b.get("valor"))) {
                List<Object> pair = arr(v);
                if (!surges.containsKey(pair.get(0))) throw bad(key + ": Valor Surge desconocido '" + pair.get(0) + "'");
                primary |= Boolean.TRUE.equals(pair.get(1));
            }
            if (!primary) throw bad(key + ": falta un Valor Surge principal (true)");
            if (arr(b.getOrDefault("skills", List.of())).isEmpty() && b.get("skillNote") == null)
                throw bad(key + ": indica habilidades o una skillNote");
        }
        for (Model.GameClass c : classes)
            for (Model.Phase p : phases)
                if (!builds.containsKey(c.code() + "/" + p.code())) throw bad("falta la build " + c.code() + "/" + p.code());
    }

    // ------------------------------------------------------------ consultas
    Model.Meta meta() {
        return new Model.Meta("memory", List.copyOf(phases), List.copyOf(styles), List.copyOf(classes));
    }

    List<Model.BuildSummary> listBuilds(String phaseCode) {
        List<Model.BuildSummary> out = new ArrayList<>();
        for (Model.GameClass c : classes) {
            Map<String, Object> b = builds.get(c.code() + "/" + phaseCode);
            if (b == null) continue;
            out.add(new Model.BuildSummary(c.code(), (String) b.get("name"), (String) b.get("summary"), primaryValor(b)));
        }
        return out;
    }

    Optional<Model.BuildDetail> getBuild(String classCode, String phaseCode) {
        Map<String, Object> b = builds.get(classCode + "/" + phaseCode);
        if (b == null) return Optional.empty();

        List<Model.Item> its = new ArrayList<>();
        for (Object n : arr(b.get("items"))) its.add(items.get(n));

        List<Model.Skill> sk = new ArrayList<>();
        for (Object n : arr(b.getOrDefault("skills", List.of()))) sk.add(new Model.Skill((String) n, skillClass.get(n), false));
        for (Object n : arr(b.getOrDefault("follow", List.of()))) sk.add(new Model.Skill((String) n, skillClass.get(n), true));

        List<Model.Surge> vs = new ArrayList<>();
        for (Object v : arr(b.get("valor"))) {
            List<Object> pair = arr(v);
            Model.SurgeListing s = surges.get(pair.get(0));
            vs.add(new Model.Surge(s.name(), s.tier(), s.description(), Boolean.TRUE.equals(pair.get(1))));
        }

        return Optional.of(new Model.BuildDetail(classCode, phaseCode, (String) b.get("name"), (String) b.get("summary"),
                (String) b.get("skillNote"), sourceUrl, its, sk, vs, strings(b.get("weaponMods")), strings(b.get("outfitMods"))));
    }

    List<Model.SurgeListing> valorSurges() {
        return surges.values().stream()
                .sorted(Comparator.<Model.SurgeListing>comparingInt(v -> TIER_ORDER.get(v.tier())).thenComparing(Model.SurgeListing::name))
                .toList();
    }

    int buildCount() { return builds.size(); }

    /** Equipo del catálogo, en el orden del JSON; los filtros vacíos o nulos no filtran. q busca en el nombre sin distinguir mayúsculas. */
    List<Model.Item> listItems(String category, String rarity, String origin, String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        return items.values().stream()
                .filter(i -> category == null || category.isEmpty() || i.category().equals(category))
                .filter(i -> rarity == null || rarity.isEmpty() || i.rarity().equals(rarity))
                .filter(i -> origin == null || origin.isEmpty() || i.origin().equals(origin))
                .filter(i -> needle.isEmpty() || i.name().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .toList();
    }

    int itemCount() { return items.size(); }

    // --------------------------------------------------------------- utilidades
    private static String primaryValor(Map<String, Object> b) {
        for (Object v : arr(b.get("valor"))) {
            List<Object> pair = arr(v);
            if (Boolean.TRUE.equals(pair.get(1))) return (String) pair.get(0);
        }
        return null;
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o != null) for (Object x : arr(o)) out.add((String) x);
        return out;
    }

    private static IllegalStateException bad(String msg) { return new IllegalStateException("hfw-data.json: " + msg); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        if (!(o instanceof Map)) throw bad("se esperaba un objeto { } y hay: " + o);
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> arr(Object o) {
        if (!(o instanceof List)) throw bad("se esperaba una lista [ ] y hay: " + o);
        return (List<Object>) o;
    }

    private static String str(Object o, String where) {
        if (!(o instanceof String s) || s.isBlank()) throw bad("falta el texto '" + where + "'");
        return s;
    }
}
