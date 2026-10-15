package lock.module;

import com.google.gson.*;
import lock.setting.Setting;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    public final General general = new General();
    public final Boxes boxes = new Boxes();
    public final Tracers tracers = new Tracers();
    public final Charms charms = new Charms();
    public final Outlines outlines = new Outlines();
    public final StorageEsp storage = new StorageEsp();
    public final NameTags nameTags = new NameTags();
    public final BaseFinder baseFinder = new BaseFinder();
    public final Ambience ambience = new Ambience();
    public final Freecam freecam = new Freecam();
    public final Zoom zoom = new Zoom();
    public final Glint glint = new Glint();
    public final Arrows arrows = new Arrows();
    public final ItemEsp items = new ItemEsp();
    public final Fullbright fullbright = new Fullbright();

    /** Menu panel layout: id -> {x, y, collapsed(0/1)}. */
    public final Map<String, float[]> guiLayout = new LinkedHashMap<>();

    public final List<Module> all = List.of(general, boxes, tracers, charms, outlines, storage, nameTags, items, fullbright, baseFinder, ambience, freecam, zoom, glint, arrows);

    private final Path file = configFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configFile() {
        Path dir = Path.of(System.getProperty("user.home"), ".lock");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir.resolve("config.json");
    }

    public void tick() { for (Module m : all) if (m.enabled()) m.onTick(); }

    public void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("_gui")) {
                for (var e : root.getAsJsonObject("_gui").entrySet()) {
                    JsonArray a = e.getValue().getAsJsonArray();
                    guiLayout.put(e.getKey(), new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
                }
            }
            for (Module m : all) {
                if (!root.has(m.name)) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
                for (Setting<?> s : m.settings) {
                    if (o.has(s.name)) {
                        try { s.load(o.get(s.name)); } catch (RuntimeException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Module m : all) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled());
            for (Setting<?> s : m.settings) o.add(s.name, s.save());
            root.add(m.name, o);
        }
        JsonObject gui = new JsonObject();
        for (var e : guiLayout.entrySet()) {
            JsonArray a = new JsonArray();
            for (float f : e.getValue()) a.add(f);
            gui.add(e.getKey(), a);
        }
        root.add("_gui", gui);
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
        } catch (Exception ignored) {}
    }
}
