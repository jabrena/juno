package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsToUnoR4WiFiWithoutAnAnnotation() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class Unannotated {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Unannotated", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Unannotated");

        assertThat(program.board()).isEqualTo(Board.UNO_R4_WIFI);
    }

    @Test
    void theUnoQRunsUnderTheZephyrCore() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoQ.class)
                public final class OnUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                        Delay.micros(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoQ", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.OnUnoQ");
        assertThat(program.board()).isEqualTo(Board.UNO_Q);
        assertThat(program.board().fqbn()).isEqualTo("arduino:zephyr:unoq");

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoQ");
        // Zephyr inlines delay()/delayMicroseconds() and provides yield() itself.
        assertThat(result.assembly()).contains("bl juno_delay\n", "bl juno_delay_microseconds\n")
                .doesNotContain("bl delay\n", "bl delayMicroseconds\n");
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_delay(uint32_t ms)")
                .doesNotContain("extern \"C\" void yield()", "Arduino_LED_Matrix", "ArduinoLEDMatrix");
    }

    @Test
    void theUnoR4KeepsItsOwnYieldAndLedMatrix() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class OnUnoR4 {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoR4", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoR4");
        assertThat(result.assembly()).contains("bl delay\n").doesNotContain("juno_delay");
        assertThat(result.runtimeShim()).contains("extern \"C\" void yield()", "#include \"Arduino_LED_Matrix.h\"",
                "static ArduinoLEDMatrix juno_led_matrix;");
    }

    @Test
    void theUnoQHasNoWatchdog() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoQ.class)
                @Watchdog(timeoutMillis = 3000)
                public final class WatchdogOnUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogOnUnoQ", source);
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.WatchdogOnUnoQ"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("@Watchdog requires @Board(ArduinoUnoR4WiFi.class)");
    }
}
