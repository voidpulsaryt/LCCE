package dev.voidpulsar.lc_claim_economy.service;

import org.junit.jupiter.api.Test;

import static dev.voidpulsar.lc_claim_economy.service.WarpNames.normalize;
import static dev.voidpulsar.lc_claim_economy.service.WarpNames.normalizeKey;
import static org.junit.jupiter.api.Assertions.*;

class WarpNamesTest {

    @Test
    void normalize_null_returnsNull() {
        assertNull(normalize(null));
    }

    @Test
    void normalize_empty_returnsNull() {
        assertNull(normalize(""));
    }

    @Test
    void normalize_blank_returnsNull() {
        assertNull(normalize("   "));
    }

    @Test
    void normalize_trimsSurroundingWhitespace() {
        assertEquals("home", normalize("  home  "));
    }

    @Test
    void normalize_allowsLettersDigitsUnderscore() {
        assertEquals("Base_2", normalize("Base_2"));
    }

    @Test
    void normalize_rejectsSpacesInsideName() {
        assertNull(normalize("my home"));
    }

    @Test
    void normalize_rejectsPunctuation() {
        assertNull(normalize("home!"));
        assertNull(normalize("home-2"));
        assertNull(normalize("home.base"));
    }

    @Test
    void normalize_rejectsOverLongName() {
        assertNull(normalize("a".repeat(33)));
    }

    @Test
    void normalize_allowsMaxLengthName() {
        String name = "a".repeat(32);
        assertEquals(name, normalize(name));
    }

    @Test
    void normalizeKey_lowercasesValidName() {
        assertEquals("myhome", normalizeKey("MyHome"));
    }

    @Test
    void normalizeKey_null_returnsNull() {
        assertNull(normalizeKey(null));
    }

    @Test
    void normalizeKey_invalidName_returnsNull() {
        assertNull(normalizeKey("bad name"));
    }
}
