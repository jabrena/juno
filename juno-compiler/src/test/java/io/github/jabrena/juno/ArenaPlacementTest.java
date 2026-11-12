package io.github.jabrena.juno;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Arena placement")
class ArenaPlacementTest {

    private static final String[] PSRAM_MARKERS = {"EXT_RAM_BSS_ATTR", "EXT_RAM_ATTR", "ps_malloc",
        "MALLOC_CAP_SPIRAM"};

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest(name = "{0} keeps the arena in internal SRAM")
    @EnumSource(Board.class)
    @DisplayName("No generated source places the arena or the runtime in PSRAM")
    void shouldNeverPlaceTheArenaInPsram(Board board) throws Exception {
        // Given
        String source = """
                package demo;

                public final class Empty {
                    public static void main() {
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Empty", source);

        // When
        CompilationResult result = new JunoCompiler().compile(new CompilationRequest(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.Empty", false,
                Optional.of(board.id())));

        // Then
        for (String marker : PSRAM_MARKERS) {
            assertThat(result.runtimeShim()).as(board.id() + " shim").doesNotContain(marker);
            assertThat(result.assembly()).as(board.id() + " assembly").doesNotContain(marker);
        }
    }

    @Test
    @DisplayName("The arena declaration carries no section attribute")
    void shouldDeclareTheArenaWithoutASection() throws Exception {
        String allocator = new String(getClass().getResourceAsStream(
                "/runtime/common/allocator.cpp").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String declaration = allocator.lines().filter(line -> line.contains("juno_arena[")).findFirst().orElseThrow();

        assertThat(declaration).doesNotContain("section").doesNotContain("EXT_RAM");
    }
}
