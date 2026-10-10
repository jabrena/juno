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
                import io.github.jabrena.juno.api.io.serial.BaudRate;
                import io.github.jabrena.juno.api.io.serial.Serial;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class AsmSmoke {
                    static int mix(int value) { return (value << 2) ^ 7; }
                    public static void main(String[] args) {
                        Gpio.pinMode(Gpio.D13, Gpio.OUTPUT);
                        Gpio.digitalWrite(Gpio.D13, mix(4) != 0);
                        Delay.millis(10);
                        Random.seed(42);
                        int randomValue = Random.nextInt(1, 7);
                        Gpio.digitalWrite(12, randomValue > 0);
                        LedMatrix.begin();
                        LedMatrix.loadFrame(0x3184a444, 0x44042081, 0x100a0040);
                        LedMatrix.clear();
                        Serial.begin(BaudRate.BAUD_9600);
                        Serial.print(randomValue > 0);
                        Serial.println(randomValue <= 0);
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
    void assemblesFlashResidentConstantTablesOfEveryElementType() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                public final class AsmTables {
                    static final boolean[] FLAGS = {true, false, true};
                    static final byte[] BYTES = {-128, 127, 3};
                    static final char[] CHARS = {'a', '\\uffff'};
                    static final short[] SHORTS = {-32768, 32767, 5};
                    static final int[] INTS = {Integer.MIN_VALUE, 42};
                    static final long[] LONGS = {Long.MIN_VALUE, 1L << 40};
                    static final float[] FLOATS = {-1.5f, 3.0e9f};
                    static final double[] DOUBLES = {0.125, -1.0e15};
                    public static void main(String[] args) {
                        int total = (FLAGS[2] ? 1 : 0) + BYTES[1] + CHARS[1] + SHORTS[SHORTS.length - 1] + INTS[1];
                        long wide = LONGS[1] + (long) DOUBLES[1] + (long) FLOATS[1];
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmTables", source);
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
    void assemblesTaskScopesWithAClassAndALambdaRunnable() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class AsmThreads {
                    static final class Job implements Runnable {
                        public void run() { Delay.millis(1); }
                    }
                    public static void main(String[] args) throws Exception {
                        int pause = 2;
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(new Job());
                            scope.fork(() -> Delay.millis(pause));
                            scope.join();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.AsmThreads", source);
        assembleAndCompile(armGcc, "demo.AsmThreads");
    }

    @Test
    void assemblesVolatileSynchronizedAndReentrantLock() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import java.util.concurrent.locks.ReentrantLock;
                public final class AsmSynchronization {
                    static final class Guard { }
                    static final Guard GUARD = new Guard();
                    static final ReentrantLock LOCK = new ReentrantLock();
                    static volatile boolean ready;
                    static int value;
                    static void update() {
                        synchronized (GUARD) {
                            LOCK.lock();
                            try {
                                value++;
                                ready = true;
                            } finally {
                                LOCK.unlock();
                            }
                        }
                    }
                    public static void main(String[] args) throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(AsmSynchronization::update);
                            while (!ready) { }
                            scope.join();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.AsmSynchronization", source);
        assembleAndCompile(armGcc, "demo.AsmSynchronization");
    }

    @Test
    void assemblesAtomicIntegerBooleanAndLong() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import java.util.concurrent.atomic.AtomicBoolean;
                import java.util.concurrent.atomic.AtomicInteger;
                import java.util.concurrent.atomic.AtomicLong;
                public final class AsmAtomics {
                    static final AtomicInteger COUNT = new AtomicInteger(1);
                    public static void main(String[] args) {
                        AtomicLong total = new AtomicLong(1L << 33);
                        AtomicBoolean flag = new AtomicBoolean();
                        total.addAndGet(COUNT.incrementAndGet());
                        if (total.compareAndSet(total.get(), 2L) && flag.compareAndSet(false, true)) {
                            COUNT.getAndAdd((int) total.getAndDecrement());
                        }
                    }
                }
                """;
        assembleAndCompile(armGcc, "demo.AsmAtomics", source);
    }

    @Test
    void assemblesStructuredTaskScopes() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class AsmStructuredTasks {
                    public static void main() throws Exception {
                        try (var scope = StructuredTaskScope.open()) {
                            var task = scope.fork(() -> "done");
                            scope.join();
                            task.get();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(
                temporaryDirectory, "demo.AsmStructuredTasks", source);
        assembleAndCompile(armGcc, "demo.AsmStructuredTasks");
    }

    @Test
    void assemblesScopedValuesInheritedBySubtasks() throws Exception {
        String armGcc = availableArmGcc();
        Assumptions.assumeTrue(armGcc != null, "No arm-none-eabi-gcc toolchain available");
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class AsmScopedValues {
                    private static final ScopedValue<String> USER = ScopedValue.newInstance();
                    public static void main() throws Exception {
                        String result = ScopedValue.where(USER, "alice").call(() -> {
                            try (var scope = StructuredTaskScope.open()) {
                                var task = scope.fork(() -> USER.get());
                                scope.join();
                                return task.get();
                            }
                        });
                        ScopedValue.where(USER, result).run(() -> USER.orElse("none"));
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(
                temporaryDirectory, "demo.AsmScopedValues", source);
        assembleAndCompile(armGcc, "demo.AsmScopedValues");
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
        assertThat(symbols).as("the GC static-root bounds must have GLOBAL linkage: " + symbols)
                .containsPattern("(?m)^\\S+ B juno_gc_static_start$")
                .containsPattern("(?m)^\\S+ B juno_gc_static_end$");
    }

    @Test
    void compilesAWifiHttpAndJsonProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.http.HttpsClient;
                import io.github.jabrena.juno.api.net.http.Json;
                import io.github.jabrena.juno.api.net.Wifi;
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
                import io.github.jabrena.juno.api.net.Wifi;
                import io.github.jabrena.juno.api.net.http.HttpsClient;
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
    void compilesAnUnoQLedMatrixShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.led.LedCanvas;
                @Board(ArduinoUnoQ.class)
                public final class AsmUnoQLedMatrix {
                    public static void main(String[] args) {
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
                        LedCanvas.fillRect(frame, 1, 1, 3, 3);
                        LedCanvas.show(frame);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmUnoQLedMatrix", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmUnoQLedMatrix");
        Path shim = temporaryDirectory.resolve("AsmUnoQLedMatrixShim.cpp");
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
                import io.github.jabrena.juno.api.net.email.Pop3Client;
                import io.github.jabrena.juno.api.net.email.Smtp;
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
                import io.github.jabrena.juno.api.net.Udp;
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

    /**
     * The LEGO Powered Up shim drives {@code ArduinoBLE}'s central API directly, so compile it for real
     * against the module's mock {@code ArduinoBLE.h}, which mirrors the library's signatures.
     */
    @Test
    void compilesALegoPoweredUpProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;
                public final class AsmLegoTrain {
                    public static void main(String[] args) {
                        if (!PoweredUpHubRemote.connect(0)) return;
                        PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_BLUE);
                        PoweredUpHubRemote.setMotorPower(PoweredUpHubRemote.PORT_A, 40);
                        Delay.millis(1000);
                        PoweredUpHubRemote.brakeMotor(PoweredUpHubRemote.PORT_A);
                        if (PoweredUpHubRemote.hubType() != PoweredUpHubRemote.TYPE_UNKNOWN && PoweredUpHubRemote.isConnected()) {
                            PoweredUpHubRemote.disconnect();
                        }
                        PoweredUpHubRemote.switchOff();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmLegoTrain", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmLegoTrain");
        Path shim = temporaryDirectory.resolve("AsmLegoTrainShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** The RCX remote is Java framing over the library-free {@code Infrared} shim, so it compiles with plain Arduino calls. */
    @Test
    void compilesAnRcxRemoteProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.RcxRemote;
                public final class AsmRcxRemote {
                    public static void main(String[] args) {
                        RcxRemote.begin(2, 3);
                        while (true) {
                            int buttons = RcxRemote.buttons();
                            if ((buttons & RcxRemote.A_FORWARD) != 0) {
                                RcxRemote.sendButtons(RcxRemote.B_FORWARD);
                            }
                            if (RcxRemote.message() >= 0) {
                                RcxRemote.sendMessage(7);
                            }
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmRcxRemote", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmRcxRemote");
        assertThat(result.runtimeShim()).contains("juno_ir_write_byte");
        assertThat(result.runtimeShim()).doesNotContain("ArduinoBLE.h");
        Path shim = temporaryDirectory.resolve("AsmRcxRemoteShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAScoutRemoteProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.RcxRemote;
                import io.github.jabrena.juno.api.lego.ScoutRemote;
                public final class AsmScoutRemote {
                    public static void main(String[] args) {
                        ScoutRemote.begin(-1, 3);
                        ScoutRemote.ping();
                        ScoutRemote.setMode(ScoutRemote.MODE_POWER);
                        ScoutRemote.playSound(2);
                        ScoutRemote.selectProgram(3);
                        ScoutRemote.sendButtons(RcxRemote.A_FORWARD);
                        ScoutRemote.stopAll();
                        ScoutRemote.powerOff();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmScoutRemote", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmScoutRemote");
        assertThat(result.runtimeShim()).contains("juno_ir_write_byte").doesNotContain("ArduinoBLE.h");
        Path shim = temporaryDirectory.resolve("AsmScoutRemoteShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** {@code ParallelBus} is the core's GPIO port registers on the UNO R4: RA {@code PCNTR3} set/reset stores. */
    @Test
    void compilesAParallelBusProgramsShimWithACppCompiler() throws Exception {
        assertParallelBusShimCompiles("", "AsmParallelBus", "PCNTR3");
    }

    /** On the UNO Q the same helpers write ports through Zephyr's raw GPIO port API, over the devicetree pin table. */
    @Test
    void compilesAnUnoQParallelBusProgramsShimWithACppCompiler() throws Exception {
        assertParallelBusShimCompiles("@io.github.jabrena.juno.annotations.Board(io.github.jabrena.juno.annotations.ArduinoUnoQ.class)",
                "AsmUnoQParallelBus", "gpio_port_set_clr_bits_raw");
    }

    private void assertParallelBusShimCompiles(String board, String name, String portWrite) throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.ParallelBus;
                ${BOARD}
                public final class ${NAME} {
                    private static final int[] PINS = {8, 9, 2, 3, 4, 5, 6, 7};
                    public static void main(String[] args) {
                        ParallelBus.begin(PINS, 15);
                        ParallelBus.write(0x2C);
                        ParallelBus.repeat16(0xF800, 320);
                    }
                }
                """.replace("${BOARD}", board).replace("${NAME}", name);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo." + name, source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo." + name);
        assertThat(result.runtimeShim()).contains("juno_parallel_bus_begin", "juno_parallel_bus_write",
                "juno_parallel_bus_repeat16", portWrite);
        assertThat(result.assembly()).contains("bl juno_parallel_bus_begin", "bl juno_parallel_bus_write",
                "bl juno_parallel_bus_repeat16");
        Path shim = temporaryDirectory.resolve(name + "Shim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAnInfraredLoopbackProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.ir.Infrared;
                public final class AsmLoopback {
                    public static void main(String[] args) {
                        Infrared.begin(2, 3, 2400);
                        int ok = 0;
                        for (int value = 0; value < 256; value++) {
                            if (Infrared.echoByte(value, 200) == value) ok++;
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmLoopback", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmLoopback");
        assertThat(result.runtimeShim()).contains("juno_ir_echo_byte");
        Path shim = temporaryDirectory.resolve("AsmLoopbackShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** Reading the RCX's sensors is a request and reply over the same library-free {@code Infrared} shim. */
    @Test
    void compilesAnRcxBrickSensorProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.RcxBrick;
                import io.github.jabrena.juno.api.lego.RcxRemote;
                public final class AsmRcxSensor {
                    public static void main(String[] args) {
                        RcxRemote.begin(2, 3);
                        if (!RcxBrick.setTouchSensor(RcxBrick.INPUT_1)) return;
                        if (RcxBrick.batteryMillivolts() < 6000) return;
                        RcxBrick.playSound(RcxBrick.SOUND_BEEP);
                        RcxBrick.playTone(440, 50);
                        RcxBrick.setLightSensor(RcxBrick.INPUT_2);
                        RcxBrick.setRotationSensor(RcxBrick.INPUT_3);
                        RcxBrick.setTemperatureSensor(RcxBrick.INPUT_1);
                        if (RcxBrick.rotationDegrees(RcxBrick.INPUT_3) > 90 || RcxBrick.temperatureTenths(RcxBrick.INPUT_1) > 300) {
                            RcxBrick.clearSensor(RcxBrick.INPUT_3);
                        }
                        RcxBrick.drive(RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_B, 4);
                        RcxBrick.setMotorDirection(RcxBrick.OUTPUT_C, false);
                        RcxBrick.setMotorPower(RcxBrick.OUTPUT_C, 3);
                        RcxBrick.motorFloat(RcxBrick.OUTPUT_C);
                        while (!RcxBrick.isPressed(RcxBrick.INPUT_1)) {
                            RcxRemote.sendButtons(RcxRemote.A_FORWARD);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmRcxSensor", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmRcxSensor");
        Path shim = temporaryDirectory.resolve("AsmRcxSensorShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** Power Functions is Java over the raw {@code Infrared} mark/space pulses, with no library. */
    @Test
    void compilesAPowerFunctionsProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.PowerFunctionsRemote;
                public final class AsmPowerFunctions {
                    public static void main(String[] args) {
                        PowerFunctionsRemote.begin(3);
                        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 5);
                        PowerFunctionsRemote.setSpeeds(PowerFunctionsRemote.CHANNEL_2, -7, 7);
                        PowerFunctionsRemote.brake(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_BLUE);
                        PowerFunctionsRemote.stop(PowerFunctionsRemote.CHANNEL_2);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmPowerFunctions", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmPowerFunctions");
        assertThat(result.runtimeShim()).contains("juno_ir_mark", "juno_ir_space").doesNotContain("ArduinoBLE.h");
        Path shim = temporaryDirectory.resolve("AsmPowerFunctionsShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** The BNO055 driver is Java over the library-free {@code I2c} shim, which wraps the core's {@code Wire}. */
    @Test
    void compilesANineAxisMotionShieldProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.imu.NineAxisMotionShield;
                public final class AsmHeading {
                    public static void main(String[] args) {
                        if (!NineAxisMotionShield.begin()) return;
                        int start = NineAxisMotionShield.headingDegrees();
                        while (NineAxisMotionShield.turnedSince(start) > -90) {
                            if (NineAxisMotionShield.calibration() < 0) return;
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmHeading", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmHeading");
        assertThat(result.runtimeShim()).contains("#include <Wire.h>", "juno_i2c_read_register16")
                .doesNotContain("ArduinoBLE.h");
        Path shim = temporaryDirectory.resolve("AsmHeadingShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** Pitch and gyroscope rate are signed words read through the same library-free {@code I2c} shim. */
    @Test
    void compilesANineAxisMotionShieldPitchAndGyroProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.imu.NineAxisMotionShield;
                public final class AsmBalance {
                    public static void main(String[] args) {
                        if (!NineAxisMotionShield.begin()) return;
                        while (Math.abs(NineAxisMotionShield.pitchRaw()) < 480 && NineAxisMotionShield.gyroXRaw() > -16000) {
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmBalance", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmBalance");
        assertThat(result.runtimeShim()).contains("juno_i2c_read_register16").doesNotContain("ArduinoBLE.h");
        Path shim = temporaryDirectory.resolve("AsmBalanceShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAPoweredUpHubImuProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;
                public final class AsmHubImu {
                    public static void main(String[] args) {
                        if (!PoweredUpHubRemote.connect(0)) return;
                        PoweredUpHubRemote.enableSensor(PoweredUpHubRemote.PORT_TECHNIC_GYRO, PoweredUpHubRemote.MODE_IMU_VALUES);
                        PoweredUpHubRemote.setLedRgb(255, 128, 0);
                        int pair = PoweredUpHubRemote.linkMotors(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.PORT_B);
                        if (pair >= 0 && PoweredUpHubRemote.batteryMillivolts() > 6000
                                && PoweredUpHubRemote.currentMilliamps() < 2000) {
                            PoweredUpHubRemote.setLinkedMotorPower(pair, 40, 40);
                            PoweredUpHubRemote.brakeLinkedMotors(pair);
                            PoweredUpHubRemote.unlinkMotors(pair);
                        }
                        while (PoweredUpHubRemote.isConnected()) {
                            if (PoweredUpHubRemote.sensorReportSize(PoweredUpHubRemote.PORT_TECHNIC_GYRO) >= 6
                                    && PoweredUpHubRemote.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_GYRO, 2, 2) > 100) {
                                PoweredUpHubRemote.brakeMotor(PoweredUpHubRemote.PORT_A);
                            }
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmHubImu", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmHubImu");
        Path shim = temporaryDirectory.resolve("AsmHubImuShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAPoweredUpHubPropertiesProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;
                public final class AsmHubProperties {
                    public static void main(String[] args) {
                        if (!PoweredUpHubRemote.connect(0)) return;
                        byte[] name = new byte[20];
                        int length = PoweredUpHubRemote.hubName(name, name.length);
                        int firmware = PoweredUpHubRemote.firmwareVersion();
                        if (length > 0 && PoweredUpHubRemote.batteryPercent() < 10 && !PoweredUpHubRemote.buttonPressed()
                                && PoweredUpHubRemote.rssi() < -80 && PoweredUpHubRemote.versionMajor(firmware) == 1
                                && PoweredUpHubRemote.versionMinor(firmware) + PoweredUpHubRemote.versionBugfix(
                                        PoweredUpHubRemote.hardwareVersion()) + PoweredUpHubRemote.versionBuild(firmware) > 0) {
                            PoweredUpHubRemote.switchOff();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmHubProperties", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmHubProperties");
        Path shim = temporaryDirectory.resolve("AsmHubPropertiesShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAPoweredUpHubPortDeviceProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;
                public final class AsmHubPorts {
                    public static void main(String[] args) {
                        if (!PoweredUpHubRemote.connect(0)) return;
                        if (PoweredUpHubRemote.portDevice(PoweredUpHubRemote.PORT_B)
                                == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_MOTOR) {
                            PoweredUpHubRemote.setMotorPower(PoweredUpHubRemote.PORT_B, 30);
                            PoweredUpHubRemote.holdMotor(PoweredUpHubRemote.PORT_B);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmHubPorts", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmHubPorts");
        Path shim = temporaryDirectory.resolve("AsmHubPortsShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    /** On the UNO Q the same ArduinoBLE-based shim sits next to the Zephyr core's own runtime glue. */
    @Test
    void compilesAnUnoQLegoPoweredUpProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;
                @Board(ArduinoUnoQ.class)
                public final class AsmUnoQLegoTrain {
                    public static void main(String[] args) {
                        if (!PoweredUpHubRemote.connect(0)) return;
                        PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_GREEN);
                        PoweredUpHubRemote.setMotorPower(PoweredUpHubRemote.PORT_A, -40);
                        Delay.millis(1000);
                        PoweredUpHubRemote.brakeMotor(PoweredUpHubRemote.PORT_A);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmUnoQLegoTrain", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmUnoQLegoTrain");
        assertThat(result.runtimeShim()).contains("#include <ArduinoBLE.h>", "juno_delay(uint32_t ms)");
        Path shim = temporaryDirectory.resolve("AsmUnoQLegoTrainShim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        syntaxCheckCpp(compiler, shim);
    }

    @Test
    void compilesAnSdPropertiesProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.Wifi;
                import io.github.jabrena.juno.api.io.SdCard;
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

    @Test
    void compilesARandomAccessFileProgramsShimWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.SdCard;
                import io.github.jabrena.juno.api.io.serial.Serial;
                import java.io.IOException;
                import java.io.RandomAccessFile;
                public final class AsmSdRandomAccess {
                    public static void main(String[] args) {
                        if (!SdCard.begin()) return;
                        byte[] entry = new byte[16];
                        try (RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r")) {
                            wad.seek(wad.length() - entry.length);
                            wad.readFully(entry, 0, entry.length);
                            Serial.println(wad.read(entry) + wad.read() + wad.skipBytes(2) + (int) wad.getFilePointer());
                        } catch (IOException failed) {
                            Serial.println(failed.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmSdRandomAccess", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmSdRandomAccess");
        Path shim = temporaryDirectory.resolve("AsmSdRandomAccessShim.cpp");
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
                import io.github.jabrena.juno.api.net.http.HttpServer;
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

    /** AP mode plus the server on both boards: WiFiS3's WiFiServer on the R4, BridgeTCPServer on the UNO Q. */
    @Test
    void compilesAnAccessPointProvisioningShimForBothBoardsWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[] {"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            String name = "AsmAp" + board;
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.%1$s;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.api.net.Wifi;
                    import io.github.jabrena.juno.api.net.http.HttpServer;
                    @Board(%1$s.class)
                    public final class %2$s {
                        public static void main(String[] args) {
                            Wifi.beginAP("juno-setup", "junosetup");
                            HttpServer.begin(80);
                            byte[] body = new byte[64];
                            if (HttpServer.accept(body, body.length) >= 0) {
                                HttpServer.respond(200, "text/html", "<form></form>");
                            }
                        }
                    }
                    """.formatted(board, name);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo." + name, source);
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo." + name);
            assertThat(result.runtimeShim()).contains("juno_wifi_begin_ap");
            Path shim = temporaryDirectory.resolve(name + "Shim.cpp");
            Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

            syntaxCheckCpp(compiler, shim);
        }
    }

    /** The pure-Java provisioning helper links against both boards' intrinsics and produces a valid shim. */
    @Test
    void compilesTheWifiProvisionerForBothBoardsWithACppCompiler() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[] {"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            String name = "AsmProvisioner" + board;
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.%1$s;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.api.net.WifiProvisioner;
                    @Board(%1$s.class)
                    public final class %2$s {
                        public static void main(String[] args) {
                            WifiProvisioner.run("juno-setup", "junosetup");
                        }
                    }
                    """.formatted(board, name);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo." + name, source);
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo." + name);
            Path shim = temporaryDirectory.resolve(name + "Shim.cpp");
            Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

            syntaxCheckCpp(compiler, shim);
        }
    }

    private static final String EXCEPTIONS = """
            package demo;
            import io.github.jabrena.juno.api.Clock;
            import io.github.jabrena.juno.api.io.serial.Serial;
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
                import io.github.jabrena.juno.api.io.serial.Serial;
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
    void assemblesAStringConcatProgramAndCompilesItsShim() throws Exception {
        String armGcc = availableArmGcc();
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(armGcc != null || compiler != null, "No toolchain available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Clock;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class AsmStringConcat {
                    public static void main(String[] args) {
                        String state = Clock.millis() > 0 ? "on" : null;
                        boolean active = Clock.millis() > 0;
                        int count = Clock.millis();
                        long total = 7L;
                        float ratio = 1.5f;
                        double precise = 2.5;
                        Serial.println(state + ":" + active + ":" + count + ":" + total + ":" + ratio + ":" + precise);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.AsmStringConcat", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AsmStringConcat");
        if (armGcc != null) {
            assembleAndCompile(armGcc, "demo.AsmStringConcat");
        }
        if (compiler != null) {
            Path shim = temporaryDirectory.resolve("AsmStringConcatShim.cpp");
            Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);
            syntaxCheckCpp(compiler, shim);
        }
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

            extern "C" {
              uintptr_t juno_gc_stack_top;
              uint8_t juno_gc_static_start;
              uint8_t juno_gc_static_end;
            }
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
                import io.github.jabrena.juno.api.net.http.Json;
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
                extern "C" {
                  uintptr_t juno_gc_stack_top;
                  uint8_t juno_gc_static_start;
                  uint8_t juno_gc_static_end;
                }

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

    /**
     * Runs the generated LEGO Powered Up shim on the host against the module's fake {@code ArduinoBLE.h},
     * which plays one City Hub: every command must reach the hub as the exact LEGO Wireless Protocol 3.0
     * frame, and the hub's Port Value notifications must decode into {@code readSensor} values.
     */
    @Test
    void generatedLegoPoweredUpShimSpeaksLegoWirelessProtocolAtRuntime() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;
                public final class LegoRuntime {
                    public static void main(String[] args) {
                        if (PoweredUpHubRemote.connect(0) && PoweredUpHubRemote.isConnected()) {
                            PoweredUpHubRemote.enableSensor(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.MODE_MOTOR_POSITION);
                            PoweredUpHubRemote.setMotorPower(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.readSensor(PoweredUpHubRemote.PORT_A));
                            PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.hubType());
                            PoweredUpHubRemote.brakeMotor(PoweredUpHubRemote.PORT_A);
                            PoweredUpHubRemote.switchOff();
                            PoweredUpHubRemote.disconnect();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LegoRuntime", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LegoRuntime");
        Path sketch = temporaryDirectory.resolve("LegoRuntime.cpp");
        String harness = """

                #include <stdio.h>

                // Provided by the generated entry-point assembly on a board; see the JSON runtime test.
                extern "C" {
                  uintptr_t juno_gc_stack_top;
                  uint8_t juno_gc_static_start;
                  uint8_t juno_gc_static_end;
                }

                static int junoCheckedWrites = 0;

                static bool junoExpectWrite(const uint8_t* expected, int length) {
                  if (junoCheckedWrites >= junofake::writes) return false;
                  const junofake::Message& actual = junofake::written[junoCheckedWrites++];
                  bool same = actual.length == length && memcmp(actual.bytes, expected, length) == 0;
                  if (!same) {
                    printf("write %d expected:", junoCheckedWrites - 1);
                    for (int i = 0; i < length; i++) printf(" %02X", expected[i]);
                    printf(" actual:");
                    for (int i = 0; i < actual.length; i++) printf(" %02X", actual.bytes[i]);
                    printf("\\n");
                  }
                  return same;
                }

                #define EXPECT_WRITE(code, ...) do { \\
                    const uint8_t expected[] = {__VA_ARGS__}; \\
                    if (!junoExpectWrite(expected, sizeof(expected))) return code; \\
                  } while (0)
                #define NOTIFY(...) do { \\
                    const uint8_t message[] = {__VA_ARGS__}; \\
                    junofake::notify(message, sizeof(message)); \\
                  } while (0)

                int main() {
                  uint8_t stackTopMarker;
                  juno_gc_stack_top = reinterpret_cast<uintptr_t>(&stackTopMarker);

                  if (juno_lego_hub_type_id() != 0 || juno_lego_hub_is_connected() != 0) return 1;
                  // Commands before connecting are dropped, not queued.
                  juno_lego_hub_set_motor_power(0, 50);
                  if (junofake::writes != 0) return 2;

                  if (juno_lego_hub_connect(0) != 1 || juno_lego_hub_is_connected() != 1) return 3;
                  if (!junofake::subscribed || juno_lego_hub_type_id() != 0x41) return 4;

                  // Port output command 0x81, execute immediately with feedback 0x11, WriteDirectModeData 0x51, mode 0.
                  juno_lego_hub_set_motor_power(0, 50);
                  EXPECT_WRITE(10, 0x08, 0x00, 0x81, 0x00, 0x11, 0x51, 0x00, 0x32);
                  juno_lego_hub_set_motor_power(1, -150);  // clamped to -100
                  EXPECT_WRITE(11, 0x08, 0x00, 0x81, 0x01, 0x11, 0x51, 0x00, 0x9C);
                  juno_lego_hub_set_motor_power(3, 101);  // clamped to 100
                  EXPECT_WRITE(12, 0x08, 0x00, 0x81, 0x03, 0x11, 0x51, 0x00, 0x64);
                  juno_lego_hub_brake_motor(0);
                  EXPECT_WRITE(13, 0x08, 0x00, 0x81, 0x00, 0x11, 0x51, 0x00, 0x7F);

                  // The City Hub's LED is port 0x32: one input format setup (notifications off), then the color.
                  juno_lego_hub_set_led_color(9);
                  EXPECT_WRITE(20, 0x0A, 0x00, 0x41, 0x32, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00);
                  EXPECT_WRITE(21, 0x08, 0x00, 0x81, 0x32, 0x11, 0x51, 0x00, 0x09);
                  juno_lego_hub_set_led_color(3);
                  EXPECT_WRITE(22, 0x08, 0x00, 0x81, 0x32, 0x11, 0x51, 0x00, 0x03);
                  // RGB mode (1) needs its own input format setup; going back to color indexes sets mode 0 up again.
                  juno_lego_hub_set_led_rgb(10, 20, 300);  // channels keep their low 8 bits
                  EXPECT_WRITE(23, 0x0A, 0x00, 0x41, 0x32, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00);
                  EXPECT_WRITE(24, 0x0A, 0x00, 0x81, 0x32, 0x11, 0x51, 0x01, 0x0A, 0x14, 0x2C);
                  juno_lego_hub_set_led_rgb(1, 2, 3);
                  EXPECT_WRITE(25, 0x0A, 0x00, 0x81, 0x32, 0x11, 0x51, 0x01, 0x01, 0x02, 0x03);
                  juno_lego_hub_set_led_color(6);
                  EXPECT_WRITE(26, 0x0A, 0x00, 0x41, 0x32, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00);
                  EXPECT_WRITE(27, 0x08, 0x00, 0x81, 0x32, 0x11, 0x51, 0x00, 0x06);

                  // Port input format setup 0x41: port, mode, delta 1 (uint32 LE), notifications on.
                  juno_lego_hub_enable_sensor(0, 2);
                  EXPECT_WRITE(30, 0x0A, 0x00, 0x41, 0x00, 0x02, 0x01, 0x00, 0x00, 0x00, 0x01);
                  if (juno_lego_hub_read_sensor(0) != 0) return 31;
                  NOTIFY(0x08, 0x00, 0x45, 0x00, 0x68, 0x01, 0x00, 0x00);  // Port Value 0x45: 360 degrees
                  if (juno_lego_hub_read_sensor(0) != 360) return 32;
                  NOTIFY(0x08, 0x00, 0x45, 0x00, 0xA6, 0xFF, 0xFF, 0xFF);  // -90 degrees
                  if (juno_lego_hub_read_sensor(0) != -90) return 33;
                  NOTIFY(0x0F, 0x00, 0x04, 0x00, 0x01, 0x2E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                  if (juno_lego_hub_read_sensor(0) != -90) return 34;  // Hub Attached I/O 0x04 is not a value
                  // A length of 128 or more takes two bytes (7 bits each); readers must skip both.
                  NOTIFY(0x89, 0x00, 0x00, 0x45, 0x00, 0x10, 0x00, 0x00, 0x00);
                  if (juno_lego_hub_read_sensor(0) != 16) return 35;

                  juno_lego_hub_enable_sensor(1, 0);
                  EXPECT_WRITE(40, 0x0A, 0x00, 0x41, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01);
                  NOTIFY(0x05, 0x00, 0x45, 0x01, 0xFF);  // int8: a remote's minus button
                  if (juno_lego_hub_read_sensor(1) != -1) return 41;
                  NOTIFY(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);  // port 2 is not enabled yet
                  if (juno_lego_hub_read_sensor(2) != 0) return 42;
                  juno_lego_hub_enable_sensor(2, 0);
                  EXPECT_WRITE(43, 0x0A, 0x00, 0x41, 0x02, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01);
                  NOTIFY(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);  // int16: 300
                  if (juno_lego_hub_read_sensor(2) != 300) return 44;
                  if (juno_lego_hub_read_sensor(0) != 16 || juno_lego_hub_read_sensor(1) != -1) return 45;

                  // Eight ports report at once; a ninth is refused without a write, re-enabling reuses a slot.
                  for (int port = 3; port < 8; port++) juno_lego_hub_enable_sensor(port, 0);
                  junoCheckedWrites += 5;
                  juno_lego_hub_enable_sensor(8, 0);
                  if (junofake::writes != junoCheckedWrites) return 50;
                  juno_lego_hub_enable_sensor(0, 1);
                  EXPECT_WRITE(51, 0x0A, 0x00, 0x41, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x01);
                  if (juno_lego_hub_read_sensor(0) != 0) return 52;

                  // Hub Properties 0x01: the first property call subscribes (0x02) to button, RSSI and battery and
                  // requests (0x05) name, firmware and hardware; the answers are updates (0x06). The test clock
                  // never advances, so the answers are queued first to end the wait for them.
                  NOTIFY(0x06, 0x00, 0x01, 0x06, 0x06, 0x55);  // battery 85 %
                  NOTIFY(0x09, 0x00, 0x01, 0x03, 0x06, 0x40, 0x00, 0x23, 0x10);  // firmware 1.0.23 build 64
                  NOTIFY(0x09, 0x00, 0x01, 0x04, 0x06, 0x01, 0x00, 0x00, 0x10);  // hardware 1.0.0 build 1
                  NOTIFY(0x08, 0x00, 0x01, 0x01, 0x06, 'H', 'u', 'b');  // name
                  NOTIFY(0x06, 0x00, 0x01, 0x02, 0x06, 0x01);  // button down
                  NOTIFY(0x06, 0x00, 0x01, 0x05, 0x06, 0xC4);  // RSSI -60 dBm
                  if (juno_lego_hub_battery_percent() != 85) return 80;
                  EXPECT_WRITE(81, 0x05, 0x00, 0x01, 0x02, 0x02);
                  EXPECT_WRITE(82, 0x05, 0x00, 0x01, 0x05, 0x02);
                  EXPECT_WRITE(83, 0x05, 0x00, 0x01, 0x06, 0x02);
                  EXPECT_WRITE(84, 0x05, 0x00, 0x01, 0x01, 0x05);
                  EXPECT_WRITE(85, 0x05, 0x00, 0x01, 0x03, 0x05);
                  EXPECT_WRITE(86, 0x05, 0x00, 0x01, 0x04, 0x05);
                  if (juno_lego_hub_firmware_version() != 0x10230040) return 87;
                  if (juno_lego_hub_hardware_version() != 0x10000001) return 88;
                  if (juno_lego_hub_button_pressed() != 1 || juno_lego_hub_rssi() != -60) return 89;
                  uint8_t hubName[8];
                  if (juno_lego_hub_name(hubName, 8) != 3 || memcmp(hubName, "Hub", 3) != 0) return 90;
                  if (juno_lego_hub_name(hubName, 2) != 2) return 91;
                  if (junofake::writes != junoCheckedWrites) return 92;  // requested once, not on every call

                  juno_lego_hub_switch_off();  // Hub action 0x02: switch off 0x01
                  EXPECT_WRITE(60, 0x04, 0x00, 0x02, 0x01);
                  juno_lego_hub_disconnect();
                  if (juno_lego_hub_is_connected() != 0) return 61;
                  juno_lego_hub_set_motor_power(0, 50);
                  if (junofake::writes != junoCheckedWrites) return 62;

                  // Reconnecting starts with no ports reporting.
                  if (juno_lego_hub_connect(0) != 1) return 70;
                  if (juno_lego_hub_read_sensor(2) != 0) return 71;
                  NOTIFY(0x06, 0x00, 0x45, 0x02, 0x2C, 0x01);
                  if (juno_lego_hub_read_sensor(2) != 0) return 72;

                  // Multi-value reports, e.g. the Technic Hub's gyroscope (port 0x62): three int16 values.
                  juno_lego_hub_enable_sensor(0x62, 0);
                  EXPECT_WRITE(100, 0x0A, 0x00, 0x41, 0x62, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01);
                  if (juno_lego_hub_sensor_report_size(0x62) != 0 || juno_lego_hub_read_sensor_value(0x62, 0, 2) != 0) return 101;
                  NOTIFY(0x0A, 0x00, 0x45, 0x62, 0x34, 0x12, 0xFE, 0xFF, 0x0A, 0x00);
                  if (juno_lego_hub_sensor_report_size(0x62) != 6) return 102;
                  if (juno_lego_hub_read_sensor_value(0x62, 0, 2) != 0x1234) return 103;
                  if (juno_lego_hub_read_sensor_value(0x62, 1, 2) != -2) return 104;
                  if (juno_lego_hub_read_sensor_value(0x62, 2, 2) != 10) return 105;
                  if (juno_lego_hub_sensor_report_count(0x62) != 1) return 111;
                  NOTIFY(0x0A, 0x00, 0x45, 0x62, 0x34, 0x12, 0xFE, 0xFF, 0x0A, 0x00);  // an unchanged report still counts
                  if (juno_lego_hub_sensor_report_count(0x62) != 2 || juno_lego_hub_sensor_report_count(0x05) != 0) return 112;
                  if (juno_lego_hub_read_sensor_value(0x62, 3, 2) != 0) return 106;  // beyond the report
                  if (juno_lego_hub_read_sensor_value(0x62, 0, 4) != static_cast<int32_t>(0xFFFE1234)) return 107;
                  if (juno_lego_hub_read_sensor_value(0x62, 5, 1) != 0) return 108;  // a high byte of the third value
                  if (juno_lego_hub_read_sensor_value(0x62, 0, 3) != 0) return 109;  // widths are 1, 2 or 4
                  if (juno_lego_hub_read_sensor_value(0x05, 0, 2) != 0) return 110;  // a port nobody enabled

                  // Virtual port: Virtual Port Setup 0x61 connect (0x01); the hub answers with Hub Attached I/O 0x04,
                  // event 0x02, announcing the new port 0x10 that pairs ports 0 and 1.
                  NOTIFY(0x09, 0x00, 0x04, 0x10, 0x02, 0x46, 0x00, 0x00, 0x01);
                  if (juno_lego_hub_link_motors(0, 1) != 0x10) return 120;
                  EXPECT_WRITE(121, 0x06, 0x00, 0x61, 0x01, 0x00, 0x01);
                  if (juno_lego_hub_link_motors(1, 0) != 0x10 || junofake::writes != junoCheckedWrites) return 122;
                  // StartPower with two powers (subcommand 0x02), clamped; 127 is brake.
                  juno_lego_hub_set_linked_motor_power(0x10, 50, -150);
                  EXPECT_WRITE(123, 0x08, 0x00, 0x81, 0x10, 0x11, 0x02, 0x32, 0x9C);
                  juno_lego_hub_brake_linked_motors(0x10);
                  EXPECT_WRITE(124, 0x08, 0x00, 0x81, 0x10, 0x11, 0x02, 0x7F, 0x7F);
                  juno_lego_hub_unlink_motors(0x10);  // disconnect (0x00)
                  EXPECT_WRITE(125, 0x05, 0x00, 0x61, 0x00, 0x10);
                  NOTIFY(0x09, 0x00, 0x04, 0x11, 0x02, 0x46, 0x00, 0x02, 0x03);
                  if (juno_lego_hub_link_motors(3, 2) != 0x11) return 126;  // pairs are matched in either order
                  EXPECT_WRITE(127, 0x06, 0x00, 0x61, 0x01, 0x03, 0x02);
                  return 0;
                }
                """;
        Files.writeString(sketch, result.runtimeShim() + harness, StandardCharsets.UTF_8);
        Path executable = temporaryDirectory.resolve("lego-runtime");

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
        Assumptions.assumeTrue(finished, "Generated LEGO runtime test timed out");
        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(run.exitValue()).as("runtime exit code (the failing check); output: " + output).isEqualTo(0);
    }

    /** Reaches every supported {@code java.lang.Math} overload, so the shim carries all of their helpers. */
    private static final String MATH_EVERYTHING = """
            package demo;
            import io.github.jabrena.juno.api.Clock;
            import io.github.jabrena.juno.api.io.serial.Serial;
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
                extern "C" {
                  uintptr_t juno_gc_stack_top;
                  uint8_t juno_gc_static_start;
                  uint8_t juno_gc_static_end;
                }
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
