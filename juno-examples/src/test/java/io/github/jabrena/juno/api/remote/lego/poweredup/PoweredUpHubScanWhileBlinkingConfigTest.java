package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The example whose upload once overflowed the UNO R4 WiFi's RAM by a few bytes: it forks two tasks, yet the runtime
 * reserved a stack for each of three slots. Its subtasks are now continuations on one carrier, so there is no slot
 * count left to tune.
 */
class PoweredUpHubScanWhileBlinkingConfigTest {
    private static final String MAIN = "io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpHubScanWhileBlinking";

    @Test
    void runsItsSubtasksOnOneCarrierWithNoSlotCountToTune() throws Exception {
        CompilationResult result = compile(RuntimeConfig.DEFAULT);

        assertThat(result.report().configSuggestions()).isEmpty();
        assertThat(result.runtimeShim()).contains("juno_carrier_stack[JUNO_THREAD_STACK_BYTES]")
                .doesNotContain("JUNO_MAX_THREADS");
    }

    private static CompilationResult compile(RuntimeConfig config) throws URISyntaxException {
        Path examples = Path.of("target/classes");
        // Anchored on an annotation: test sources here shadow some api classes (e.g. a fake Gpio).
        Path api = Path.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new JunoCompiler().compile(new CompilationRequest(List.of(examples, api), MAIN, false,
                Optional.of(Board.UNO_R4_WIFI.id()), config));
    }
}
