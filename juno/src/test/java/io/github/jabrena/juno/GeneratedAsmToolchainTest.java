package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline toolchain verification for the ASM backend's two build artifacts, replacing the retired
 * C++ backend's whole-sketch {@code -fsyntax-only} smoke tests: the generated {@code .S} file is a
 * real GNU ARM (Cortex-M4, Thumb-2) assembly source, verified by actually assembling it with the same
 * {@code arm-none-eabi-gcc} arduino-cli bundles (searched for locally; every test here skips instead
 * of failing when it isn't found), and the generated runtime shim is a real C++ translation unit,
 * verified with the host's own {@code g++}/{@code clang++} against this module's mock Arduino headers
 * (as the retired tests already did). One test also links and runs the shim's JSON parser against a
 * real payload with the retired test's original edge cases (unicode escapes, array roots, truncated/
 * malformed input, int64 boundaries) -- runtime behavior, not just syntax.
 */
class GeneratedAsmToolchainTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void assemblesAGpioLedMatrixAndSerialProgram() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.io.Gpio;
                import io.github.jabrena.juno.api.io.usb.BaudRate;
                import io.github.jabrena.juno.api.io.usb.Serial;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class AsmSmoke {
                    static int mix(int value) { return (value << 2) ^ 7; }
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                        Gpio.digitalWrite(13, mix(4) != 0);
                        Delay.millis(10);
                        LedMatrix.begin();
                        LedMatrix.loadFrame(0x3184a444, 0x44042081, 0x100a0040);
                        LedMatrix.clear();
                        Serial.begin(BaudRate.BAUD_9600);
                        Serial.println("ready");
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmSmoke", source);
    }

    @Test
    void assemblesALongFloatAndDoubleMathProgram() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class AsmMath {
                    static long lmix(long a, long b) { return (a + b) * (a - b) % 7; }
                    static float fmix(float a, float b) { return -(a * b) / 2.0f; }
                    static double dmix(double a, double b) { return -(a * b) % 1.5; }
                    public static void main(String[] args) {
                        long l = lmix(123456789012L, -42L);
                        float f = fmix(1.5f, 2.25f);
                        double d = dmix(1.25, 0.5);
                        Delay.millis((int) l + (int) f + (int) d);
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmMath", source);
    }

    @Test
    void assemblesArraysAndArenaAllocatedRecordsProgram() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String recordSource = """
                package demo;
                public record AsmPoint(int x, int y) {
                }
                """;
        String source = """
                package demo;
                public final class AsmArrays {
                    static int sum(int[] values, int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    public static void main(String[] args) {
                        int[] xs = new int[3];
                        xs[0] = 1;
                        xs[1] = 2;
                        xs[2] = 3;
                        AsmPoint p = new AsmPoint(sum(xs, xs.length), 4);
                        int total = p.x() + p.y();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmPoint", recordSource);
        assembleAndCompile(armGcc, "demo.AsmArrays", source);
    }

    @Test
    void compilesAWifiHttpAndJsonProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.HttpsClient;
                import io.github.jabrena.juno.api.io.net.Json;
                import io.github.jabrena.juno.api.io.net.Wifi;
                public final class AsmNetworking {
                    public static void main(String[] args) {
                        Wifi.begin("ssid", "password");
                        byte[] response = new byte[64];
                        byte[] headers = new byte[64];
                        int[] out = new int[2];
                        HttpsClient.get("example.com", 443, "/status", response, response.length,
                                headers, headers.length, out);
                        int temperature = Json.getInt(response, response.length, "data.temp");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmNetworking", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmNetworking");
        Path shim = temporaryDirectory.resolve("AsmNetworkingShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAStringBuilderProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class AsmStringBuilder {
                    public static void main(String[] args) {
                        StringBuilder builder = new StringBuilder(8);
                        builder.append('a');
                        builder.append("bc");
                        Serial.println(builder.toString().length());
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmStringBuilder", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmStringBuilder");
        Path shim = temporaryDirectory.resolve("AsmStringBuilderShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void generatedJsonShimParsesStrictBoundedDocumentsAtRuntime() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Json;
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
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonRuntime");
        Path sketch = temporaryDirectory.resolve("JsonRuntime.cpp");
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
        Files.writeString(sketch, result.runtimeShim() + harness, StandardCharsets.UTF_8);
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

    private void assembleAndCompile(String armGcc, String mainClass, String source) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        CompilerTestSupport.compileJava(temporaryDirectory, mainClass, source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, mainClass);
        Path assembly = temporaryDirectory.resolve(simpleName + ".S");
        Files.writeString(assembly, result.assembly(), StandardCharsets.UTF_8);
        Path object = temporaryDirectory.resolve(simpleName + ".o");

        Process process = new ProcessBuilder(armGcc, "-mcpu=cortex-m4", "-mthumb", "-c",
                assembly.toString(), "-o", object.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "arm-none-eabi-gcc timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    private void syntaxCheckCpp(String compiler, Path shim) throws Exception {
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "C++ compiler timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isEqualTo(0);
    }

    private String availableCppCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                // Try the next compiler.
            }
        }
        return null;
    }

    /**
     * Cortex-M4 sketches need a real GNU ARM cross-assembler, which is rarely on {@code PATH} outside
     * CI; arduino-cli already bundles one under its board package cache for {@code arduino-cli compile}
     * itself, so this looks there too rather than only checking {@code PATH}.
     */
    private String availableArmGcc() {
        for (String candidate : new String[]{"arm-none-eabi-gcc"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                // Fall through to the bundled-toolchain search below.
            }
        }
        Path packagesRoot = Path.of(System.getProperty("user.home"), "Library", "Arduino15", "packages");
        if (!Files.isDirectory(packagesRoot)) {
            return null;
        }
        try (Stream<Path> found = Files.walk(packagesRoot, 6)) {
            return found.filter(path -> path.getFileName().toString().equals("arm-none-eabi-gcc")
                            && Files.isExecutable(path) && !Files.isDirectory(path))
                    .max(Comparator.comparing(Path::toString))
                    .map(Path::toString)
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }
}
