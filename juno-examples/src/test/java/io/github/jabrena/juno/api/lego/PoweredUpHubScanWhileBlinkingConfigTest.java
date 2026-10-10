package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.analysis.ConfigSuggestion;
import io.github.jabrena.juno.analysis.ConfigSuggestionFormatter;
import io.github.jabrena.juno.annotations.Watchdog;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The example whose upload overflowed the UNO R4 WiFi's RAM by a few bytes: it forks two tasks, yet the runtime
 * reserves stacks for three. The configuration analysis should point at exactly that.
 */
class PoweredUpHubScanWhileBlinkingConfigTest {
    private static final String MAIN = "io.github.jabrena.juno.api.lego.PoweredUpHubScanWhileBlinking";

    @Test
    void pointsAtTheUnusedTaskStackThatOverflowedTheR4() throws Exception {
        List<ConfigSuggestion> suggestions = compile(RuntimeConfig.DEFAULT).report().configSuggestions();

        assertThat(suggestions).hasSize(1);
        ConfigSuggestion suggestion = suggestions.get(0);
        assertThat(suggestion.code()).isEqualTo("JUNO-CONFIG-001");
        assertThat(suggestion.parameter()).isEqualTo("maxThreads");
        assertThat(suggestion.current()).isEqualTo(4);
        assertThat(suggestion.suggested()).isEqualTo(3);
        assertThat(suggestion.bytesDelta()).isEqualTo(-2048);
        assertThat(ConfigSuggestionFormatter.format(suggestions)).anyMatch(line ->
                line.contains("JUNO-CONFIG-001") && line.contains("maxThreads 4 -> 3")
                        && line.contains("2048"));
    }

    @Test
    void theSuggestedConfigurationShrinksTheGeneratedThreadStacksAndSilencesTheAdvice() throws Exception {
        CompilationResult before = compile(RuntimeConfig.DEFAULT);
        CompilationResult after = compile(new RuntimeConfig(8192, 3, OptionalInt.empty()));

        assertThat(before.runtimeShim()).contains("JUNO_MAX_THREADS = 4u");
        assertThat(after.runtimeShim()).contains("JUNO_MAX_THREADS = 3u").doesNotContain("JUNO_MAX_THREADS = 4u");
        assertThat(after.report().configSuggestions()).isEmpty();
    }

    private static CompilationResult compile(RuntimeConfig config) throws URISyntaxException {
        Path examples = Path.of("target/classes");
        // Anchored on an annotation: test sources here shadow some api classes (e.g. a fake Gpio).
        Path api = Path.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new JunoCompiler().compile(new CompilationRequest(List.of(examples, api), MAIN, false,
                Optional.of("arduino-uno-r4-wifi"), config));
    }
}
