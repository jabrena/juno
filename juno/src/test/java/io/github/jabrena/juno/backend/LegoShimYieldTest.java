package io.github.jabrena.juno.backend;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The Powered Up shim's waits are switch points in a program with tasks, and nothing in one without. */
class LegoShimYieldTest {
    private static final Pattern PUBLIC_ENTRY = Pattern.compile("extern \"C\" [^\n]*juno_lego_hub_\\w+\\([^\n]*\\) \\{");
    private static final Pattern GUARD = Pattern.compile("JunoLegoGuard juno_lego_guard;");

    @Test
    void aProgramWithTasksYieldsToTheSchedulerWhileTheShimWaits() {
        String shim = ShimLibraries.legoPoweredUpHelpers(true);

        assertThat(shim).contains("extern \"C\" void juno_thread_yield();")
                .containsPattern("static void juno_lego_wait\\(\\) \\{\\s+juno_thread_yield\\(\\);\\s+\\}");
    }

    @Test
    void aProgramWithoutTasksKeepsTheShimWaitsAsPlainLoops() {
        String shim = ShimLibraries.legoPoweredUpHelpers(false);

        assertThat(shim).doesNotContain("juno_thread_yield")
                .containsPattern("static void juno_lego_wait\\(\\) \\{\\s+\\}");
    }

    @Test
    void theScanAndTheTwoReplyWaitsCallTheHook() {
        String shim = ShimLibraries.legoPoweredUpHelpers(true);

        // The scan loop yields when no hub has advertised yet; the property and link waits yield after each poll.
        assertThat(shim).containsPattern("if \\(!candidate\\) \\{\\s+juno_lego_wait\\(\\);\\s+continue;")
                .containsPattern("juno_lego_name_length == 0\\)\\) \\{\\s+juno_lego_poll\\(\\);\\s+juno_lego_wait\\(\\);")
                .containsPattern("while \\(true\\) \\{\\s+juno_lego_poll\\(\\);\\s+juno_lego_wait\\(\\);\\s+int32_t id");
    }

    @Test
    void everyPublicEntryTakesTheGuardSoAnotherTaskCannotReenterTheBleCalls() {
        String shim = ShimLibraries.legoPoweredUpHelpers(true);

        assertThat(count(PUBLIC_ENTRY, shim)).isGreaterThan(20);
        assertThat(count(GUARD, shim)).isEqualTo(count(PUBLIC_ENTRY, shim));
        // The guard is the first statement of the entry, so nothing runs before it.
        assertThat(shim).doesNotContainPattern("juno_lego_hub_\\w+\\([^\n]*\\) \\{\\s+(?!JunoLegoGuard)\\S");
    }

    @Test
    void arduinoBleIsPolledOnlyOnceItHasStarted() {
        String shim = ShimLibraries.legoPoweredUpHelpers(false);

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
}
