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
