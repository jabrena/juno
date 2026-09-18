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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
    }

    @Test
    void generatedFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixText;
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
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixSmallText;
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
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixShapes;
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
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixShapes;
                import io.github.jabrena.juno.api.led.LedMatrixTransform;
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
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixShapes;
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

    @Test
    void generatedAsciiFontSketchPassesACppSyntaxCheck() throws Exception {
        String compiler = availableCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixText;
                public final class Ascii {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        int word0 = LedMatrixText.drawChar(0, 0, 'H', 4, 0);
                        int word1 = LedMatrixText.drawChar(0, 1, 'i', 4, 0);
                        int word2 = LedMatrixText.drawChar(0, 2, '!', 4, 0);
                        LedMatrix.loadFrame(word0, word1, word2);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
        assertEquals(0, process.exitValue(), diagnostics);
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
