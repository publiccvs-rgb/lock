package lock.inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Mojang's official Proguard-format client mapping ("client.txt") and resolves official (Mojmap) class,
 * method and field names to the obfuscated short names actually shipped in a vanilla client.jar.
 *
 * File shape, one block per class:
 *   official.package.Name -> obf:
 *       [int:int:]returnType name(paramType,...) -> obfName        (method; line range is sometimes absent)
 *       type fieldName -> obfName                                   (field)
 *
 * This is the exact inverse of what a deobfuscator does: we go official -> obfuscated so patched bytecode can
 * target the real names the running (non-remapped) JVM uses.
 */
public final class Mapping {
    public record Clazz(String official, String obfuscated) {}
    public record Member(String obfuscated, String returnType, String[] paramTypes) {}

    private final Map<String, Clazz> classesByOfficial = new HashMap<>();
    private final Map<String, Clazz> classesByObfuscated = new HashMap<>();
    /** key: "official.Class#member(paramType,...)"; empty paramTypes for fields. */
    private final Map<String, Member> members = new HashMap<>();

    private static final Pattern CLASS_LINE = Pattern.compile("^(\\S+) -> (\\S+):$");
    private static final Pattern MEMBER_LINE = Pattern.compile("^ {4}(?:\\d+:\\d+:)?(\\S+) ([a-zA-Z_$][\\w$]*)\\(([^)]*)\\) -> (\\S+)$");
    private static final Pattern FIELD_LINE = Pattern.compile("^ {4}(\\S+) ([a-zA-Z_$][\\w$]*) -> (\\S+)$");

    public static Mapping parse(Reader source) throws IOException {
        Mapping m = new Mapping();
        try (BufferedReader r = new BufferedReader(source, 1 << 20)) {
            String line;
            Clazz current = null;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                if (line.charAt(0) != ' ') {
                    Matcher cm = CLASS_LINE.matcher(line);
                    if (cm.matches()) {
                        current = new Clazz(cm.group(1), cm.group(2));
                        m.classesByOfficial.put(current.official(), current);
                        m.classesByObfuscated.put(current.obfuscated(), current);
                    } else {
                        current = null;
                    }
                    continue;
                }
                if (current == null) continue;
                Matcher mm = MEMBER_LINE.matcher(line);
                if (mm.matches()) {
                    String ret = mm.group(1), name = mm.group(2), paramsRaw = mm.group(3), obf = mm.group(4);
                    String[] params = paramsRaw.isEmpty() ? new String[0] : paramsRaw.split(",");
                    String key = current.official() + "#" + name + "(" + String.join(",", params) + ")";
                    m.members.put(key, new Member(obf, ret, params));
                    continue;
                }
                Matcher fm = FIELD_LINE.matcher(line);
                if (fm.matches()) {
                    String type = fm.group(1), name = fm.group(2), obf = fm.group(3);
                    String key = current.official() + "#" + name;
                    m.members.put(key, new Member(obf, type, null));
                }
            }
        }
        return m;
    }

    public Clazz clazz(String officialDotted) { return classesByOfficial.get(officialDotted); }
    public Clazz classByObf(String obfDotted) { return classesByObfuscated.get(obfDotted); }

    public Member method(String officialClass, String name, String... officialParamTypes) {
        return members.get(officialClass + "#" + name + "(" + String.join(",", officialParamTypes) + ")");
    }

    public Member field(String officialClass, String name) { return members.get(officialClass + "#" + name); }

    /** Internal JVM form of an obfuscated or unmapped type name ("int" -> "I", "pkg.Cls" -> "Lpkg/Cls;", "T[]" -> "[..."). */
    public String obfDescriptorType(String officialType) {
        int arr = 0;
        String t = officialType;
        while (t.endsWith("[]")) { arr++; t = t.substring(0, t.length() - 2); }
        String prim = switch (t) {
            case "void" -> "V"; case "boolean" -> "Z"; case "byte" -> "B"; case "char" -> "C";
            case "short" -> "S"; case "int" -> "I"; case "long" -> "J"; case "float" -> "F"; case "double" -> "D";
            default -> null;
        };
        String core;
        if (prim != null) core = prim;
        else {
            Clazz c = classesByOfficial.get(t);
            core = "L" + (c != null ? c.obfuscated() : t).replace('.', '/') + ";";
        }
        return "[".repeat(arr) + core;
    }

    /** Builds a full JVM method descriptor, e.g. "(Lnet/minecraft/world/entity/Entity;)Z", in obfuscated terms. */
    public String obfMethodDescriptor(Member m) {
        StringBuilder sb = new StringBuilder("(");
        if (m.paramTypes() != null) for (String p : m.paramTypes()) sb.append(obfDescriptorType(p));
        sb.append(')').append(obfDescriptorType(m.returnType()));
        return sb.toString();
    }

    public int classCount() { return classesByOfficial.size(); }
    public int memberCount() { return members.size(); }
}
