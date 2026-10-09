package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.ConfigSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigAnalyzerTest {
    private static final String TWO_FORKS = """
            package demo;
            public final class TwoForks {
                static int shared;
                public static void main() throws Exception {
                    try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                        scope.fork(() -> { shared = 1; });
                        scope.fork(() -> { shared = 2; });
                        scope.join();
                    }
                }
            }
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void suggestsFewerSlotsWhenTheProgramForksFewerTasksThanTheRuntimeReserves() throws Exception {
        List<ConfigSuggestion> suggestions = suggestions("demo.TwoForks", TWO_FORKS, RuntimeConfig.DEFAULT,
                Optional.empty());

        assertThat(suggestions).hasSize(1);
        ConfigSuggestion suggestion = suggestions.get(0);
        assertThat(suggestion.code()).isEqualTo("JUNO-CONFIG-001");
        assertThat(suggestion.parameter()).isEqualTo("maxThreads");
        assertThat(suggestion.current()).isEqualTo(4);
        assertThat(suggestion.suggested()).isEqualTo(3);
        // The UNO R4 WiFi's 2048 byte stack, one stack too many.
        assertThat(suggestion.bytesDelta()).isEqualTo(-2048);
    }

    @Test
    void countsTheUnusedStackAtTheBoardsOwnSize() throws Exception {
        List<ConfigSuggestion> suggestions = suggestions("demo.TwoForks", TWO_FORKS, RuntimeConfig.DEFAULT,
                Optional.of("arduino-uno-q"));

        assertThat(suggestions).extracting(ConfigSuggestion::bytesDelta).containsExactly(-4096);
    }

    @Test
    void usesTheConfiguredStackWhenOneIsSet() throws Exception {
        RuntimeConfig config = new RuntimeConfig(8192, 4, OptionalInt.of(1024));

        assertThat(suggestions("demo.TwoForks", TWO_FORKS, config, Optional.empty()))
                .extracting(ConfigSuggestion::bytesDelta).containsExactly(-1024);
    }

    @Test
    void staysQuietWhenTheSlotsAlreadyMatchTheForks() throws Exception {
        RuntimeConfig config = new RuntimeConfig(8192, 3, OptionalInt.empty());

        assertThat(suggestions("demo.TwoForks", TWO_FORKS, config, Optional.empty())).isEmpty();
    }

    @Test
    void staysQuietWhenAForkSitsInALoop() throws Exception {
        String source = """
                package demo;
                public final class LoopForks {
                    public static void main() throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            for (int i = 0; i < 2; i++) {
                                scope.fork(() -> { });
                            }
                            scope.join();
                        }
                    }
                }
                """;

        assertThat(suggestions("demo.LoopForks", source, RuntimeConfig.DEFAULT, Optional.empty())).isEmpty();
    }

    @Test
    void staysQuietForAProgramWithNoForks() throws Exception {
        String source = """
                package demo;
                public final class NoForks {
                    public static void main() { }
                }
                """;

        assertThat(suggestions("demo.NoForks", source, RuntimeConfig.DEFAULT, Optional.empty())).isEmpty();
    }

    @Test
    void suggestsALargerArenaWhenTheStartupEstimateExceedsIt() throws Exception {
        String source = """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """;

        List<ConfigSuggestion> suggestions = suggestions("demo.TooLarge", source, RuntimeConfig.DEFAULT,
                Optional.empty());

        assertThat(suggestions).hasSize(1);
        ConfigSuggestion suggestion = suggestions.get(0);
        assertThat(suggestion.code()).isEqualTo("JUNO-CONFIG-002");
        assertThat(suggestion.parameter()).isEqualTo("arenaBytes");
        assertThat(suggestion.suggested() % 1024).isZero();
        assertThat(suggestion.suggested()).isGreaterThan(12000);
        assertThat(suggestion.bytesDelta()).isEqualTo(suggestion.suggested() - 8192);
    }

    @Test
    void acceptsTheArenaItWasGivenWhenItCoversTheEstimate() throws Exception {
        String source = """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """;
        RuntimeConfig config = new RuntimeConfig(16384, 4, OptionalInt.empty());

        assertThat(suggestions("demo.TooLarge", source, config, Optional.empty())).isEmpty();
    }

    private List<ConfigSuggestion> suggestions(String className, String source, RuntimeConfig config,
                                               Optional<String> board) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        CompilationResult result = new JunoCompiler().compile(new CompilationRequest(
                List.of(temporaryDirectory, Path.of("target/classes")), className, false, board, config));
        return result.report().configSuggestions();
    }
}
