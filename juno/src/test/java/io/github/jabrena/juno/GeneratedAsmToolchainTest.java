package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
                import io.github.jabrena.juno.api.Random;
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
                        Random.seed(42);
                        int randomValue = Random.nextInt(1, 7);
                        Gpio.digitalWrite(12, randomValue > 0);
                        LedMatrix.begin();
                        LedMatrix.loadFrame(0x3184a444, 0x44042081, 0x100a0040);
                        LedMatrix.clear();
                        Serial.begin(BaudRate.BAUD_9600);
                        Serial.print(Long.MIN_VALUE);
                        Serial.print(Float.MIN_VALUE);
                        Serial.print(Double.MIN_VALUE);
                        Serial.println("ready");
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmSmoke", source);
    }

    @Test
    void assemblesATftTouchShieldProgram() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.tft.TftTouchShield;
                public final class AsmTft {
                    public static void main(String[] args) {
                        TftTouchShield.begin();
                        TftTouchShield.fillScreen(TftTouchShield.BLACK);
                        TftTouchShield.drawRect(0, 0, 20, 20, TftTouchShield.GREEN);
                        TftTouchShield.println("Juno");
                        if (TftTouchShield.readTouch()) {
                            TftTouchShield.drawPixel(TftTouchShield.touchX(), TftTouchShield.touchY(), TftTouchShield.RED);
                        }
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmTft", source);
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
    void assemblesClosedWorldInterfaceDispatch() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class AsmInterfaces {
                    interface Operation { int apply(int value); }
                    static final class Add implements Operation {
                        public int apply(int value) { return value + 2; }
                    }
                    static final class Multiply implements Operation {
                        public int apply(int value) { return value * 3; }
                    }
                    static Operation choose(boolean add) {
                        return add ? new Add() : new Multiply();
                    }
                    public static void main(String[] args) {
                        Operation operation = choose(Gpio.analogRead(0) > 0);
                        Delay.millis(operation.apply(5));
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmInterfaces", source);
    }

    @Test
    void assemblesLambdasClosuresAndMethodReferences() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class AsmLambdas {
                    interface Operation { int apply(int value); }
                    static final class Adder {
                        private int amount;
                        Adder(int amount) { this.amount = amount; }
                        int apply(int value) { return value + amount; }
                    }
                    static int twice(int value) { return value * 2; }
                    static int run(Operation operation, int value) { return operation.apply(value); }
                    public static void main(String[] args) {
                        int amount = 3;
                        Operation captured = value -> value + amount;
                        Operation referenced = AsmLambdas::twice;
                        Adder adder = new Adder(4);
                        Operation bound = adder::apply;
                        Delay.millis(run(captured, 1) + run(referenced, 2) + run(bound, 3));
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmLambdas", source);
    }

    @Test
    void assemblesThreadsWithAClassAndALambdaRunnable() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class AsmThreads {
                    static final class Job implements Runnable {
                        public void run() { Delay.millis(1); }
                    }
                    public static void main(String[] args) throws InterruptedException {
                        int pause = 2;
                        Thread first = new Thread(new Job());
                        Thread second = new Thread(() -> Delay.millis(pause));
                        first.start();
                        second.start();
                        first.join();
                        second.join();
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmThreads", source);
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
                import io.github.jabrena.juno.api.io.net.http.HttpsClient;
                import io.github.jabrena.juno.api.io.net.http.Json;
                import io.github.jabrena.juno.api.io.net.Wifi;
                public final class AsmNetworking {
                    public static void main(String[] args) {
                        Wifi.begin("ssid", "password");
                        byte[] response = new byte[64];
                        byte[] headers = new byte[64];
                        int[] out = new int[2];
                        HttpsClient.get("example.com", 443, "/status", response, response.length,
                                headers, headers.length, out);
                        byte[] path = new byte[8];
                        path[0] = '/';
                        HttpsClient.get("example.com", 443, path, 1, response, response.length,
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
    void compilesAnUnoQRouterBridgeHttpsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.net.Wifi;
                import io.github.jabrena.juno.api.io.net.http.HttpsClient;
                @Board(ArduinoUnoQ.class)
                public final class AsmUnoQHttps {
                    public static void main(String[] args) {
                        Wifi.begin("ssid", "password");
                        byte[] response = new byte[64];
                        byte[] headers = new byte[64];
                        int[] out = new int[2];
                        HttpsClient.get("example.com", 443, "/", response, 64, headers, 64, out);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmUnoQHttps", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmUnoQHttps");
        Path shim = temporaryDirectory.resolve("AsmUnoQHttpsShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAnUnoQRouterBridgeEmailShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.net.email.Pop3Client;
                import io.github.jabrena.juno.api.io.net.email.Smtp;
                @Board(ArduinoUnoQ.class)
                public final class AsmUnoQEmail {
                    public static void main(String[] args) {
                        Smtp.sendTls("mail.example.com", 465, "me@example.com", "secret",
                                "me@example.com", "me@example.com", "Hello", "Hello");
                        Pop3Client.messageCount("mail.example.com", 995, "me@example.com", "secret");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmUnoQEmail", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmUnoQEmail");
        Path shim = temporaryDirectory.resolve("AsmUnoQEmailShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesPortableUdpShimsForBothArduinoCores() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.net.Udp;
                @Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
                public final class AsmUdp {
                    public static void main(String[] args) {
                        byte[] payload = new byte[12];
                        int[] source = new int[Udp.ENDPOINT_SIZE];
                        Udp.listen(5000);
                        Udp.broadcast(5000, payload, payload.length);
                        Udp.send(source, 5000, payload, payload.length);
                        Udp.receive(payload, payload.length, source);
                        Udp.stop();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmUdp", source);
        List<Path> classpath = List.of(temporaryDirectory, Path.of("target/classes"));
        JunoCompiler juno = new JunoCompiler();

        CompilationResult r4 = juno.compile(classpath, "demo.AsmUdp", false,
                Optional.of("arduino-uno-r4-wifi"));
        Path r4Shim = temporaryDirectory.resolve("AsmUdpR4Shim.cpp");
        Files.writeString(r4Shim, r4.runtimeShim(), StandardCharsets.UTF_8);
        syntaxCheckCpp(compiler, r4Shim);

        CompilationResult q = juno.compile(classpath, "demo.AsmUdp", false,
                Optional.of("arduino-uno-q"));
        Path qShim = temporaryDirectory.resolve("AsmUdpQShim.cpp");
        Files.writeString(qShim, q.runtimeShim(), StandardCharsets.UTF_8);
        syntaxCheckCpp(compiler, qShim);
    }

    @Test
    void compilesAnSdPropertiesProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Wifi;
                import io.github.jabrena.juno.api.io.storage.SdCard;
                import java.io.IOException;
                import java.io.InputStream;
                import java.util.Properties;
                public final class AsmSdProperties {
                    public static void main(String[] args) throws IOException {
                        if (!SdCard.begin()) return;
                        InputStream file = SdCard.open("application.properties");
                        if (file == null) return;
                        Properties properties = new Properties();
                        properties.load(file);
                        file.close();
                        String ssid = properties.getProperty("wifi.ssid");
                        String password = properties.getProperty("wifi.password");
                        if (ssid != null && password != null) Wifi.begin(ssid, password);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmSdProperties", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmSdProperties");
        Path shim = temporaryDirectory.resolve("AsmSdPropertiesShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /**
     * {@code io.github.jabrena.juno.backend.NetworkShimLibraries#httpServerHelpers} hand-writes
     * placement-new construction of a static {@code WiFiServer} and reads a {@code StringBuilder}
     * handle's raw arena bytes directly — exactly the kind of shim code most likely to have a real
     * C++ syntax error that a hand-rolled IR unit test would never catch, so this compiles it for
     * real against the module's mock {@code WiFiS3.h} (extended with a minimal {@code WiFiServer}
     * for this).
     */
    @Test
    void compilesAnHttpServerProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.http.HttpServer;
                public final class AsmHttpServer {
                    public static void main(String[] args) {
                        HttpServer.begin(80);
                        byte[] body = new byte[64];
                        int bodyLength = HttpServer.accept(body, body.length);
                        if (bodyLength >= 0) {
                            if (HttpServer.method().equals("GET") && HttpServer.path().equals("/status")) {
                                HttpServer.respond(200, "application/json", "{\\"ok\\":true}");
                            } else {
                                StringBuilder json = new StringBuilder(32);
                                json.append('{').append('}');
                                HttpServer.respond(404, "application/json", json);
                            }
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmHttpServer", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmHttpServer");
        Path shim = temporaryDirectory.resolve("AsmHttpServerShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    private static final String EXCEPTIONS = """
            package demo;
            import io.github.jabrena.juno.api.Clock;
            import io.github.jabrena.juno.api.io.usb.Serial;
            public final class AsmExceptions {
                static final class SensorException extends RuntimeException {
                    final int code;
                    SensorException(String message, int code) {
                        super(message);
                        this.code = code;
                    }
                }
                public static void main(String[] args) {
                    int reading = Clock.millis();
                    try {
                        if (reading < 0) throw new SensorException("offline", 7);
                        if (reading > 1000) throw new IllegalStateException();
                    } catch (SensorException e) {
                        Serial.println(e.code);
                    } catch (RuntimeException e) {
                        Serial.println(e.getMessage());
                    } finally {
                        Serial.println("done");
                    }
                    throw new UnsupportedOperationException("halt");
                }
            }
            """;

    @Test
    void assemblesAnExceptionsProgramAndCompilesItsShim() throws Exception {
        String armGcc = availableArmGcc();
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(armGcc != null || compiler != null, "No toolchain available");
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmExceptions", EXCEPTIONS);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmExceptions");
        if (armGcc != null) {
            assembleAndCompile(armGcc, "demo.AsmExceptions");
        }
        if (compiler != null) {
            Path shim = temporaryDirectory.resolve("AsmExceptionsShim.cpp");
            Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);
            syntaxCheckCpp(compiler, shim);
        }
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
     * {@link io.github.jabrena.juno.backend.Thumb2AsmBackend#emitMethod}) so the conservative GC's
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
                import io.github.jabrena.juno.api.io.net.http.Json;
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
                // assembly normally defines and populates it — see Thumb2AsmBackend.emitMethod);
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

    /** Reaches every supported {@code java.lang.Math} overload, so the shim carries all of their helpers. */
    private static final String MATH_EVERYTHING = """
            package demo;
            import io.github.jabrena.juno.api.Clock;
            import io.github.jabrena.juno.api.io.usb.Serial;
            public final class MathEverything {
                public static void main(String[] args) {
                    int i = Clock.millis() % 2 == 0 ? 3 : -7;
                    long l = i * 1000000000000L;
                    float f = i / 2.0f;
                    double d = i / 4.0;
                    int ints = Math.abs(i) + Math.min(i, 2) + Math.max(i, 2) + Math.clamp(l, -5, 5)
                            + Math.floorDiv(i, 2) + Math.floorMod(i, 2) + Math.floorMod(l, 3) + Math.round(f);
                    long longs = Math.abs(l) + Math.min(l, 2L) + Math.max(l, 2L) + Math.clamp(l, -5L, 5L)
                            + Math.floorDiv(l, 2L) + Math.floorMod(l, 2L) + Math.floorDiv(l, 3) + Math.round(d);
                    float floats = Math.abs(f) + Math.min(f, 1.0f) + Math.max(f, 1.0f)
                            + Math.clamp(f, -1.0f, 1.0f) + Math.signum(f);
                    double doubles = Math.abs(d) + Math.min(d, 1.0) + Math.max(d, 1.0) + Math.clamp(d, -1.0, 1.0)
                            + Math.signum(d) + Math.floor(d) + Math.ceil(d) + Math.sqrt(d) + Math.cbrt(d)
                            + Math.pow(d, 2.0) + Math.hypot(d, 3.0) + Math.exp(d) + Math.log(d) + Math.log10(d)
                            + Math.sin(d) + Math.cos(d) + Math.tan(d) + Math.asin(d) + Math.acos(d) + Math.atan(d)
                            + Math.atan2(d, 2.0) + Math.toRadians(d) + Math.toDegrees(d);
                    Serial.println(ints);
                    Serial.println(longs);
                    Serial.println(floats);
                    Serial.println(doubles);
                }
            }
            """;

    @Test
    void assemblesAProgramUsingEveryMathOverload() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        // Math.clamp is Java 21.
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MathEverything", MATH_EVERYTHING, "21");
        assembleAndCompile(armGcc, "demo.MathEverything");
    }

    /**
     * Runs every generated {@code juno_math_*} helper natively and compares it with what the real
     * {@code java.lang.Math} returns for the same edge-case inputs: bit-exact for every operation Java
     * specifies exactly, within 1e-14 (relative) for the {@code libm}-backed transcendental functions.
     */
    @Test
    void generatedMathShimMatchesJavaLangMathAtRuntime() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MathEverything", MATH_EVERYTHING, "21");
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MathEverything");

        int[] ints = {0, 1, -1, 7, -7, 2, -2, Integer.MIN_VALUE, Integer.MAX_VALUE};
        long[] longs = {0L, 1L, -1L, 7L, -7L, 3L, 1L << 40, -(1L << 40) - 3, Long.MIN_VALUE, Long.MAX_VALUE};
        float[] floats = {0.0f, -0.0f, 1.5f, -1.5f, 2.5f, -2.5f, 0.49999997f, 3.0e9f, -3.0e9f,
                Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        double[] doubles = {0.0, -0.0, 0.5, -0.5, 1.0, -1.0, 2.5, -2.5, 0.49999999999999994, 0.75, 10.0,
                1.0e19, -1.0e19, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        StringBuilder checks = new StringBuilder();
        for (int a : ints) {
            checks.append(exactInt("juno_math_abs_int(" + cInt(a) + ")", Math.abs(a)));
            checks.append(exactInt("juno_math_round_float(" + cFloat(a) + ")", Math.round((float) a)));
            for (int b : ints) {
                checks.append(exactInt("juno_math_min_int(" + cInt(a) + ", " + cInt(b) + ")", Math.min(a, b)));
                checks.append(exactInt("juno_math_max_int(" + cInt(a) + ", " + cInt(b) + ")", Math.max(a, b)));
                if (b != 0) {
                    checks.append(exactInt("juno_math_floor_div_int(" + cInt(a) + ", " + cInt(b) + ")",
                            Math.floorDiv(a, b)));
                    checks.append(exactInt("juno_math_floor_mod_int(" + cInt(a) + ", " + cInt(b) + ")",
                            Math.floorMod(a, b)));
                }
            }
        }
        for (long a : longs) {
            checks.append(exactLong("juno_math_abs_long(" + cLong(a) + ")", Math.abs(a)));
            checks.append(exactInt("juno_math_clamp_int(" + cLong(a) + ", -5, 5)", Math.clamp(a, -5, 5)));
            checks.append(exactLong("juno_math_clamp_long(" + cLong(a) + ", " + cLong(-3L) + ", " + cLong(1L << 41)
                    + ")", Math.clamp(a, -3L, 1L << 41)));
            for (int b : ints) {
                if (b != 0) {
                    checks.append(exactLong("juno_math_floor_div_long_int(" + cLong(a) + ", " + cInt(b) + ")",
                            Math.floorDiv(a, b)));
                    checks.append(exactInt("juno_math_floor_mod_long_int(" + cLong(a) + ", " + cInt(b) + ")",
                            Math.floorMod(a, b)));
                }
            }
            for (long b : longs) {
                checks.append(exactLong("juno_math_min_long(" + cLong(a) + ", " + cLong(b) + ")", Math.min(a, b)));
                checks.append(exactLong("juno_math_max_long(" + cLong(a) + ", " + cLong(b) + ")", Math.max(a, b)));
                if (b != 0) {
                    checks.append(exactLong("juno_math_floor_div_long(" + cLong(a) + ", " + cLong(b) + ")",
                            Math.floorDiv(a, b)));
                    checks.append(exactLong("juno_math_floor_mod_long(" + cLong(a) + ", " + cLong(b) + ")",
                            Math.floorMod(a, b)));
                }
            }
        }
        for (float a : floats) {
            checks.append(exactFloat("juno_math_abs_float(" + cFloat(a) + ")", Math.abs(a)));
            checks.append(exactFloat("juno_math_signum_float(" + cFloat(a) + ")", Math.signum(a)));
            checks.append(exactInt("juno_math_round_float(" + cFloat(a) + ")", Math.round(a)));
            checks.append(exactFloat("juno_math_clamp_float(" + cFloat(a) + ", " + cFloat(-0.0f) + ", 2.0f)",
                    Math.clamp(a, -0.0f, 2.0f)));
            for (float b : floats) {
                checks.append(exactFloat("juno_math_min_float(" + cFloat(a) + ", " + cFloat(b) + ")", Math.min(a, b)));
                checks.append(exactFloat("juno_math_max_float(" + cFloat(a) + ", " + cFloat(b) + ")", Math.max(a, b)));
            }
        }
        for (double a : doubles) {
            String x = cDouble(a);
            checks.append(exactDouble("juno_math_abs_double(" + x + ")", Math.abs(a)));
            checks.append(exactDouble("juno_math_signum_double(" + x + ")", Math.signum(a)));
            checks.append(exactLong("juno_math_round_double(" + x + ")", Math.round(a)));
            checks.append(exactDouble("juno_math_clamp_double(" + x + ", " + cDouble(-0.0) + ", 2.0)",
                    Math.clamp(a, -0.0, 2.0)));
            checks.append(exactDouble("juno_math_floor(" + x + ")", Math.floor(a)));
            checks.append(exactDouble("juno_math_ceil(" + x + ")", Math.ceil(a)));
            checks.append(exactDouble("juno_math_sqrt(" + x + ")", Math.sqrt(a)));
            checks.append(exactDouble("juno_math_to_radians(" + x + ")", Math.toRadians(a)));
            checks.append(exactDouble("juno_math_to_degrees(" + x + ")", Math.toDegrees(a)));
            checks.append(nearDouble("juno_math_cbrt(" + x + ")", Math.cbrt(a)));
            checks.append(nearDouble("juno_math_exp(" + x + ")", Math.exp(a)));
            checks.append(nearDouble("juno_math_log(" + x + ")", Math.log(a)));
            checks.append(nearDouble("juno_math_log10(" + x + ")", Math.log10(a)));
            checks.append(nearDouble("juno_math_sin(" + x + ")", Math.sin(a)));
            checks.append(nearDouble("juno_math_cos(" + x + ")", Math.cos(a)));
            checks.append(nearDouble("juno_math_tan(" + x + ")", Math.tan(a)));
            checks.append(nearDouble("juno_math_asin(" + x + ")", Math.asin(a)));
            checks.append(nearDouble("juno_math_acos(" + x + ")", Math.acos(a)));
            checks.append(nearDouble("juno_math_atan(" + x + ")", Math.atan(a)));
            for (double b : doubles) {
                String y = cDouble(b);
                checks.append(exactDouble("juno_math_min_double(" + x + ", " + y + ")", Math.min(a, b)));
                checks.append(exactDouble("juno_math_max_double(" + x + ", " + y + ")", Math.max(a, b)));
                checks.append(nearDouble("juno_math_pow(" + x + ", " + y + ")", Math.pow(a, b)));
                checks.append(nearDouble("juno_math_hypot(" + x + ", " + y + ")", Math.hypot(a, b)));
                checks.append(nearDouble("juno_math_atan2(" + x + ", " + y + ")", Math.atan2(a, b)));
            }
        }
        String harness = """

                #include <stdio.h>
                #include <string.h>
                extern "C" { uintptr_t juno_gc_stack_top; }
                static float junoFloatBits(uint32_t bits) { float value; memcpy(&value, &bits, 4); return value; }
                static double junoDoubleBits(uint64_t bits) { double value; memcpy(&value, &bits, 8); return value; }
                static int junoFailures = 0;
                static void junoCheck(bool ok, const char* expression) {
                  if (!ok) { junoFailures++; printf("mismatch: %s\\n", expression); }
                }
                static bool junoSameFloat(float actual, uint32_t expected) {
                  uint32_t bits; memcpy(&bits, &actual, 4);
                  bool expectedNaN = (expected & 0x7f800000u) == 0x7f800000u && (expected & 0x007fffffu) != 0;
                  return expectedNaN ? actual != actual : bits == expected;
                }
                static bool junoSameDouble(double actual, uint64_t expected) {
                  uint64_t bits; memcpy(&bits, &actual, 8);
                  bool expectedNaN = (expected & 0x7ff0000000000000ull) == 0x7ff0000000000000ull
                      && (expected & 0x000fffffffffffffull) != 0;
                  return expectedNaN ? actual != actual : bits == expected;
                }
                static bool junoNearDouble(double actual, uint64_t expectedBits) {
                  double expected; memcpy(&expected, &expectedBits, 8);
                  if (expected != expected) return actual != actual;
                  if (actual == expected) return true;
                  return fabs(actual - expected) <= 1e-14 * fabs(expected);
                }
                int main() {
                  uint8_t stackTopMarker;
                  juno_gc_stack_top = reinterpret_cast<uintptr_t>(&stackTopMarker);
                """ + checks + """
                  return junoFailures == 0 ? 0 : 1;
                }
                """;
        Process run = compileAndStart(compiler, result.runtimeShim() + harness, "math-runtime");
        boolean finished = run.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "Generated Math runtime test timed out");
        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(run.exitValue()).as("mismatches against java.lang.Math:\n" + output).isEqualTo(0);
    }

    private static String cInt(int value) {
        return "static_cast<int32_t>(0x" + Integer.toHexString(value) + "u)";
    }

    private static String cLong(long value) {
        return "static_cast<int64_t>(0x" + Long.toHexString(value) + "ull)";
    }

    private static String cFloat(float value) {
        return "junoFloatBits(0x" + Integer.toHexString(Float.floatToRawIntBits(value)) + "u)";
    }

    private static String cDouble(double value) {
        return "junoDoubleBits(0x" + Long.toHexString(Double.doubleToRawLongBits(value)) + "ull)";
    }

    private static String exactInt(String call, int expected) {
        return "  junoCheck(" + call + " == " + cInt(expected) + ", \"" + call + "\");\n";
    }

    private static String exactLong(String call, long expected) {
        return "  junoCheck(" + call + " == " + cLong(expected) + ", \"" + call + "\");\n";
    }

    private static String exactFloat(String call, float expected) {
        return "  junoCheck(junoSameFloat(" + call + ", 0x" + Integer.toHexString(Float.floatToRawIntBits(expected))
                + "u), \"" + call + "\");\n";
    }

    private static String exactDouble(String call, double expected) {
        return "  junoCheck(junoSameDouble(" + call + ", 0x" + Long.toHexString(Double.doubleToRawLongBits(expected))
                + "ull), \"" + call + "\");\n";
    }

    private static String nearDouble(String call, double expected) {
        return "  junoCheck(junoNearDouble(" + call + ", 0x" + Long.toHexString(Double.doubleToRawLongBits(expected))
                + "ull), \"" + call + "\");\n";
    }

    private void assembleAndCompile(String armGcc, String mainClass, String source) throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, mainClass, source);
        assembleAndCompile(armGcc, mainClass);
    }

    private void assembleAndCompile(String armGcc, String mainClass) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
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
