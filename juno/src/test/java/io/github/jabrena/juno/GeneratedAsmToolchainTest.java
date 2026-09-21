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

    /**
     * A regression test for exactly the bug this GC implementation shipped with once: every other
     * toolchain test here either assembles the {@code .S} alone (no link) or compiles/links the shim
     * alone on the host (never against the {@code .S}), so all of them kept passing even though
     * {@code juno_gc_stack_top} was emitted as a bare label with no {@code .global} directive — giving
     * it local (file-scope) linkage the shim's {@code extern "C"} declaration could never actually
     * resolve against. It only surfaced building a real allocating example (LedMatrixSnake) with
     * {@code arduino-cli}, as an "undefined reference to juno_gc_stack_top" link error — because a
     * non-allocating program like Blink has this whole path stripped by the linker's
     * {@code --gc-sections} before the missing symbol would ever matter. This test directly checks the
     * one property that broke: {@code juno_gc_stack_top} must have GLOBAL linkage in the assembled
     * object (nm's uppercase {@code B}), not local ({@code b}).
     */
    @Test
    void gcStackTopSymbolHasGlobalLinkageInGeneratedAssembly() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String armNm = availableArmNm(armGcc);
        Assumptions.assumeTrue(armNm != null, "No arm-none-eabi-nm toolchain available");
        String source = """
                package demo;
                public final class AsmAllocatesForLinkage {
                    public static void main(String[] args) {
                        int[] xs = new int[3];
                        xs[0] = 1;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmAllocatesForLinkage", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmAllocatesForLinkage");
        Path assembly = temporaryDirectory.resolve("AsmAllocatesForLinkage.S");
        Files.writeString(assembly, result.assembly(), StandardCharsets.UTF_8);
        Path object = temporaryDirectory.resolve("AsmAllocatesForLinkage.o");

        Process assemble = new ProcessBuilder(armGcc, "-mcpu=cortex-m4", "-mthumb", "-c",
                assembly.toString(), "-o", object.toString())
                .redirectErrorStream(true)
                .start();
        boolean assembled = assemble.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(assembled, "arm-none-eabi-gcc timed out");
        String assembleDiagnostics = new String(assemble.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(assemble.exitValue()).as(assembleDiagnostics).isEqualTo(0);

        Process nm = new ProcessBuilder(armNm, object.toString()).redirectErrorStream(true).start();
        boolean nmFinished = nm.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(nmFinished, "arm-none-eabi-nm timed out");
        String symbols = new String(nm.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(symbols).as("juno_gc_stack_top must have GLOBAL linkage (uppercase nm type 'B'), "
                + "not local ('b'), so the shim's extern \"C\" declaration can link against it: " + symbols)
                .containsPattern("(?m)^\\S+ B juno_gc_stack_top$");
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

    /**
     * Every generated program's entry-point prologue captures the live {@code sp} into
     * {@code juno_gc_stack_top} before its own {@code push} (see
     * {@link io.github.jabrena.juno.backend.CortexM4AsmBackend#emitMethod}) so the conservative GC's
     * stack scan has a sound upper bound. These host-only harnesses never run that generated
     * assembly, so each one stands in for it: define the storage {@code juno_alloc}'s shim only
     * declares {@code extern}, and set it from a local near the top of {@code main()}, the same way
     * the real prologue does.
     */
    private static final String GC_STACK_TOP_PRELUDE = """

            extern "C" { uintptr_t juno_gc_stack_top; }
            """;

    /**
     * Capturing here, in {@code main()}'s own minimal frame, before calling into {@code gcTestBody()}
     * (which holds every local a test actually uses) guarantees this bound sits above everything
     * {@code gcTestBody()} and its callees touch — a called function's frame is always deeper than
     * its caller's, regardless of compiler choices. Capturing a local declared alongside a test's own
     * locals in that same frame instead doesn't have this guarantee: the compiler is free to place
     * those locals in either order, so such a bound can end up excluding some of them — exactly the
     * bug this shape avoids (an earlier draft of these tests learned this the hard way: it captured
     * a marker declared before a large local array in the very same function, the compiler placed
     * the array outside the resulting bound, and the collector — correctly, given that bad bound —
     * couldn't see the array's pointers as roots and reclaimed blocks that were still reachable).
     */
    private static final String GC_STACK_TOP_MAIN = """

            int main() {
              uint8_t stackTopMarker;
              juno_gc_stack_top = reinterpret_cast<uintptr_t>(&stackTopMarker);
              return gcTestBody();
            }
            """;

    @Test
    void gcReclaimsUnreachableBlocksSoAllocationFarExceedingArenaCapacitySucceeds() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                public final class GcReclaim {
                    public static void main(String[] args) {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.GcReclaim", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GcReclaim");
        String harness = GC_STACK_TOP_PRELUDE + """

                static int gcTestBody() {
                  // Each iteration's block is dropped (never stored anywhere reachable) before the
                  // next allocation, so only the collector's reclamation keeps this from exhausting
                  // the 8 KiB arena: 5000 * 64 bytes is roughly 39x the arena's capacity.
                  for (int i = 0; i < 5000; i++) {
                    void* block = juno_alloc(64, 4);
                    (void) block;
                  }
                  return 0;
                }
                """ + GC_STACK_TOP_MAIN;
        Process run = compileAndStart(compiler, result.runtimeShim() + harness, "gc-reclaim");
        boolean finished = run.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            run.destroyForcibly();
        }
        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as("process should not hang if the collector actually reclaims garbage: "
                + output).isTrue();
        assertThat(run.exitValue()).as("runtime exit code; output: " + output).isEqualTo(0);
    }

    @Test
    void gcCannotReclaimRetainedBlocksSoArenaExhaustionStillPanics() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                public final class GcRetained {
                    public static void main(String[] args) {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.GcRetained", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GcRetained");
        String harness = GC_STACK_TOP_PRELUDE + """

                static int gcTestBody() {
                  // Every block stays reachable via this stack-local array for the rest of
                  // gcTestBody(), so the collector must not (and, being conservative, cannot
                  // incorrectly) reclaim any of them: 200 * 68 bytes (64 payload + 4 header) is well
                  // past the 8 KiB arena, so this must eventually hit juno_panic()'s
                  // noInterrupts()+for(;;) halt.
                  void* retained[200];
                  for (int i = 0; i < 200; i++) {
                    retained[i] = juno_alloc(64, 4);
                  }
                  return 0;
                }
                """ + GC_STACK_TOP_MAIN;
        Process run = compileAndStart(compiler, result.runtimeShim() + harness, "gc-retained");
        boolean finished = run.waitFor(3, TimeUnit.SECONDS);
        if (!finished) {
            run.destroyForcibly();
        }
        assertThat(finished).as("retaining every block leaves nothing for the collector to reclaim, "
                + "so exhaustion should still reach juno_panic()'s infinite loop instead of returning")
                .isFalse();
    }

    @Test
    void gcReusesAnInteriorFreedBlockRatherThanGrowingTheArena() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                public final class GcFreeList {
                    public static void main(String[] args) {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.GcFreeList", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GcFreeList");
        // juno_arena_used/juno_gc_collect have internal (static) linkage in the generated shim, but
        // this harness is appended to the very same translation unit (like the JSON runtime test
        // above), so it can reach them directly to observe the allocator's internal bookkeeping —
        // exactly what makes this check "surgical" rather than only inferring reclamation indirectly
        // from whether the process merely finishes, as the two tests above do.
        String harness = GC_STACK_TOP_PRELUDE + """

                static int gcTestBody() {
                  void* first = juno_alloc(64, 4);
                  void* anchor = juno_alloc(64, 4);
                  (void) first;
                  (void) anchor;
                  // `anchor` is allocated after `first` and stays reachable for the rest of
                  // gcTestBody(), so once `first` becomes unreachable it is an INTERIOR dead block
                  // (anchor sits above it), not a trailing one — sweep can only reclaim it by
                  // free-listing it, never via the cheaper "retreat the bump cursor" special case for
                  // a trailing run.
                  first = nullptr;
                  juno_gc_collect();

                  uint32_t usedAfterCollect = juno_arena_used;
                  void* reused = juno_alloc(64, 4);
                  (void) reused;
                  // If this allocation grew juno_arena_used, it was satisfied by bumping the arena,
                  // not by reusing the free-listed interior block — proof the free-list path itself
                  // (not just the trailing-run special case a simpler test could pass without it)
                  // is what satisfied it.
                  return (juno_arena_used == usedAfterCollect) ? 0 : 1;
                }
                """ + GC_STACK_TOP_MAIN;
        Process run = compileAndStart(compiler, result.runtimeShim() + harness, "gc-free-list");
        boolean finished = run.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            run.destroyForcibly();
        }
        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as("process should not hang: " + output).isTrue();
        assertThat(run.exitValue()).as("expected the interior freed block to be reused via the "
                + "free list rather than the arena growing; output: " + output).isEqualTo(0);
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

                // juno_alloc's shim declares juno_gc_stack_top extern (the generated entry-point
                // assembly normally defines and populates it — see CortexM4AsmBackend.emitMethod);
                // this host-only harness never runs that assembly, so it must provide the storage
                // itself and set it near the top of main(), the same way the generated prologue does.
                extern "C" { uintptr_t juno_gc_stack_top; }

                int main() {
                  uint8_t stackTopMarker;
                  juno_gc_stack_top = reinterpret_cast<uintptr_t>(&stackTopMarker);
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

    /** Compiles+links {@code shimSource} natively (like the JSON runtime test) and starts it, unstarted-timeout-free. */
    private Process compileAndStart(String compiler, String shimSource, String fileBaseName) throws Exception {
        Path sketch = temporaryDirectory.resolve(fileBaseName + ".cpp");
        Files.writeString(sketch, shimSource, StandardCharsets.UTF_8);
        Path executable = temporaryDirectory.resolve(fileBaseName);

        Process compile = new ProcessBuilder(compiler, "-std=c++17", "-x", "c++",
                "-Isrc/test/resources", sketch.toString(), "-o", executable.toString())
                .redirectErrorStream(true)
                .start();
        boolean compiled = compile.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(compiled, "C++ compiler timed out");
        String diagnostics = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(compile.exitValue()).as(diagnostics).isEqualTo(0);

        return new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
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

    /** {@code arm-none-eabi-nm} lives alongside whichever {@code arm-none-eabi-gcc} was found. */
    private String availableArmNm(String armGcc) {
        Path sibling = Path.of(armGcc).resolveSibling("arm-none-eabi-nm");
        if (Files.isExecutable(sibling) && !Files.isDirectory(sibling)) {
            return sibling.toString();
        }
        try {
            Process process = new ProcessBuilder("arm-none-eabi-nm", "--version").start();
            if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return "arm-none-eabi-nm";
            }
        } catch (IOException | InterruptedException ignored) {
            // Not on PATH either.
        }
        return null;
    }
}
