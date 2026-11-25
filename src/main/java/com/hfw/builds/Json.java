package com.hfw.builds;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON mínimo sin dependencias: lee a Map/List/String/Long/Double/Boolean/null y escribe Map, List, records y valores simples. */
final class Json {
    private Json() {}

    // ------------------------------------------------------------ lectura
    static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw p.error("Texto sobrante después del JSON");
        return v;
    }

    private static final class Parser {
        final String s;
        int i = 0;

        Parser(String s) { this.s = s; }

        IllegalArgumentException error(String msg) { return new IllegalArgumentException(msg + " (posición " + i + ")"); }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw error("El JSON termina antes de tiempo");
            return s.charAt(i);
        }

        void expect(char c) {
            if (peek() != c) throw error("Se esperaba '" + c + "'");
            i++;
        }

        void literal(String word) {
            if (!s.startsWith(word, i)) throw error("Valor no válido");
            i += word.length();
        }

        Object value() {
            switch (peek()) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default: return number();
            }
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                ws();
                m.put(key, value());
                ws();
                if (peek() == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            expect('[');
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                if (peek() == ',') { i++; continue; }
                expect(']');
                return l;
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = peek();
                i++;
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("Escape \\u incompleto");
                        try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                        catch (NumberFormatException ex) { throw error("Escape \\u no válido"); }
                        i += 4;
                        break;
                    default: throw error("Escape no válido");
                }
            }
        }

        Object number() {
            int start = i;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw error("Valor no válido");
            try {
                return t.matches("-?\\d+") ? (Object) Long.parseLong(t) : (Object) Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                i = start;
                throw error("Número no válido");
            }
        }
    }

    // ---------------------------------------------------------- escritura
    static String write(Object value) {
        StringBuilder b = new StringBuilder();
        write(value, b);
        return b.toString();
    }

    private static void write(Object o, StringBuilder b) {
        if (o == null) {
            b.append("null");
        } else if (o instanceof String s) {
            quote(s, b);
        } else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) {
            b.append(o);
        } else if (o instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) throw new IllegalArgumentException("Número no representable en JSON");
            b.append(d);
        } else if (o instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), b);
                b.append(':');
                write(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof Iterable<?> it) {
            b.append('[');
            boolean first = true;
            for (Object x : it) {
                if (!first) b.append(',');
                first = false;
                write(x, b);
            }
            b.append(']');
        } else if (o.getClass().isRecord()) {
            b.append('{');
            boolean first = true;
            for (RecordComponent rc : o.getClass().getRecordComponents()) {
                if (!first) b.append(',');
                first = false;
                quote(rc.getName(), b);
                b.append(':');
                try {
                    Method accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    write(accessor.invoke(o), b);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("No se pudo leer " + rc.getName(), e);
                }
            }
            b.append('}');
        } else {
            throw new IllegalArgumentException("Tipo no serializable: " + o.getClass().getName());
        }
    }

    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
