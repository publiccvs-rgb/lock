package lock.module;

import com.google.gson.*;
import lock.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ModuleManager {
    public final General general = new General();
    public final Boxes boxes = new Boxes();
    public final Tracers tracers = new Tracers();
    public final Charms charms = new Charms();
    public final Outlines outlines = new Outlines();
    public final StorageEsp storage = new StorageEsp();
    public final Health health = new Health();
    public final ItemEsp items = new ItemEsp();
    public final Fullbright fullbright = new Fullbright();

    public final List<Module> all = List.of(general, boxes, tracers, charms, outlines, storage, health, items, fullbright);

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("lock.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void tick() { for (Module m : all) if (m.enabled()) m.onTick(); }

    public void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
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
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
        } catch (Exception ignored) {}
    }
}
