package dev.lock.core;

import dev.lock.core.json.Json;
import dev.lock.core.json.JsonException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void roundTripPreservesOrderAndTypes() {
        String src = "{\"b\":1,\"a\":[true,null,2.5,\"x\\n\\\"q\\\"\"],\"c\":{\"z\":-3}}";
        Map<String, Object> m = Json.parseObject(src);
        assertEquals(List.of("b", "a", "c"), List.copyOf(m.keySet()));
        assertEquals(1L, m.get("b"));
        assertEquals(Json.parse(Json.write(m)), m);
        assertEquals(m, Json.parse(Json.pretty(m)));
    }

    @Test
    void unicodeEscapes() {
        assertEquals("\u00B0C", Json.parse("\"\\u00b0C\""));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(JsonException.class, () -> Json.parse("[1,2"));
        assertThrows(JsonException.class, () -> Json.parse("{} trailing"));
    }
}
