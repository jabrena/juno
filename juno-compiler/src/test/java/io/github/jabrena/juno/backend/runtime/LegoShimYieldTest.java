package io.github.jabrena.juno.backend.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The Powered Up shim's waits are switch points in a program with tasks, and nothing in one without. */
@DisplayName("Powered Up shim yield points")
class LegoShimYieldTest {
    private final ShimLibraries shimLibraries = new ShimLibraries();

    private static final Pattern GUARD = Pattern.compile("JunoLegoGuard juno_lego_guard;");

    @Test
    @DisplayName("A program with tasks yields to the scheduler while the shim waits")
    void shouldYieldToTheSchedulerWhileTheShimWaitsInAProgramWithTasks() {
        // Given
        String shim = shimLibraries.legoPoweredUpHelpers(true);

        // When & Then
        assertThat(shim).contains("extern \"C\" void juno_thread_yield();")
                .containsPattern("static void juno_lego_wait\\(\\) \\{\\s+juno_thread_yield\\(\\);\\s+\\}");
    }

    @Test
    @DisplayName("A program without tasks keeps the shim waits as plain loops")
    void shouldKeepTheShimWaitsAsPlainLoopsInAProgramWithoutTasks() {
        // Given
        String shim = shimLibraries.legoPoweredUpHelpers(false);

        // When & Then
        assertThat(shim).doesNotContain("juno_thread_yield")
                .containsPattern("static void juno_lego_wait\\(\\) \\{\\s+\\}");
    }

    @Test
    @DisplayName("The scan and the two reply waits call the yield hook")
    void shouldCallTheYieldHookFromTheScanAndTheTwoReplyWaits() {
        // Given
        String shim = shimLibraries.legoPoweredUpHelpers(true);

        // When & Then
        // The scan loop yields when no hub has advertised yet; the property and link waits yield after each poll.
        assertThat(shim).containsPattern("if \\(!candidate\\) \\{\\s+juno_lego_wait\\(\\);\\s+continue;")
                .containsPattern("juno_lego_name_length == 0\\)\\) \\{\\s+juno_lego_poll\\(\\);\\s+juno_lego_wait\\(\\);")
                .containsPattern("while \\(true\\) \\{\\s+juno_lego_poll\\(\\);\\s+juno_lego_wait\\(\\);\\s+int32_t id");
    }

    @Test
    @DisplayName("Every public entry takes the guard so another task cannot re-enter the BLE calls")
    void shouldTakeTheGuardInEveryPublicEntry() {
        // Given
        String shim = shimLibraries.legoPoweredUpHelpers(true);

        // When
        int entries = countPublicEntries(shim);

        // Then
        assertThat(entries).isGreaterThan(20).isEqualTo(count(GUARD, shim));
    }

    @Test
    @DisplayName("The guard is the first statement of each entry")
    void shouldPlaceTheGuardAsTheFirstStatementOfEachEntry() {
        // Given
        String shim = shimLibraries.legoPoweredUpHelpers(true);

        // When & Then
        assertThat(shim).doesNotContainPattern("juno_lego_hub_\\w+\\([^\n]*\\) \\{\\s+(?!JunoLegoGuard)\\S");
    }

    @Test
    @DisplayName("Arduino BLE is polled only once it has started")
    void shouldPollArduinoBleOnlyOnceItHasStarted() {
        // Given
        String shim = shimLibraries.legoPoweredUpHelpers(false);

        // When & Then
        // Every poll goes through the helper, whose only BLE.poll() runs after BLE.begin() succeeded.
        assertThat(count(Pattern.compile("BLE\\.poll\\(\\);"), shim)).isEqualTo(1);
        assertThat(shim).containsPattern(
                "static void juno_lego_poll\\(\\) \\{\\s+if \\(juno_lego_ble_started\\) \\{\\s+BLE\\.poll\\(\\);");
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int countPublicEntries(String text) {
        int count = 0;
        for (String line : text.lines().toList()) {
            if (isPublicEntry(line)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isPublicEntry(String line) {
        String declaration = line.strip();
        int nameStart = declaration.indexOf("juno_lego_hub_");
        int openParenthesis = nameStart < 0 ? -1 : declaration.indexOf('(', nameStart);
        return declaration.startsWith("extern \"C\" ")
                && declaration.endsWith(") {")
                && openParenthesis > nameStart;
    }
}
