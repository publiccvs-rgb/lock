package dev.lock.core.module;

import java.util.List;
import java.util.Locale;

/**
 * A typed, validated module setting. Values are stored in profiles as plain JSON values;
 * {@link #validate(Object)} turns any stored value (possibly hand-edited or from an older
 * version) into a legal value, falling back to the default instead of throwing.
 */
public abstract sealed class Setting<T> permits Setting.Bool, Setting.Num, Setting.Color, Setting.Choice, Setting.Text, Setting.Key {
    public final String id;
    public final String name;
    public final String description;
    public final T defaultValue;
    /** Optional grouping inside a module's settings panel ("Layout", "Appearance", ...). */
    public String group = "";

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
    }

    public abstract T validate(Object raw);

    /** JSON representation of a (valid) value. */
    public Object toJson(T value) { return value; }

    @SuppressWarnings("unchecked")
    public Object toJsonUnchecked(Object value) { return toJson((T) value); }

    public abstract String kind();

    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, String name, String desc, boolean def) { super(id, name, desc, def); }

        @Override public Boolean validate(Object raw) {
            if (raw instanceof Boolean b) return b;
            if (raw instanceof String s) {
                if (s.equalsIgnoreCase("true")) return true;
                if (s.equalsIgnoreCase("false")) return false;
            }
            return defaultValue;
        }

        @Override public String kind() { return "bool"; }
    }

    public static final class Num extends Setting<Double> {
        public final double min, max, step;

        public Num(String id, String name, String desc, double def, double min, double max, double step) {
            super(id, name, desc, def);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override public Double validate(Object raw) {
            double v;
            if (raw instanceof Number n) v = n.doubleValue();
            else if (raw instanceof String s) {
                try { v = Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return defaultValue; }
            } else return defaultValue;
            if (Double.isNaN(v) || Double.isInfinite(v)) return defaultValue;
            v = Math.max(min, Math.min(max, v));
            if (step > 0) v = min + Math.round((v - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            return Math.round(v * 1e6) / 1e6;
        }

        public boolean isInteger() { return step >= 1 && step == Math.rint(step) && min == Math.rint(min); }

        public String format(double v) {
            return isInteger() ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", v);
        }

        @Override public String kind() { return "number"; }
    }

    /** ARGB colour stored as "#AARRGGBB". */
    public static final class Color extends Setting<Integer> {
        public Color(String id, String name, String desc, int argb) { super(id, name, desc, argb); }

        @Override public Integer validate(Object raw) {
            if (raw instanceof Number n) return (int) n.longValue();
            if (raw instanceof String s) {
                Integer p = parse(s);
                if (p != null) return p;
            }
            return defaultValue;
        }

        public static Integer parse(String s) {
            s = s.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            if (s.length() != 6 && s.length() != 8) return null;
            try {
                long v = Long.parseLong(s, 16);
                if (s.length() == 6) v |= 0xFF000000L;
                return (int) v;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public static String format(int argb) { return String.format("#%08X", argb); }

        @Override public Object toJson(Integer value) { return format(value); }

        @Override public String kind() { return "color"; }
    }

    public static final class Choice extends Setting<String> {
        public final List<String> options;

        public Choice(String id, String name, String desc, String def, List<String> options) {
            super(id, name, desc, def);
            this.options = List.copyOf(options);
            if (!this.options.contains(def)) throw new IllegalArgumentException(id + ": default not in options");
        }

        @Override public String validate(Object raw) {
            if (raw instanceof String s) for (String o : options) if (o.equalsIgnoreCase(s)) return o;
            return defaultValue;
        }

        public String next(String current, int dir) {
            int i = options.indexOf(current);
            if (i < 0) i = 0;
            return options.get(Math.floorMod(i + dir, options.size()));
        }

        @Override public String kind() { return "choice"; }
    }

    public static final class Text extends Setting<String> {
        public final int maxLength;

        public Text(String id, String name, String desc, String def, int maxLength) {
            super(id, name, desc, def);
            this.maxLength = maxLength;
        }

        @Override public String validate(Object raw) {
            if (!(raw instanceof String s)) return defaultValue;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length() && sb.length() < maxLength; i++) {
                char c = s.charAt(i);
                if (c >= 0x20 && c != 0x7F && c != '§') sb.append(c);
            }
            return sb.toString();
        }

        @Override public String kind() { return "text"; }
    }

    /** A keyboard/mouse binding stored as a GLFW key code; {@link Keys#NONE} means unbound. */
    public static final class Key extends Setting<Integer> {
        public Key(String id, String name, String desc, int def) { super(id, name, desc, def); }

        @Override public Integer validate(Object raw) {
            if (raw instanceof Number n) {
                int k = (int) n.longValue();
                return Keys.isValid(k) ? k : defaultValue;
            }
            if (raw instanceof String s) {
                Integer k = Keys.byName(s);
                return k == null ? defaultValue : k;
            }
            return defaultValue;
        }

        @Override public Object toJson(Integer value) { return Keys.name(value); }

        @Override public String kind() { return "key"; }
    }
}
