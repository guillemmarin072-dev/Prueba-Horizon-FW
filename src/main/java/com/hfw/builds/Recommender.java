package com.hfw.builds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Lógica de recomendación.
 * puntuación = suma de la afinidad (0-3) de la clase con cada estilo elegido.
 */
final class Recommender {
    private Recommender() {}

    record Ranked(String code, int score, Integer match) {}

    static List<Ranked> rank(List<Model.GameClass> classes, Collection<String> selectedStyles) {
        List<String> picked = new ArrayList<>(new LinkedHashSet<>(selectedStyles));
        int max = picked.size() * 3;

        record Row(int index, Model.GameClass cls, int score, int total) {}
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            Model.GameClass c = classes.get(i);
            int score = 0;
            for (String s : picked) score += c.styles().getOrDefault(s, 0);
            int total = c.styles().values().stream().mapToInt(Integer::intValue).sum();
            rows.add(new Row(i, c, score, total));
        }

        // Sin estilos: orden original y sin porcentaje.
        if (max == 0) return rows.stream().map(r -> new Ranked(r.cls().code(), r.score(), null)).toList();

        // Desempate: más afinidad total (clase más versátil) y luego el orden original.
        rows.sort(Comparator.<Row>comparingInt(r -> -r.score())
                .thenComparingInt(r -> -r.total())
                .thenComparingInt(Row::index));

        final int maxScore = max;
        return rows.stream()
                .map(r -> new Ranked(r.cls().code(), r.score(), (int) Math.round(r.score() * 100.0 / maxScore)))
                .toList();
    }
}
