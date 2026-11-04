package io.github.jabrena.juno.conformance;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConformanceWorkspaceTest {
    private static final Path BASEDIR = Path.of(".").toAbsolutePath().normalize();
    private static final Path API_CLASSES = Path.of("api-classes");

    @TempDir
    Path temporary;

    @Test
    void selectsBoardsFromTheBoardAnnotation() throws Exception {
        ProgramSource everywhere = write("Everywhere.java", "package demo; class Everywhere {}");
        ProgramSource avrOnly = write("AvrOnly.java", """
                package demo;
                @Board(ArduinoUnoR3WiFiRev2Board.class)
                class AvrOnly {}
                """);

        assertThat(everywhere.targets(Board.UNO_R4_WIFI)).isTrue();
        assertThat(everywhere.declares(Board.UNO_R4_WIFI)).isFalse();
        assertThat(avrOnly.targets(Board.UNO_R4_WIFI)).isFalse();
        assertThat(avrOnly.targets(Board.UNO_R3_WIFI_REV2)).isTrue();
        assertThat(avrOnly.declares(Board.UNO_R3_WIFI_REV2)).isTrue();
        assertThat(avrOnly.mainClass()).isEqualTo("demo.AvrOnly");
        assertThat(avrOnly.simpleName()).isEqualTo("AvrOnly");
    }

    @Test
    void listsDirectAndNestedSourcesInFileNameOrder() throws Exception {
        write("B.java", "");
        write("A.java", "");
        Files.writeString(temporary.resolve("A.expected"), "");
        Files.createDirectories(temporary.resolve("nested"));
        Files.writeString(temporary.resolve("nested/C.java"), "");
        ProgramDirectory directory = new ProgramDirectory(temporary);

        assertThat(directory.programs()).extracting(ProgramSource::simpleName).containsExactly("A", "B");
        assertThat(directory.allSources()).containsExactly(temporary.resolve("A.java"), temporary.resolve("B.java"),
                temporary.resolve("nested/C.java"));
    }

    @Test
    void resolvesCataloguedProgramsAgainstTheConformanceRoot() {
        ConformanceWorkspace workspace = new ConformanceWorkspace(BASEDIR, "qemu", API_CLASSES);

        List<ProgramSource> sources = workspace.catalog().programs().stream().map(workspace::source).toList();

        assertThat(sources).isNotEmpty().allSatisfy(source -> assertThat(source.path()).isRegularFile());
        assertThat(workspace.conformancePrograms().allSources())
                .containsExactlyInAnyOrderElementsOf(sources.stream().map(ProgramSource::path).toList());
    }

    @Test
    void keepsEachBackendUnderItsOwnTargetDirectory() {
        ConformanceWorkspace workspace = new ConformanceWorkspace(temporary, "simavr", API_CLASSES);

        assertThat(workspace.generated(Board.UNO_R3_WIFI_REV2, "demo.IntegralArithmetic"))
                .isEqualTo(temporary.resolve("target/simavr/generated/arduino-uno-r3-wifi-rev2/IntegralArithmetic"));
        assertThat(workspace.generated(Board.UNO_R3_WIFI_REV2, "IntegralArithmetic"))
                .isEqualTo(workspace.generated(Board.UNO_R3_WIFI_REV2, "demo.IntegralArithmetic"));
        assertThat(workspace.junoClasspath())
                .containsExactly(temporary.resolve("target/simavr/program-classes"), API_CLASSES);
    }

    @Test
    void writesDiagnosticsOnlyWhenTheTranscriptDiffers() throws Exception {
        ConformanceWorkspace workspace = new ConformanceWorkspace(temporary, "qemu", API_CLASSES);
        Path diagnostics = workspace.generated(Board.UNO_R4_WIFI, "demo.Same");

        workspace.assertTranscript(Board.UNO_R4_WIFI, "demo.Same", "1\n", "1\n", "same");
        assertThat(diagnostics.resolve("expected.txt")).doesNotExist();

        assertThatThrownBy(() -> workspace.assertTranscript(Board.UNO_R4_WIFI, "demo.Other", "1\n", "2\n",
                "conformance demo.Other")).isInstanceOf(AssertionError.class).hasMessageContaining("conformance demo.Other");
        Path mismatch = workspace.generated(Board.UNO_R4_WIFI, "demo.Other");
        assertThat(mismatch.resolve("expected.txt")).hasContent("1");
        assertThat(mismatch.resolve("actual.txt")).hasContent("2");
    }

    @Test
    void keepsTheHarnessOutputNextToTheGeneratedSources() throws Exception {
        ConformanceWorkspace workspace = new ConformanceWorkspace(temporary, "xtensa", API_CLASSES);

        workspace.writeEmulatorLog(Board.NANO_ESP32, "demo.LongArithmetic", "/harness/run.sh /work",
                "[juno-begin]\n", "Segmentation fault\n");

        assertThat(workspace.generated(Board.NANO_ESP32, "demo.LongArithmetic").resolve("emulator.log"))
                .content().contains("--- command ---", "/harness/run.sh /work", "--- stdout ---", "[juno-begin]", "--- stderr ---", "Segmentation fault");
    }

    private ProgramSource write(String file, String content) throws Exception {
        Path path = temporary.resolve(file);
        Files.writeString(path, content);
        return new ProgramSource(path);
    }
}
