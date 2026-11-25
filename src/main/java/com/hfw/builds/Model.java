package com.hfw.builds;

import java.util.List;
import java.util.Map;

/** Objetos que viajan por la API. Los nombres de campo coinciden con los que lee static/app.js. */
final class Model {
    private Model() {}

    record Phase(String code, String name, String hint) {}

    record Style(String code, String name) {}

    /** Clase (árbol de habilidades) con su afinidad 0-3 por estilo de juego. */
    record GameClass(String code, String name, String tagline, String description, Map<String, Integer> styles) {}

    record Meta(String source, List<Phase> phases, List<Style> styles, List<GameClass> classes) {}

    record BuildSummary(String classCode, String name, String summary, String primaryValor) {}

    /** Arma o armadura. origin: base, arena, ngplus, dlc o preorder. statsLevel: max, base o null (sin stats propios). */
    record Item(String name, String category, String rarity, String origin, String armorType, String howToGet,
                String statsLevel, List<String> stats, List<String> baseStats, String note) {}

    record Skill(String name, String classCode, boolean followUp) {}

    record Surge(String name, String tier, String description, boolean isPrimary) {}

    record BuildDetail(String classCode, String phaseCode, String name, String summary, String skillNote,
                       String sourceUrl, List<Item> items, List<Skill> skills, List<Surge> valorSurges,
                       List<String> weaponMods, List<String> outfitMods) {}

    record SurgeListing(String name, String tier, String description, String classCode) {}
}
