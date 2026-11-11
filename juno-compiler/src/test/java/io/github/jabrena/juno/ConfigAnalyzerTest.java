package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.ConfigSuggestion;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Runtime configuration suggestions")
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
    @DisplayName("Suggests no thread setting for a program that forks, since the arena bounds its virtual threads")
    void shouldSuggestNoThreadSettingForAProgramThatForks() throws Exception {
        // When & Then
        assertThat(suggestions("demo.TwoForks", TWO_FORKS, RuntimeConfig.DEFAULT, Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("Stays quiet when a fork sits in a loop")
    void shouldStayQuietWhenAForkSitsInALoop() throws Exception {
        // Given
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

        // When & Then
        assertThat(suggestions("demo.LoopForks", source, RuntimeConfig.DEFAULT, Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("Stays quiet for a program with no forks")
    void shouldStayQuietForAProgramWithNoForks() throws Exception {
        // Given
        String source = """
                package demo;
                public final class NoForks {
                    public static void main() { }
                }
                """;

        // When & Then
        assertThat(suggestions("demo.NoForks", source, RuntimeConfig.DEFAULT, Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("Suggests a larger arena when the startup estimate exceeds it")
    void shouldSuggestALargerArenaWhenTheStartupEstimateExceedsIt() throws Exception {
        // Given
        String source = """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """;

        // When & Then
        assertThat(suggestions("demo.TooLarge", source, RuntimeConfig.DEFAULT, Optional.empty()))
                .singleElement().extracting(ConfigSuggestion::code).isEqualTo("JUNO-CONFIG-002");
    }

    @Test
    @DisplayName("Suggests an arena that is a whole number of kilobytes above the estimate")
    void shouldSuggestAnArenaThatIsAWholeNumberOfKilobytes() throws Exception {
        // Given
        String source = """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """;
        ConfigSuggestion suggestion = suggestions("demo.TooLarge", source, RuntimeConfig.DEFAULT, Optional.empty())
                .get(0);

        // When & Then
        assertThat(suggestion.suggested() % 1024).isZero();
        assertThat(suggestion.suggested()).isGreaterThan(12000);
    }

    @Test
    @DisplayName("Reports the arena growth as the suggested size minus the current arena")
    void shouldReportTheArenaGrowthAsTheSuggestedSizeMinusTheCurrentArena() throws Exception {
        // Given
        String source = """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """;
        ConfigSuggestion suggestion = suggestions("demo.TooLarge", source, RuntimeConfig.DEFAULT, Optional.empty())
                .get(0);

        // When & Then
        assertThat(suggestion.bytesDelta()).isEqualTo(suggestion.suggested() - 8192);
    }

    @Test
    @DisplayName("Accepts the arena it was given when it covers the estimate")
    void shouldAcceptTheArenaItWasGivenWhenItCoversTheEstimate() throws Exception {
        // Given
        String source = """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """;
        RuntimeConfig config = new RuntimeConfig(16384, OptionalInt.empty());

        // When & Then
        assertThat(suggestions("demo.TooLarge", source, config, Optional.empty())).isEmpty();
    }

    private List<ConfigSuggestion> suggestions(String className, String source, RuntimeConfig config,
                                               Optional<String> board) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        Optional<String> targetBoard = board.or(() -> Optional.of(Board.UNO_R4_WIFI.id()));
        CompilationResult result = new JunoCompiler().compile(new CompilationRequest(
                CompilerTestSupport.classpath(temporaryDirectory), className, false, targetBoard, config));
        return result.report().configSuggestions();
    }
}
