package io.github.jabrena.juno.site;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchIndexExtensionsTest {

    @Test
    void escapesQuotesAndBackslashes() {
        assertEquals("say \\\"hi\\\" from C:\\\\tmp", SearchIndexExtensions.jsEscape("say \"hi\" from C:\\tmp"));
    }

    @Test
    void escapesLineBreaksAndTabs() {
        assertEquals("a\\nb\\rc\\td", SearchIndexExtensions.jsEscape("a\nb\rc\td"));
    }

    @Test
    void escapesOtherControlCharactersAsUnicodeEscapes() {
        assertEquals("x\\u0001y", SearchIndexExtensions.jsEscape("x\u0001y"));
    }

    @Test
    void leavesPlainTextAndNonAsciiUnchanged() {
        assertEquals("Gpio → Delay ✓", SearchIndexExtensions.jsEscape("Gpio → Delay ✓"));
    }
}
