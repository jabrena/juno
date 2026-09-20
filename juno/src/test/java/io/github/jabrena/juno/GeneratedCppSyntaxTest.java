package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedCppSyntaxTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedFloatSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class FloatSmoke {
                    static float lastValue;
                    static double lastDouble;
                    static float adjust(float value, int factor, float offset) {
                        return value * (float) factor + offset;
                    }
                    static float sum(float[] values) {
                        return values[0] + values[1];
                    }
                    static double blend(double[] values, double offset) {
                        return values[0] * values[1] + offset;
                    }
                    public static void main(String[] args) {
                        float total = 0.0f;
                        for (int i = 0; i < 4; i++) total += 0.75f;
                        float[] inputs = new float[2];
                        inputs[0] = total;
                        inputs[1] = 0.5f;
                        float remainder = -adjust(sum(inputs), 2, 0.0f) % 1.25f;
                        lastValue = remainder;
                        double[] precise = new double[2];
                        precise[0] = 1.25;
                        precise[1] = 2.0;
                        lastDouble = blend(precise, 0.5) % 1.5;
                        int narrowed = (int) remainder;
                        Gpio.digitalWrite(13, (float) narrowed < total && lastValue == remainder
                                && (int) lastDouble >= 0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatSmoke", source);
        Path sketch = temporaryDirectory.resolve("FloatSmoke.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatSmoke"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedLedMatrixSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedMatrix;
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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Digits {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.drawDigit(frame, 1, 4, 0);
                        LedCanvas.drawLetter(frame, 0, 0, 0);
                        LedCanvas.show(frame);
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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedSmallFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Decimal {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.drawDecimal(frame, 2, 5, 1, 1);
                        LedCanvas.show(frame);
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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedShapesSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Shapes {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.fillRect(frame, 1, 1, 4, 4);
                        LedCanvas.drawRect(frame, 0, 0, 10, 6);
                        LedCanvas.show(frame);
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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedTriangleSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Triangle {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.fillTriangle(frame, 5, 0, 2, 6, 8, 6);
                        LedCanvas.drawTriangle(frame, 5, 0, 2, 6, 8, 6);
                        LedCanvas.drawLine(frame, 0, 0, 11, 7);
                        LedCanvas.show(frame);
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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedCircleSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Circle {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.fillCircle(frame, 5, 3, 3);
                        LedCanvas.drawCircle(frame, 5, 3, 0);
                        LedCanvas.show(frame);
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
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedAsciiFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Ascii {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.drawChar(frame, 'H', 4, 0);
                        LedCanvas.show(frame);
                        Delay.millis(500);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Ascii", source);
        Path sketch = temporaryDirectory.resolve("Ascii.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Ascii"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedSerialCounterSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.Serial;
                public final class Counter {
                    public static void main(String[] args) {
                        Serial.begin(9600);
                        int counter = 0;
                        Serial.println(counter);
                        counter++;
                        Delay.millis(1000);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", source);
        Path sketch = temporaryDirectory.resolve("Counter.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedArraySketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class ArrayDemo {
                    static int sum(int[] values, int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    public static void main(String[] args) {
                        int[] pins = new int[3];
                        pins[0] = 2;
                        pins[1] = 3;
                        pins[2] = 4;
                        int total = sum(pins, pins.length);
                        Gpio.pinMode(total, Gpio.OUTPUT);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArrayDemo", source);
        Path sketch = temporaryDirectory.resolve("ArrayDemo.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArrayDemo"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedOtherElementTypeArraysAndArrayReturnSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                public final class ArrayVariety {
                    static int sumBytes(byte[] values, int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    static int[] pick(boolean useA, int[] a, int[] b) {
                        if (useA) {
                            return a;
                        }
                        return b;
                    }
                    public static void main(String[] args) {
                        byte[] buf = new byte[4];
                        buf[0] = 10;
                        buf[1] = 20;
                        char[] chars = new char[3];
                        chars[0] = 'a';
                        short[] shorts = new short[2];
                        shorts[0] = 1000;
                        boolean[] flags = new boolean[2];
                        flags[0] = true;
                        int total = sumBytes(buf, buf.length);

                        int[] x = new int[2];
                        int[] y = new int[2];
                        int[] chosen = pick(true, x, y);
                        chosen[1] = 99;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArrayVariety", source);
        Path sketch = temporaryDirectory.resolve("ArrayVariety.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArrayVariety"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedRatonLocoSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.DigitalOutput;
                import io.github.jabrena.juno.api.Mouse;
                public final class RatonLoco {
                    public static void main(String[] args) {
                        DigitalOutput led = DigitalOutput.of(13);
                        Mouse.begin();
                        led.high();
                        Delay.millis(100);
                        led.low();
                        Mouse.move(50, 0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.RatonLoco", source);
        Path sketch = temporaryDirectory.resolve("RatonLoco.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RatonLoco"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedLongMathSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                public final class LongMath {
                    public static void main(String[] args) {
                        long acc = 0L;
                        for (long i = 0; i < 10; i++) {
                            acc += i;
                        }
                        long a = 123456789012L;
                        long b = -987654321098L;
                        long sum = a + b;
                        long diff = a - b;
                        long prod = a * 3L;
                        long quot = a / 7L;
                        long rem = a % 7L;
                        long neg = -a;
                        long shiftedLeft = a << 3;
                        long shiftedRight = b >> 2;
                        long shiftedUnsigned = b >>> 2;
                        long anded = a & b;
                        long ored = a | b;
                        long xored = a ^ b;
                        int fromLong = (int) a;
                        long fromInt = fromLong;
                        boolean less = a < b;
                        if (less && sum != diff) {
                            acc = acc + 1;
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongMath", source);
        Path sketch = temporaryDirectory.resolve("LongMath.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongMath"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedEnumSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String enumSource = """
                package demo;
                public enum Direction {
                    NORTH, SOUTH, EAST, WEST;
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesEnum {
                    static int classify(Direction d) {
                        if (d == Direction.NORTH) {
                            return 0;
                        }
                        if (d != Direction.SOUTH) {
                            return 1;
                        }
                        return 2;
                    }
                    public static void main(String[] args) {
                        Direction d = Direction.EAST;
                        int code = classify(d);
                        boolean isWest = d == Direction.WEST;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Direction", enumSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesEnum", usingSource);
        Path sketch = temporaryDirectory.resolve("UsesEnum.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesEnum"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedRecordSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String recordSource = """
                package demo;
                public record Point(int x, int y) {
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesPoint {
                    public static void main(String[] args) {
                        Point p = new Point(3, 4);
                        int total = p.x() + p.y();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesPoint", usingSource);
        Path sketch = temporaryDirectory.resolve("UsesPoint.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesPoint"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedArenaObjectAndSwitchSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                public final class ArenaSmoke {
                    static int seed = 10;
                    enum Mode { FAST, SLOW }
                    record Step(int value) {}
                    static final class Box {
                        int value;
                        Box(int value) { this.value = value; }
                        int add(Step step) { value += step.value(); return value; }
                    }
                    static Step echo(Step step) { return step; }
                    static int[] values(int first, int second) {
                        int[] result = new int[2];
                        result[0] = first;
                        result[1] = second;
                        return result;
                    }
                    static int select(Mode mode) {
                        return switch (mode) { case FAST -> 1; case SLOW -> 2; };
                    }
                    public static void main(String[] args) {
                        int[][] matrix = new int[2][2];
                        matrix[1][1] = 3;
                        Box box = new Box(seed);
                        Step step = echo(new Step(select(Mode.FAST) + matrix[1][1]));
                        int[] result = values(box.add(step), box.add(new Step(select(Mode.SLOW))));
                        int total = result[0] + result[1];
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArenaSmoke", source);
        Path sketch = temporaryDirectory.resolve("ArenaSmoke.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArenaSmoke"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedHttpMethodSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.HttpClient;
                public final class HttpMethodsSmoke {
                    public static void main(String[] args) {
                        byte[] response = new byte[64];
                        int getBytes = HttpClient.get("example.com", 80, "/items", response, response.length);
                        int postBytes = HttpClient.post("example.com", 80, "/items", "{\\\"value\\\":1}",
                                response, response.length);
                        int deleteBytes = HttpClient.delete("example.com", 80, "/items/1",
                                response, response.length);
                        int patchBytes = HttpClient.patch("example.com", 80, "/items/1", "{\\\"value\\\":2}",
                                response, response.length);
                        int queryBytes = HttpClient.query("example.com", 80, "/items/search", "{\\\"value\\\":2}",
                                response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpMethodsSmoke", source);
        Path sketch = temporaryDirectory.resolve("HttpMethodsSmoke.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpMethodsSmoke"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedHttpsMethodSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.HttpsClient;
                public final class HttpsMethodsSmoke {
                    public static void main(String[] args) {
                        byte[] response = new byte[64];
                        int getBytes = HttpsClient.get("example.com", 443, "/items",
                                response, response.length);
                        int postBytes = HttpsClient.post("example.com", 443, "/items", "{\\\"value\\\":1}",
                                response, response.length);
                        int deleteBytes = HttpsClient.delete("example.com", 443, "/items/1",
                                response, response.length);
                        int patchBytes = HttpsClient.patch("example.com", 443, "/items/1", "{\\\"value\\\":2}",
                                response, response.length);
                        int queryBytes = HttpsClient.query("example.com", 443, "/items/search", "{\\\"value\\\":2}",
                                response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpsMethodsSmoke", source);
        Path sketch = temporaryDirectory.resolve("HttpsMethodsSmoke.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpsMethodsSmoke"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedJsonFieldExtractionSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.Json;
                public final class JsonSmoke {
                    public static void main(String[] args) {
                        byte[] buffer = new byte[64];
                        byte[] name = new byte[16];
                        int kind = Json.type(buffer, buffer.length, "data.samples[0]");
                        int temperature = Json.getInt(buffer, buffer.length, "data.sensor.temp");
                        long sequence = Json.getLong(buffer, buffer.length, "sequence");
                        double precise = Json.getDouble(buffer, buffer.length, "precise");
                        boolean ok = Json.getBool(buffer, buffer.length, "ok");
                        int nameLength = Json.getString(buffer, buffer.length, "name", name, name.length);
                        int samples = Json.arraySize(buffer, buffer.length, "data.samples");
                        int total = kind + temperature + (int) sequence + (int) precise
                                + nameLength + samples + (ok ? 1 : 0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonSmoke", source);
        Path sketch = temporaryDirectory.resolve("JsonSmoke.ino");
        Files.writeString(sketch, CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonSmoke"),
                StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", sketch.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    @Test
    void generatedJsonHelpersParseStrictBoundedDocumentsAtRuntime() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.Json;
                public final class JsonRuntime {
                    public static void main(String[] args) {
                        byte[] buffer = new byte[1];
                        byte[] out = new byte[1];
                        int reachable = Json.type(buffer, 0, "")
                                + Json.getInt(buffer, 0, "x")
                                + (int) Json.getLong(buffer, 0, "x")
                                + (int) Json.getDouble(buffer, 0, "x")
                                + (Json.getBool(buffer, 0, "x") ? 1 : 0)
                                + Json.getString(buffer, 0, "x", out, out.length)
                                + Json.arraySize(buffer, 0, "x");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonRuntime", source);
        Path sketch = temporaryDirectory.resolve("JsonRuntime.cpp");
        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonRuntime");
        String harness = """

                int main() {
                  const char json[] = R"json({"users":[{"id":1},{"id":2147483648,"active":false,
                      "name":"A\\n\\u00e9\\uD83D\\uDE00"}],"numbers":[-12,1.25e2],"nothing":null,
                      "minimum":-9223372036854775808,"tooLarge":9223372036854775808})json";
                  const uint8_t* bytes = reinterpret_cast<const uint8_t*>(json);
                  int32_t length = static_cast<int32_t>(sizeof(json) - 1);
                  uint8_t out[16] = {};
                  if (juno_json_type(bytes, length, "users") != JUNO_JSON_ARRAY) return 1;
                  if (juno_json_array_size(bytes, length, "users") != 2) return 2;
                  if (juno_json_get_int(bytes, length, "users[0].id") != 1) return 3;
                  if (juno_json_get_int(bytes, length, "users[1].id") != 0) return 4;
                  if (juno_json_get_long(bytes, length, "users[1].id") != 2147483648LL) return 5;
                  if (juno_json_type(bytes, length, "users[1].active") != JUNO_JSON_BOOLEAN) return 6;
                  if (juno_json_get_bool(bytes, length, "users[1].active")) return 7;
                  if (juno_json_get_double(bytes, length, "numbers[1]") != 125.0) return 8;
                  if (juno_json_get_int(bytes, length, "numbers[1]") != 0) return 9;
                  if (juno_json_type(bytes, length, "nothing") != JUNO_JSON_NULL) return 10;
                  if (juno_json_type(bytes, length, "missing") != JUNO_JSON_MISSING) return 11;
                  int32_t written = juno_json_get_string(bytes, length, "users[1].name", out, 16);
                  const uint8_t expected[] = {'A', '\\n', 0xc3, 0xa9, 0xf0, 0x9f, 0x98, 0x80};
                  if (written != 8 || memcmp(out, expected, 8) != 0) return 12;
                  if (juno_json_get_string(bytes, length, "users[1].name", out, 4) != 4) return 13;

                  const char rootArray[] = R"json([true,3])json";
                  const uint8_t* rootBytes = reinterpret_cast<const uint8_t*>(rootArray);
                  int32_t rootLength = static_cast<int32_t>(sizeof(rootArray) - 1);
                  if (juno_json_type(rootBytes, rootLength, "[0]") != JUNO_JSON_BOOLEAN) return 14;
                  if (juno_json_get_int(rootBytes, rootLength, "[1]") != 3) return 15;

                  const char truncated[] = R"json({"x":[1,2)json";
                  if (juno_json_type(reinterpret_cast<const uint8_t*>(truncated),
                                     static_cast<int32_t>(sizeof(truncated) - 1), "x") != JUNO_JSON_INVALID) return 16;
                  const char malformed[] = R"json({"x":01})json";
                  if (juno_json_type(reinterpret_cast<const uint8_t*>(malformed),
                                     static_cast<int32_t>(sizeof(malformed) - 1), "x") != JUNO_JSON_INVALID) return 17;
                  if (juno_json_get_long(bytes, length, "minimum") != INT64_MIN) return 18;
                  if (juno_json_get_long(bytes, length, "tooLarge") != 0) return 19;
                  return 0;
                }
                """;
        Files.writeString(sketch, generated + harness, StandardCharsets.UTF_8);
        Path executable = temporaryDirectory.resolve("json-runtime");

        Process compile = new ProcessBuilder(compiler, "-std=c++17", "-x", "c++",
                "-Isrc/test/resources", sketch.toString(), "-o", executable.toString())
                .redirectErrorStream(true)
                .start();
        boolean compiled = compile.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(compiled, "C++ compiler timed out");
        String diagnostics = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(compile.exitValue()).as(diagnostics).isEqualTo(0);

        Process run = new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
        boolean finished = run.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "Generated JSON runtime test timed out");
        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(run.exitValue()).as("runtime exit code; output: " + output).isEqualTo(0);
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
