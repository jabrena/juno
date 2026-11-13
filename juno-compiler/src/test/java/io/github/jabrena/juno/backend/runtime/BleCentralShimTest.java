package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.core.CoreRuntimes;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.ArduinoCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The BLE central shim's waits are switch points in a program with tasks, and nothing in one without. */
@DisplayName("BLE central shim yield points")
class BleCentralShimTest {
    private final ShimLibraries shimLibraries = new ShimLibraries();

    private static final Pattern GUARD = Pattern.compile("JunoBleGuard juno_ble_guard;");
    private static final Pattern PUBLIC_ENTRY = Pattern.compile("extern \"C\" [^{;]*?\\bjuno_ble_\\w+\\([^{;]*\\) \\{");

    @Test
    @DisplayName("A program with tasks yields to the scheduler while the shim waits")
    void shouldYieldToTheSchedulerWhileTheShimWaitsInAProgramWithTasks() {
        // Given
        String shim = shimLibraries.bleCentralHelpers(true);

        // When & Then
        assertThat(shim).contains("extern \"C\" void juno_thread_yield();")
                .containsPattern("static void juno_ble_wait\\(\\) \\{\\s+juno_thread_yield\\(\\);\\s+\\}");
    }

    @Test
    @DisplayName("A program without tasks keeps the shim waits as plain loops")
    void shouldKeepTheShimWaitsAsPlainLoopsInAProgramWithoutTasks() {
        // Given
        String shim = shimLibraries.bleCentralHelpers(false);

        // When & Then
        assertThat(shim).doesNotContain("juno_thread_yield")
                .containsPattern("static void juno_ble_wait\\(\\) \\{\\s+\\}");
    }

    @Test
    @DisplayName("The scan and an empty read call the yield hook")
    void shouldCallTheYieldHookFromTheScanAndAnEmptyRead() {
        // Given
        String shim = shimLibraries.bleCentralHelpers(true);

        // When & Then
        // The scan loop yields when no device has advertised yet; a read with nothing queued yields before it returns 0.
        assertThat(shim).containsPattern("if \\(!candidate\\) \\{\\s+juno_ble_wait\\(\\);\\s+continue;")
                .containsPattern("if \\(juno_ble_queue_count == 0\\) \\{\\s+juno_ble_wait\\(\\);\\s+return 0;");
    }

    @Test
    @DisplayName("Every public entry takes the guard so another task cannot re-enter the BLE calls")
    void shouldTakeTheGuardInEveryPublicEntry() {
        // Given
        String shim = shimLibraries.bleCentralHelpers(true);

        // When
        int entries = countPublicEntries(shim);

        // Then
        assertThat(entries).isEqualTo(5).isEqualTo(count(GUARD, shim));
    }

    @Test
    @DisplayName("The guard is the first statement of each entry")
    void shouldPlaceTheGuardAsTheFirstStatementOfEachEntry() {
        // Given
        String shim = shimLibraries.bleCentralHelpers(true);

        // When & Then
        assertThat(shim).doesNotContainPattern("extern \"C\" [^{;]*?juno_ble_\\w+\\([^{;]*\\) \\{\\s+(?!JunoBleGuard)\\S");
    }

    @Test
    @DisplayName("Arduino BLE is polled only once it has started")
    void shouldPollArduinoBleOnlyOnceItHasStarted() {
        // Given
        String shim = shimLibraries.bleCentralHelpers(false);

        // When & Then
        // Every poll goes through the helper, whose only BLE.poll() runs after BLE.begin() succeeded.
        assertThat(count(Pattern.compile("BLE\\.poll\\(\\);"), shim)).isEqualTo(1);
        assertThat(shim).containsPattern(
                "static void juno_ble_poll\\(\\) \\{\\s+if \\(juno_ble_started\\) \\{\\s+BLE\\.poll\\(\\);");
    }

    @Test
    @DisplayName("The ESP32 BLE adapter defines no top-level name the central shim appended after it defines too")
    void shouldKeepTheEsp32AdapterAndTheCentralShimFreeOfSharedTopLevelNames() {
        // Given
        CoreRuntime esp32 = new CoreRuntimes().of(ArduinoCore.ESP32);
        String adapter = esp32.bleHelpers();
        String central = shimLibraries.bleCentralHelpers(true, esp32.bleNames());

        // When
        Set<String> shared = new TreeSet<>(topLevelStatics(adapter));
        shared.retainAll(topLevelStatics(central));

        // Then
        // The shim appends both into one translation unit, so a shared name fails the board's compile (#284 did).
        assertThat(shared).isEmpty();
    }

    private static final Pattern TOP_LEVEL_STATIC = Pattern.compile(
            "(?m)^static (?:const |constexpr )?[\\w:<>*& ]+?\\b(juno_\\w+|JUNO_\\w+)\\s*[\\[=;(]");

    private static Set<String> topLevelStatics(String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = TOP_LEVEL_STATIC.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** The entries are the {@code extern "C"} definitions; their signatures can span lines, so match across them. */
    private static int countPublicEntries(String text) {
        return count(PUBLIC_ENTRY, text);
    }
}
