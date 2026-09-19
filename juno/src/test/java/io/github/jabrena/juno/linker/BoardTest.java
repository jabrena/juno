package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(Board.UNO_R4_WIFI, program.board());
    }

    @Test
    void resolvesTheBoardFromTheEntryPointsAnnotation() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.ArduinoUnoR4Minima;
                import io.github.jabrena.juno.api.Board;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoR4Minima.class)
                public final class MinimaProgram {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MinimaProgram", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.MinimaProgram");

        assertEquals(Board.UNO_R4_MINIMA, program.board());
    }

    @Test
    void rejectsLedMatrixUsageOnTheMinimaWhichHasNoOnboardMatrix() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.ArduinoUnoR4Minima;
                import io.github.jabrena.juno.api.Board;
                import io.github.jabrena.juno.api.led.LedMatrix;
                @Board(ArduinoUnoR4Minima.class)
                public final class MinimaWithMatrix {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MinimaWithMatrix", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.link(temporaryDirectory, "demo.MinimaWithMatrix"));
        assertTrue(exception.getMessage().contains("LedMatrix"));
        assertTrue(exception.getMessage().contains("Minima"));
    }
}
