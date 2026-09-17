package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedCppSyntaxTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Clock;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.Gpio;
                public final class Smoke {
                    static int mix(int value) { return (value << 2) ^ 7; }
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                        Gpio.digitalWrite(13, mix(Clock.millis()) != 0);
                        Delay.micros(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Smoke", source);
        Path sketch = temporaryDirectory.resolve("Smoke.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Smoke"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedLedMatrixSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.LedMatrix;
                public final class Heart {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        LedMatrix.loadFrame(0x3184a444, 0x44042081, 0x100a0040);
                        Delay.millis(500);
                        LedMatrix.clear();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Heart", source);
        Path sketch = temporaryDirectory.resolve("Heart.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Heart"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.LedMatrix;
                import io.github.jabrena.juno.api.LedMatrixText;
                public final class Digits {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        int word0 = LedMatrixText.drawDigit(0, 0, 1, 4, 0);
                        int word1 = LedMatrixText.drawDigit(0, 1, 1, 4, 0);
                        int word2 = LedMatrixText.drawDigit(0, 2, 1, 4, 0);
                        word0 = LedMatrixText.drawLetter(word0, 0, 0, 0, 0);
                        LedMatrix.loadFrame(word0, word1, word2);
                        Delay.millis(500);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Digits", source);
        Path sketch = temporaryDirectory.resolve("Digits.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedSmallFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.LedMatrix;
                import io.github.jabrena.juno.api.LedMatrixSmallText;
                public final class Decimal {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        int word0 = LedMatrixSmallText.drawDecimal(0, 0, 2, 5, 1, 1);
                        int word1 = LedMatrixSmallText.drawDecimal(0, 1, 2, 5, 1, 1);
                        int word2 = LedMatrixSmallText.drawDecimal(0, 2, 2, 5, 1, 1);
                        LedMatrix.loadFrame(word0, word1, word2);
                        Delay.millis(500);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Decimal", source);
        Path sketch = temporaryDirectory.resolve("Decimal.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Decimal"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedShapesSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.LedMatrix;
                import io.github.jabrena.juno.api.LedMatrixShapes;
                public final class Shapes {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        int word0 = LedMatrixShapes.fillRect(0, 0, 1, 1, 4, 4);
                        int word1 = LedMatrixShapes.drawRect(0, 1, 1, 1, 4, 4);
                        int word2 = LedMatrixShapes.drawRect(0, 2, 0, 0, 10, 6);
                        LedMatrix.loadFrame(word0, word1, word2);
                        Delay.millis(500);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Shapes", source);
        Path sketch = temporaryDirectory.resolve("Shapes.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Shapes"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedTriangleSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.LedMatrix;
                import io.github.jabrena.juno.api.LedMatrixShapes;
                import io.github.jabrena.juno.api.LedMatrixTransform;
                public final class Triangle {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        int x1 = LedMatrixTransform.rotateX(5, 0, 5, 3, 1);
                        int y1 = LedMatrixTransform.rotateY(5, 0, 5, 3, 1);
                        int word0 = LedMatrixShapes.fillTriangle(0, 0, x1, y1, 2, 6, 8, 6);
                        int word1 = LedMatrixShapes.drawTriangle(0, 1, x1, y1, 2, 6, 8, 6);
                        int word2 = LedMatrixShapes.drawLine(0, 2, 0, 0, 11, 7);
                        LedMatrix.loadFrame(word0, word1, word2);
                        Delay.millis(500);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Triangle", source);
        Path sketch = temporaryDirectory.resolve("Triangle.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Triangle"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedCircleSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.LedMatrix;
                import io.github.jabrena.juno.api.LedMatrixShapes;
                public final class Circle {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        int word0 = LedMatrixShapes.fillCircle(0, 0, 5, 3, 3);
                        int word1 = LedMatrixShapes.drawCircle(0, 1, 5, 3, 3);
                        int word2 = LedMatrixShapes.drawCircle(0, 2, 5, 3, 0);
                        LedMatrix.loadFrame(word0, word1, word2);
                        Delay.millis(500);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Circle", source);
        Path sketch = temporaryDirectory.resolve("Circle.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Circle"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), diagnostics);
    }

    private String availableCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Try the next compiler.
            }
        }
        return null;
    }
}
