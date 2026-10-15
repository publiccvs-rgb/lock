package dev.lock.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed Mojang/ProGuard mappings. Holds both directions:
 * {@link #toObf} maps Mojang ("named") names to obfuscated names, {@link #toNamed} the reverse.
 * Class names are internal names ({@code net/minecraft/client/Minecraft}).
 */
public final class Mappings {
    /** One direction of the mapping: source-namespace names to target-namespace names. */
    public static final class Direction {
        public final Map<String, String> classes = new HashMap<>();
        /** owner -> (name + ":" + desc) -> mapped name */
        public final Map<String, Map<String, String>> fields = new HashMap<>();
        /** owner -> (name + desc) -> mapped name */
        public final Map<String, Map<String, String>> methods = new HashMap<>();

        public String mapClass(String internal) { return classes.get(internal); }

        public String field(String owner, String name, String desc) {
            Map<String, String> m = fields.get(owner);
            return m == null ? null : m.get(name + ":" + desc);
        }

        /** Field lookup ignoring the descriptor (for callers that only know the name). */
        public String fieldByName(String owner, String name) {
            Map<String, String> m = fields.get(owner);
            if (m == null) return null;
            for (Map.Entry<String, String> e : m.entrySet())
                if (e.getKey().startsWith(name + ":")) return e.getValue();
            return null;
        }

        public String method(String owner, String name, String desc) {
            Map<String, String> m = methods.get(owner);
            return m == null ? null : m.get(name + desc);
        }

        /** Maps every class reference in a method or field descriptor. */
        public String mapDesc(String desc) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < desc.length()) {
                char c = desc.charAt(i);
                if (c == 'L') {
                    int end = desc.indexOf(';', i);
                    String cls = desc.substring(i + 1, end);
                    String m = classes.get(cls);
                    sb.append('L').append(m != null ? m : cls).append(';');
                    i = end + 1;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        }
    }

    public final Direction toObf = new Direction();
    public final Direction toNamed = new Direction();

    public static Mappings parse(Reader reader) throws IOException {
        Mappings m = new Mappings();
        // First pass collects classes (descriptors need the full class map), second pass members.
        java.util.List<String[]> members = new java.util.ArrayList<>();
        BufferedReader br = new BufferedReader(reader);
        String line, currentNamed = null, currentObf = null;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.startsWith(" ")) {
                int arrow = line.indexOf(" -> ");
                if (arrow < 0 || !line.endsWith(":")) continue;
                currentNamed = line.substring(0, arrow).replace('.', '/');
                currentObf = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
                m.toObf.classes.put(currentNamed, currentObf);
                m.toNamed.classes.put(currentObf, currentNamed);
            } else if (currentNamed != null) {
                String t = line.trim();
                if (t.startsWith("#")) continue;
                members.add(new String[]{currentNamed, currentObf, t});
            }
        }
        for (String[] e : members) m.addMember(e[0], e[1], e[2]);
        return m;
    }

    private void addMember(String ownerNamed, String ownerObf, String t) {
        int arrow = t.indexOf(" -> ");
        if (arrow < 0) return;
        String left = t.substring(0, arrow);
        String obfName = t.substring(arrow + 4).trim();
        // strip leading "12:34:" line numbers
        while (!left.isEmpty() && Character.isDigit(left.charAt(0))) {
            int colon = left.indexOf(':');
            if (colon < 0) break;
            left = left.substring(colon + 1);
        }
        int space = left.indexOf(' ');
        if (space < 0) return;
        String type = left.substring(0, space);
        String rest = left.substring(space + 1);
        int paren = rest.indexOf('(');
        if (paren < 0) {
            String desc = typeToDesc(type);
            toObf.fields.computeIfAbsent(ownerNamed, k -> new HashMap<>()).put(rest + ":" + desc, obfName);
            toNamed.fields.computeIfAbsent(ownerObf, k -> new HashMap<>()).put(obfName + ":" + toObf.mapDesc(desc), rest);
        } else {
            String name = rest.substring(0, paren);
            int close = rest.indexOf(')', paren);
            String args = rest.substring(paren + 1, close);
            StringBuilder d = new StringBuilder("(");
            if (!args.isEmpty()) for (String a : args.split(",")) d.append(typeToDesc(a));
            d.append(')').append(typeToDesc(type));
            String desc = d.toString();
            if (name.equals("<init>") || name.equals("<clinit>")) return;
            toObf.methods.computeIfAbsent(ownerNamed, k -> new HashMap<>()).put(name + desc, obfName);
            toNamed.methods.computeIfAbsent(ownerObf, k -> new HashMap<>()).put(obfName + toObf.mapDesc(desc), name);
        }
    }

    static String typeToDesc(String type) {
        int dims = 0;
        while (type.endsWith("[]")) {
            dims++;
            type = type.substring(0, type.length() - 2);
        }
        String base = switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + type.replace('.', '/') + ";";
        };
        return "[".repeat(dims) + base;
    }
}
