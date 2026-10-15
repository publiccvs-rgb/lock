package dev.lock.agent;

import dev.lock.core.util.Log;
import dev.lock.core.util.OfficialMappings;
import dev.lock.mapping.Mappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** Loads the official mappings, downloading them first if the launcher has not already done so. */
public final class MappingProvider {
    private MappingProvider() {}

    public static Mappings load(String version, Map<String, Object> info) throws IOException {
        if (!OfficialMappings.VERSION.equals(version)) throw new IOException("no mappings known for " + version);
        long t0 = System.nanoTime();
        if (!OfficialMappings.present()) {
            Log.info("downloading official mappings for " + version);
            OfficialMappings.download();
            info.put("mappingsSource", "downloaded");
        } else info.put("mappingsSource", "cache");
        Mappings m;
        try (var r = Files.newBufferedReader(OfficialMappings.cached(), StandardCharsets.UTF_8)) {
            m = Mappings.parse(r);
        }
        info.put("mappingsClasses", m.toObf.classes.size());
        info.put("mappingsLoadMs", (System.nanoTime() - t0) / 1_000_000);
        return m;
    }
}
