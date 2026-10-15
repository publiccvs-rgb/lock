package dev.lock.agent;

import dev.lock.mapping.Mappings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every hook must resolve against the real 1.21.11 mappings. Uses the dev environment created by
 * :mapping:setupMinecraft (skipped when it is absent, e.g. on a machine without network access).
 */
class HookSpecsTest {
    @Test
    void allHooksResolveAgainstOfficialMappings() throws Exception {
        Path mappings = Paths.get(System.getProperty("user.dir")).getParent().resolve(".mc-dev/1.21.11/client.txt");
        Assumptions.assumeTrue(Files.exists(mappings), "dev mappings not present");
        Mappings m;
        try (var r = Files.newBufferedReader(mappings)) {
            m = Mappings.parse(r);
        }
        var specs = HookSpecs.all(m);
        assertTrue(specs.size() >= 20);
        for (HookSpecs.Spec s : specs) {
            assertNotNull(m.toObf.mapClass(s.owner()), s.name() + ": class " + s.owner());
            assertNotNull(m.toObf.method(s.owner(), s.method(), s.desc()), s.name() + ": method " + s.method() + s.desc());
        }
        assertNotNull(m.toObf.field("net/minecraft/client/MouseHandler", "accumulatedDX", "D"));
    }
}
