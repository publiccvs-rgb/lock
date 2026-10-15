package dev.lock.core;

import dev.lock.core.module.Keys;
import dev.lock.core.module.Setting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingTest {
    @Test
    void numbersAreClampedAndStepped() {
        Setting.Num n = new Setting.Num("n", "N", "", 5, 0, 10, 0.5);
        assertEquals(10.0, n.validate(99));
        assertEquals(0.0, n.validate(-3));
        assertEquals(2.5, n.validate(2.6));
        assertEquals(5.0, n.validate("not a number"));
        assertEquals(5.0, n.validate(Double.NaN));
        assertEquals(7.0, n.validate("7"));
    }

    @Test
    void invalidValuesFallBackToDefault() {
        assertEquals(true, new Setting.Bool("b", "B", "", true).validate(42));
        assertEquals("Hold", new Setting.Choice("c", "C", "", "Hold", List.of("Hold", "Toggle")).validate("nope"));
        assertEquals("Toggle", new Setting.Choice("c", "C", "", "Hold", List.of("Hold", "Toggle")).validate("toggle"));
        assertEquals(0xFF112233, new Setting.Color("c", "C", "", 0xFF000000).validate("#112233"));
        assertEquals(0xFF000000, new Setting.Color("c", "C", "", 0xFF000000).validate("#zz"));
    }

    @Test
    void textStripsFormattingAndControlCharacters() {
        Setting.Text t = new Setting.Text("t", "T", "", "", 5);
        assertEquals("abcde", t.validate("a\u00A7b\ncdefgh"));
    }

    @Test
    void keysByNameAndCode() {
        Setting.Key k = new Setting.Key("k", "K", "", Keys.NONE);
        assertEquals(344, k.validate("RIGHT_SHIFT"));
        assertEquals(67, k.validate(67));
        assertEquals(Keys.NONE, k.validate(99999));
        assertEquals("RIGHT_SHIFT", k.toJson(344));
        assertEquals("Right Shift", Keys.display(344));
        assertEquals(Keys.MOUSE_BASE + 3, Keys.byName("MOUSE_4"));
    }
}
