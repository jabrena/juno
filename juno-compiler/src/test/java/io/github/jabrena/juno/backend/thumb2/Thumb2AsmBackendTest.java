package io.github.jabrena.juno.backend.thumb2;

import io.github.jabrena.juno.backend.GeneratedCode;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Thumb2AsmBackendTest#lowersABlinkShapedProgramToLinkableThumb2Assembly} builds the exact IR
 * {@code Blink} lowers to (see {@code juno-examples/src/main/java/Blink.java} and {@code juno inspect --ir
 * --main Blink}). That exact backend output has separately been assembled and linked against the real
 * {@code arduino:renesas_uno} toolchain from a tiny {@code extern "C"} wrapper sketch, with
 * {@code pinMode}/{@code digitalWrite}/{@code delay}/{@code yield} all resolving to the core's real
 * addresses (see {@link Thumb2AsmBackend}'s class doc) — this test only re-checks the generated text.
 */
@DisplayName("Thumb-2 assembly backend")
class Thumb2AsmBackendTest {
    @Test
    @DisplayName("Lowers a Blink-shaped program to linkable Thumb-2 assembly")
    void shouldLowerABlinkShapedProgramToLinkableThumb2Assembly() {
        // Given
        MethodRef entryPoint = new MethodRef("Blink", "main", "([Ljava/lang/String;)V");
        IrBasicBlock setupBlock = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(Value.int32(0), 13),
                new IrInstruction.StoreLocal(2, Value.int32(0)),
                new IrInstruction.IntrinsicCall(Optional.of(Value.int32(2)), Intrinsic.DIGITAL_OUTPUT_OF,
                        Optional.empty(), List.of(Value.int32(0)), List.of()),
                new IrInstruction.StoreLocal(2, Value.int32(2)),
                new IrInstruction.StoreLocal(1, Value.int32(2))),
                new IrTerminator.Jump(6));
        IrBasicBlock loopBlock = new IrBasicBlock(6, List.of(
                new IrInstruction.LoadLocal(Value.int32(4), 1),
                new IrInstruction.StoreLocal(2, Value.int32(4)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DIGITAL_OUTPUT_HIGH,
                        Optional.of(Value.int32(4)), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(6), 500),
                new IrInstruction.StoreLocal(2, Value.int32(6)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(6)), List.of()),
                new IrInstruction.LoadLocal(Value.int32(8), 1),
                new IrInstruction.StoreLocal(2, Value.int32(8)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DIGITAL_OUTPUT_LOW,
                        Optional.of(Value.int32(8)), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(10), 500),
                new IrInstruction.StoreLocal(2, Value.int32(10)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(10)), List.of())),
                new IrTerminator.Jump(6));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 3, 11, List.of(), List.of(setupBlock, loopBlock));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(result.entryPointSymbol()).isEqualTo("juno_Blink_asm");
    }

    @Test
    @DisplayName("Blink calls pinMode, digitalWrite, delay and yield with the entry point exported")
    void shouldCallTheCoreFunctionsAndExportTheEntryPointForBlink() {
        // Given
        MethodRef entryPoint = new MethodRef("Blink", "main", "([Ljava/lang/String;)V");
        IrMethod method = blinkMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains(".global juno_Blink_asm", "bl pinMode", "bl digitalWrite", "bl delay");
    }

    @Test
    @DisplayName("The loop backedge emits yield right before the branch back to the loop block")
    void shouldEmitYieldRightBeforeTheLoopBackedge() {
        // Given
        MethodRef entryPoint = new MethodRef("Blink", "main", "([Ljava/lang/String;)V");
        IrMethod method = blinkMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains("bl yield\n    b .");
    }

    @Test
    @DisplayName("The Blink runtime shim declares the allocator and guards the Serial yield hook")
    void shouldDeclareTheAllocatorAndGuardTheSerialYieldHookInBlinkShim() {
        // Given
        MethodRef entryPoint = new MethodRef("Blink", "main", "([Ljava/lang/String;)V");
        IrMethod method = blinkMethod(entryPoint);

        // When
        String shim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).runtimeShim();

        // Then
        assertThat(shim).contains("extern \"C\" void* juno_alloc").contains(
                "if (juno_yield_active) return;",
                "juno_yield_active = true;\n  static_cast<void>(static_cast<bool>(Serial));\n  juno_yield_active = false;");
    }

    /**
     * The conservative GC's stack scan (see {@code runtimeShim}'s {@code juno_gc_mark}) needs a sound
     * upper bound on the live stack, so the entry point alone captures {@code sp} into
     * {@code juno_gc_stack_top} before its own {@code push} touches it — a helper method's prologue
     * must not repeat this (it isn't where the program's stack starts, and doing it there would
     * overwrite the true bound with a shallower one).
     */
    @Test
    @DisplayName("Captures the stack top only in the entry-point prologue before its own push")
    void shouldCaptureTheStackTopOnlyInTheEntryPointPrologueBeforeItsOwnPush() {
        // Given
        MethodRef entryPoint = new MethodRef("GcStackTop", "main", "([Ljava/lang/String;)V");
        MethodRef helper = new MethodRef("GcStackTop", "helper", "()V");
        IrMethod entryMethod = IrMethod.withInferredValues(entryPoint, 1, 1, List.of(),
                List.of(new IrBasicBlock(0,
                        List.of(new IrInstruction.Call(Optional.empty(), helper, List.of())),
                        new IrTerminator.Return(Optional.empty()))));
        IrMethod helperMethod = IrMethod.withInferredValues(helper, 0, 0, List.of(),
                List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()))));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(entryMethod, helperMethod)));
        String assembly = result.assembly();

        // Then
        assertThat(assembly).contains("juno_GcStackTop_asm:\n" + gcStackTopCapture());
    }

    @Test
    @DisplayName("The stack-top capture appears exactly once: the helper's prologue must not repeat it")
    void shouldEmitTheStackTopCaptureExactlyOnce() {
        // Given
        MethodRef entryPoint = new MethodRef("GcStackTop", "main", "([Ljava/lang/String;)V");
        MethodRef helper = new MethodRef("GcStackTop", "helper", "()V");
        IrMethod entryMethod = IrMethod.withInferredValues(entryPoint, 1, 1, List.of(),
                List.of(new IrBasicBlock(0,
                        List.of(new IrInstruction.Call(Optional.empty(), helper, List.of())),
                        new IrTerminator.Return(Optional.empty()))));
        IrMethod helperMethod = IrMethod.withInferredValues(helper, 0, 0, List.of(),
                List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()))));
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(entryMethod, helperMethod))).assembly();

        // When & Then
        assertThat(countOccurrences(assembly, "ldr r0, =juno_gc_stack_top")).isEqualTo(1);
    }

    @Test
    @DisplayName("The helper's prologue does not repeat the stack-top capture")
    void shouldNotRepeatTheStackTopCaptureInTheHelperPrologue() {
        // Given
        MethodRef entryPoint = new MethodRef("GcStackTop", "main", "([Ljava/lang/String;)V");
        MethodRef helper = new MethodRef("GcStackTop", "helper", "()V");
        IrMethod entryMethod = IrMethod.withInferredValues(entryPoint, 1, 1, List.of(),
                List.of(new IrBasicBlock(0,
                        List.of(new IrInstruction.Call(Optional.empty(), helper, List.of())),
                        new IrTerminator.Return(Optional.empty()))));
        IrMethod helperMethod = IrMethod.withInferredValues(helper, 0, 0, List.of(),
                List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()))));
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(entryMethod, helperMethod))).assembly();

        // When & Then
        assertThat(assembly).doesNotContain("juno_fn1:\n" + gcStackTopCapture());
    }

    private static String gcStackTopCapture() {
        return """
                    ldr r0, =juno_gc_stack_top
                    mov r1, sp
                    str r1, [r0]
                    movw r2, #4096
                    sub r2, r1, r2
                    movs r3, #0
                .LjunoZeroStack:
                    cmp r2, r1
                    bhs .LjunoZeroStackDone
                    str r3, [r2]
                    add r2, r2, #4
                    b .LjunoZeroStack
                .LjunoZeroStackDone:
                    push {r4-r11, lr}
                """;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static IrMethod blinkMethod(MethodRef entryPoint) {
        IrBasicBlock setupBlock = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(Value.int32(0), 13),
                new IrInstruction.StoreLocal(2, Value.int32(0)),
                new IrInstruction.IntrinsicCall(Optional.of(Value.int32(2)), Intrinsic.DIGITAL_OUTPUT_OF,
                        Optional.empty(), List.of(Value.int32(0)), List.of()),
                new IrInstruction.StoreLocal(2, Value.int32(2)),
                new IrInstruction.StoreLocal(1, Value.int32(2))),
                new IrTerminator.Jump(6));
        IrBasicBlock loopBlock = new IrBasicBlock(6, List.of(
                new IrInstruction.LoadLocal(Value.int32(4), 1),
                new IrInstruction.StoreLocal(2, Value.int32(4)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DIGITAL_OUTPUT_HIGH,
                        Optional.of(Value.int32(4)), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(6), 500),
                new IrInstruction.StoreLocal(2, Value.int32(6)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(6)), List.of()),
                new IrInstruction.LoadLocal(Value.int32(8), 1),
                new IrInstruction.StoreLocal(2, Value.int32(8)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DIGITAL_OUTPUT_LOW,
                        Optional.of(Value.int32(8)), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(10), 500),
                new IrInstruction.StoreLocal(2, Value.int32(10)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(10)), List.of())),
                new IrTerminator.Jump(6));
        return IrMethod.withInferredValues(entryPoint, 3, 11, List.of(), List.of(setupBlock, loopBlock));
    }

    /**
     * Mirrors {@code LedMatrixHeart}'s IR exactly (see {@code juno inspect --ir --main
     * LedMatrixHeart}), including the full-32-bit frame word constants that first exposed the
     * {@code movw}/{@code movt} immediate-loading fix. This assembly + shim pairing has separately
     * been assembled, linked, uploaded to, and run on a real UNO R4 WiFi (see
     * {@link Thumb2AsmBackend}'s class doc).
     */
    private static IrMethod ledMatrixHeartMethod(MethodRef entryPoint) {
        IrBasicBlock setupBlock = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.LED_MATRIX_BEGIN,
                        Optional.empty(), List.of(), List.of())),
                new IrTerminator.Jump(3));
        IrBasicBlock loopBlock = new IrBasicBlock(3, List.of(
                new IrInstruction.Const(Value.int32(0), 0x3184A444),
                new IrInstruction.StoreLocal(1, Value.int32(0)),
                new IrInstruction.Const(Value.int32(1), 0x44042081),
                new IrInstruction.StoreLocal(2, Value.int32(1)),
                new IrInstruction.Const(Value.int32(2), 0x100A0040),
                new IrInstruction.StoreLocal(3, Value.int32(2)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.LED_MATRIX_LOAD_FRAME,
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1), Value.int32(2), Value.int32(2)), List.of()),
                new IrInstruction.Const(Value.int32(6), 500),
                new IrInstruction.StoreLocal(1, Value.int32(6)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(6)), List.of()),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.LED_MATRIX_CLEAR,
                        Optional.empty(), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(8), 500),
                new IrInstruction.StoreLocal(1, Value.int32(8)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(8)), List.of())),
                new IrTerminator.Jump(3));
        return IrMethod.withInferredValues(entryPoint, 4, 9, List.of(), List.of(setupBlock, loopBlock));
    }

    @Test
    @DisplayName("LED matrix program calls begin, load-frame and clear in the assembly")
    void shouldCallTheLedMatrixBeginLoadFrameAndClearInTheAssembly() {
        // Given
        MethodRef entryPoint = new MethodRef("LedMatrixHeart", "main", "([Ljava/lang/String;)V");
        IrMethod method = ledMatrixHeartMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains("bl juno_led_matrix_begin", "bl juno_led_matrix_load_frame",
                "bl juno_led_matrix_clear", "bl delay");
    }

    @Test
    @DisplayName("LED matrix program loads the full 32-bit frame word with movt")
    void shouldLoadTheFullThirtyTwoBitFrameWordWithMovt() {
        // Given
        MethodRef entryPoint = new MethodRef("LedMatrixHeart", "main", "([Ljava/lang/String;)V");
        IrMethod method = ledMatrixHeartMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains("movt r0, #12676"); // high 16 bits of 0x3184A444
    }

    @Test
    @DisplayName("The LED matrix shim declares the matrix functions and the ArduinoLEDMatrix class")
    void shouldDeclareTheLedMatrixFunctionsAndClassInTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("LedMatrixHeart", "main", "([Ljava/lang/String;)V");
        IrMethod method = ledMatrixHeartMethod(entryPoint);

        // When
        String shim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).runtimeShim();

        // Then
        assertThat(shim).contains(
                "extern \"C\" void juno_led_matrix_begin()",
                "extern \"C\" void juno_led_matrix_load_frame(int32_t word0, int32_t word1, int32_t word2, int32_t word3)",
                "extern \"C\" void juno_led_matrix_clear()",
                "ArduinoLEDMatrix");
    }

    /**
     * Mirrors {@code SerialCounter}'s IR exactly (see {@code juno inspect --ir --main
     * SerialCounter}): {@code Serial.begin}/{@code Serial.println} plus a {@code Binary} ADD.
     */
    private static IrMethod serialCounterMethod(MethodRef entryPoint) {
        IrBasicBlock setupBlock = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(Value.int32(0), 9600),
                new IrInstruction.StoreLocal(2, Value.int32(0)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_BEGIN,
                        Optional.empty(), List.of(Value.int32(0)), List.of()),
                new IrInstruction.Const(Value.int32(2), 0),
                new IrInstruction.StoreLocal(2, Value.int32(2)),
                new IrInstruction.StoreLocal(1, Value.int32(2))),
                new IrTerminator.Jump(8));
        IrBasicBlock loopBlock = new IrBasicBlock(8, List.of(
                new IrInstruction.LoadLocal(Value.int32(4), 1),
                new IrInstruction.StoreLocal(2, Value.int32(4)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINTLN,
                        Optional.empty(), List.of(Value.int32(4)), List.of()),
                new IrInstruction.LoadLocal(Value.int32(6), 1),
                new IrInstruction.StoreLocal(2, Value.int32(6)),
                new IrInstruction.Const(Value.int32(7), 1),
                new IrInstruction.StoreLocal(3, Value.int32(7)),
                new IrInstruction.Binary(Value.int32(10), BinaryOp.ADD, Value.int32(6), Value.int32(7)),
                new IrInstruction.StoreLocal(2, Value.int32(10)),
                new IrInstruction.StoreLocal(1, Value.int32(10)),
                new IrInstruction.Const(Value.int32(12), 1000),
                new IrInstruction.StoreLocal(2, Value.int32(12)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(12)), List.of())),
                new IrTerminator.Jump(8));
        return IrMethod.withInferredValues(entryPoint, 4, 13, List.of(), List.of(setupBlock, loopBlock));
    }

    @Test
    @DisplayName("Serial counter calls begin and println in the assembly")
    void shouldCallSerialBeginAndPrintlnInTheAssembly() {
        // Given
        MethodRef entryPoint = new MethodRef("SerialCounter", "main", "([Ljava/lang/String;)V");
        IrMethod method = serialCounterMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains("bl juno_serial_begin", "bl juno_serial_println", "bl delay");
    }

    @Test
    @DisplayName("The constant one is folded into the addition's immediate field")
    void shouldFoldTheConstantOneIntoTheAdditionImmediate() {
        // Given
        MethodRef entryPoint = new MethodRef("SerialCounter", "main", "([Ljava/lang/String;)V");
        IrMethod method = serialCounterMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        // The constant 1 is folded into the addition's immediate field rather than loaded into r1.
        assertThat(assembly).contains("add r0, r0, #1");
    }

    @Test
    @DisplayName("The serial shim declares begin and println")
    void shouldDeclareSerialBeginAndPrintlnInTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("SerialCounter", "main", "([Ljava/lang/String;)V");
        IrMethod method = serialCounterMethod(entryPoint);

        // When
        String shim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).runtimeShim();

        // Then
        assertThat(shim).contains("extern \"C\" void juno_serial_begin(int32_t baud)",
                "extern \"C\" void juno_serial_println(int32_t value)");
    }

    /** A string literal gets its own {@code .rodata} symbol; the call site just loads its address. */
    @Test
    @DisplayName("String literals get their own rodata symbols and the call site loads their address")
    void shouldGiveStringLiteralsRodataSymbolsAndLoadTheirAddress() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/Greeting", "main", "([Ljava/lang/String;)V");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINT_STRING,
                        Optional.empty(), List.of(), List.of("hello")),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINTLN_STRING,
                        Optional.empty(), List.of(), List.of("world"))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 1, 0, List.of(), List.of(block));

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains(".section .rodata", "juno_str0:\n    .asciz \"hello\"",
                "juno_str1:\n    .asciz \"world\"", "ldr r0, =juno_str0", "bl juno_serial_print_str",
                "ldr r0, =juno_str1", "bl juno_serial_println_str");
    }

    @Test
    @DisplayName("The string-literal shim declares the print and println string functions")
    void shouldDeclareTheStringPrintFunctionsInTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/Greeting", "main", "([Ljava/lang/String;)V");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINT_STRING,
                        Optional.empty(), List.of(), List.of("hello")),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINTLN_STRING,
                        Optional.empty(), List.of(), List.of("world"))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 1, 0, List.of(), List.of(block));

        // When
        String shim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).runtimeShim();

        // Then
        assertThat(shim).contains("extern \"C\" void juno_serial_print_str(const char* value)",
                "extern \"C\" void juno_serial_println_str(const char* value)");
    }

    /** WiFi accepts runtime string values; {@code status()} returns through r0 like any other call. */
    private static IrMethod wifiConnectMethod(MethodRef entryPoint) {
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.StringConst(Value.int32(0), "network"),
                new IrInstruction.StringConst(Value.int32(1), "password"),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.WIFI_BEGIN,
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1)), List.of()),
                new IrInstruction.IntrinsicCall(Optional.of(Value.int32(2)), Intrinsic.WIFI_STATUS,
                        Optional.empty(), List.of(), List.of())),
                new IrTerminator.Return(Optional.empty()));
        return IrMethod.withInferredValues(entryPoint, 1, 3, List.of(), List.of(block));
    }

    @Test
    @DisplayName("WiFi intrinsics use literal string addresses and the begin/status calls")
    void shouldUseLiteralStringAddressesAndTheBeginAndStatusCallsForWifi() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/WifiConnect", "main", "([Ljava/lang/String;)V");
        IrMethod method = wifiConnectMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains("juno_str0:\n    .asciz \"network\"", "juno_str1:\n    .asciz \"password\"",
                "ldr r0, [sp,", "ldr r1, [sp,", "bl juno_wifi_begin", "bl juno_wifi_status");
    }

    @Test
    @DisplayName("The WiFi shim declares WiFiS3 and the begin and status functions")
    void shouldDeclareWifiS3AndTheBeginAndStatusFunctionsInTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/WifiConnect", "main", "([Ljava/lang/String;)V");
        IrMethod method = wifiConnectMethod(entryPoint);

        // When
        String shim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).runtimeShim();

        // Then
        assertThat(shim).contains("#include <WiFiS3.h>",
                "extern \"C\" void juno_wifi_begin(const char* ssid, const char* password)",
                "extern \"C\" int32_t juno_wifi_status()");
    }

    /**
     * Mirrors {@code ArenaFeaturesPulse}'s shape (arena object with a field, a mutable static field
     * set in {@code <clinit>}, and an enum-style {@code switch}) — see {@code juno inspect --ir --main
     * ArenaFeaturesPulse}. This exact combination has separately been assembled, linked, and uploaded
     * to real UNO R4 WiFi hardware (see {@link Thumb2AsmBackend}'s class doc).
     */
    @Test
    @DisplayName("Lowers arena objects, static fields and switch dispatch")
    void shouldLowerArenaObjectsStaticFieldsAndSwitchDispatch() {
        // Given
        MethodRef mainRef = new MethodRef("demo/Arena", "main", "()I");
        MethodRef clinitRef = new MethodRef("demo/Arena", "<clinit>", "()V");
        FieldRef staticField = new FieldRef("demo/Arena", "base", "I");
        FieldRef counterField = new FieldRef("demo/Arena$Counter", "value", "I");

        IrBasicBlock clinitBlock = new IrBasicBlock(0,
                List.of(new IrInstruction.Const(Value.int32(0), 5),
                        new IrInstruction.StoreStatic(staticField, Value.int32(0))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod clinit = IrMethod.withInferredValues(clinitRef, 0, 1, List.of(), List.of(clinitBlock));

        IrBasicBlock entryBlock = new IrBasicBlock(0, List.of(
                new IrInstruction.NewObject(Value.int32(0), "demo/Arena$Counter"),
                new IrInstruction.LoadStatic(Value.int32(1), staticField),
                new IrInstruction.StoreField(counterField, Value.int32(0), Value.int32(1)),
                new IrInstruction.LoadField(Value.int32(2), counterField, Value.int32(0)),
                new IrInstruction.Const(Value.int32(3), 1)),
                new IrTerminator.Switch(Value.int32(3), List.of(0, 1), List.of(10, 14), 18));
        IrBasicBlock caseZero = new IrBasicBlock(10,
                List.of(new IrInstruction.Const(Value.int32(5), 10), new IrInstruction.StoreLocal(0, Value.int32(5))),
                new IrTerminator.Jump(22));
        IrBasicBlock caseOne = new IrBasicBlock(14,
                List.of(new IrInstruction.Const(Value.int32(6), 20), new IrInstruction.StoreLocal(0, Value.int32(6))),
                new IrTerminator.Jump(22));
        IrBasicBlock defaultCase = new IrBasicBlock(18,
                List.of(new IrInstruction.Const(Value.int32(7), -1), new IrInstruction.StoreLocal(0, Value.int32(7))),
                new IrTerminator.Jump(22));
        IrBasicBlock join = new IrBasicBlock(22, List.of(
                new IrInstruction.LoadLocal(Value.int32(9), 0),
                new IrInstruction.Binary(Value.int32(10), BinaryOp.ADD, Value.int32(2), Value.int32(9))),
                new IrTerminator.Return(Optional.of(Value.int32(10))));
        IrMethod main = IrMethod.withInferredValues(mainRef, 1, 11, List.of(),
                List.of(entryBlock, caseZero, caseOne, defaultCase, join));

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(mainRef, List.of(clinit, main))).assembly();

        // Then
        assertThat(assembly).contains(
                "    .bss",
                "juno_static_demo_Arena_base_I:",
                "bl juno_fn0", // entry point calls <clinit> first
                "bl juno_alloc", // NewObject
                "ldr r1, [r0, #0]", // LoadField at the (only) field's offset 0
                "str r1, [r0, #0]", // StoreField
                "bne .Lswitchnext"); // Switch dispatch
    }

    /**
     * Every reachable class's {@code <clinit>} runs at startup, not just one: {@code PacMan}'s
     * {@code MazePixels.wallColor} stayed 0 (walls drawn black on black) because only
     * {@code Interludes}'s initializer was called.
     */
    @Test
    @DisplayName("The entry point calls every static initializer in program order")
    void shouldCallEveryStaticInitializerInProgramOrder() {
        // Given
        MethodRef mainRef = new MethodRef("demo/Main", "main", "()V");
        MethodRef firstClinitRef = new MethodRef("demo/Main", "<clinit>", "()V");
        MethodRef secondClinitRef = new MethodRef("demo/Colors", "<clinit>", "()V");
        FieldRef first = new FieldRef("demo/Main", "a", "I");
        FieldRef second = new FieldRef("demo/Colors", "wall", "I");

        IrMethod firstClinit = IrMethod.withInferredValues(firstClinitRef, 0, 1, List.of(),
                List.of(new IrBasicBlock(0, List.of(new IrInstruction.Const(Value.int32(0), 7),
                        new IrInstruction.StoreStatic(first, Value.int32(0))),
                        new IrTerminator.Return(Optional.empty()))));
        IrMethod secondClinit = IrMethod.withInferredValues(secondClinitRef, 0, 1, List.of(),
                List.of(new IrBasicBlock(0, List.of(new IrInstruction.Const(Value.int32(0), 0x211F),
                        new IrInstruction.StoreStatic(second, Value.int32(0))),
                        new IrTerminator.Return(Optional.empty()))));
        IrMethod main = IrMethod.withInferredValues(mainRef, 0, 0, List.of(),
                List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()))));

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(mainRef, List.of(firstClinit, secondClinit, main)))
                .assembly();
        int entry = assembly.indexOf("juno_Main_asm:");
        String prologue = assembly.substring(entry, assembly.indexOf(".Ljuno_Main_asmblock0:", entry));

        // Then
        assertThat(prologue).contains("bl juno_fn0").contains("bl juno_fn1");
        assertThat(prologue.indexOf("bl juno_fn0")).isLessThan(prologue.indexOf("bl juno_fn1"));
    }

    /**
     * Mirrors {@code RatonLoco}'s {@code mouse.begin()}/{@code mouse.move(x, y)} usage.
     * {@code Mouse} is an optional Arduino library (unlike the core-bundled LED matrix/Serial), so
     * the {@code #include <Mouse.h>} and the wrapper functions must only appear when a program
     * actually uses it — otherwise every other generated sketch would need that library installed
     * just to compile.
     */
    @Test
    @DisplayName("Mouse calls are emitted, and the Mouse library is included, only when used")
    void shouldEmitMouseCallsAndIncludeTheLibraryOnlyWhenUsed() {
        // Given
        MethodRef entryPoint = new MethodRef("RatonLoco", "main", "()V");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MOUSE_BEGIN, Optional.empty(), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(0), 50),
                new IrInstruction.Const(Value.int32(1), 0),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MOUSE_MOVE,
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1)), List.of())),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 2, List.of(), List.of(block));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(result.assembly()).contains("bl juno_mouse_begin", "bl juno_mouse_move");
        assertThat(result.runtimeShim()).contains("#include <Mouse.h>",
                "extern \"C\" void juno_mouse_move(int32_t x, int32_t y)");
    }

    @Test
    @DisplayName("A program that never touches Mouse does not mention it in the shim")
    void shouldNotMentionMouseInTheShimOfAProgramThatNeverUsesIt() {
        // Given
        // A program that never touches Mouse must not require that library to compile.
        MethodRef noArgsEntry = new MethodRef("RatonLoco", "main", "()V");

        // When
        GeneratedCode withoutMouse = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(
                new IrProgram(noArgsEntry, List.of(IrMethod.withInferredValues(noArgsEntry, 0, 0, List.of(),
                        List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty())))))));

        // Then
        assertThat(withoutMouse.runtimeShim()).doesNotContain("Mouse");
    }

    @Test
    @DisplayName("Emits compiler-generated enum arrays as word tables")
    void shouldEmitCompilerGeneratedEnumArraysAsWordTables() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/EnumMap", "main", "()V");
        IrBasicBlock block = new IrBasicBlock(0,
                List.of(
                        new IrInstruction.IntArrayConst(Value.int32(0), List.of(9600, 19200, 115200)),
                        new IrInstruction.Const(Value.int32(1), 2),
                        new IrInstruction.ArrayLoad(Value.int32(2), ArrayElementType.INT,
                                Value.int32(0), Value.int32(1))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains(".word 9600, 19200, 115200", "ldr r0, =juno_int_array0", "ldr r2, [r0, r1]");
    }

    @Test
    @DisplayName("A conditional helper lowers to a compare-and-branch with no materialized 0/1 result")
    void shouldLowerAConditionalHelperToACompareAndBranchWithNoMaterializedResult() {
        // Given
        // int classify(int x) { if (x < 0) return -1; return 1; }
        // int main() { return classify(3); }
        MethodRef classifyRef = new MethodRef("demo/Cond", "classify", "(I)I");
        MethodRef mainRef = new MethodRef("demo/Cond", "main", "()I");

        IrBasicBlock classifyEntry = new IrBasicBlock(0, List.of(
                new IrInstruction.LoadLocal(Value.int32(0), 0),
                new IrInstruction.Const(Value.int32(1), 0),
                new IrInstruction.Compare(Value.int32(2), Condition.LESS_THAN, Value.int32(0), Value.int32(1))),
                new IrTerminator.Branch(Value.int32(2), 4, 6));
        IrBasicBlock classifyNegative = new IrBasicBlock(4,
                List.of(new IrInstruction.Const(Value.int32(3), -1)),
                new IrTerminator.Return(Optional.of(Value.int32(3))));
        IrBasicBlock classifyPositive = new IrBasicBlock(6,
                List.of(new IrInstruction.Const(Value.int32(4), 1)),
                new IrTerminator.Return(Optional.of(Value.int32(4))));
        IrMethod classify = IrMethod.withInferredValues(classifyRef, 1, 5, List.of(),
                List.of(classifyEntry, classifyNegative, classifyPositive));

        IrBasicBlock mainEntry = new IrBasicBlock(0,
                List.of(new IrInstruction.Const(Value.int32(0), 3),
                        new IrInstruction.Call(Optional.of(Value.int32(1)), classifyRef, List.of(Value.int32(0)))),
                new IrTerminator.Return(Optional.of(Value.int32(1))));
        IrMethod main = IrMethod.withInferredValues(mainRef, 0, 2, List.of(), List.of(mainEntry));

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(mainRef, List.of(main, classify))).assembly();

        // Then
        // x < 0 feeds only the branch: one compare against an immediate, then the inverse condition skips to
        // the false block; the 0/1 result is never materialized.
        assertThat(assembly).contains("cmp r0, #0", "bge .Lbranchfalse")
                .doesNotContain(".Lcmptrue");
    }

    @Test
    @DisplayName("Main calls classify by its assigned label and only the entry point is exported")
    void shouldCallClassifyByItsLabelAndExportOnlyTheEntryPoint() {
        // Given
        MethodRef classifyRef = new MethodRef("demo/Cond", "classify", "(I)I");
        MethodRef mainRef = new MethodRef("demo/Cond", "main", "()I");
        IrBasicBlock classifyEntry = new IrBasicBlock(0, List.of(
                new IrInstruction.LoadLocal(Value.int32(0), 0),
                new IrInstruction.Const(Value.int32(1), 0),
                new IrInstruction.Compare(Value.int32(2), Condition.LESS_THAN, Value.int32(0), Value.int32(1))),
                new IrTerminator.Branch(Value.int32(2), 4, 6));
        IrBasicBlock classifyNegative = new IrBasicBlock(4,
                List.of(new IrInstruction.Const(Value.int32(3), -1)),
                new IrTerminator.Return(Optional.of(Value.int32(3))));
        IrBasicBlock classifyPositive = new IrBasicBlock(6,
                List.of(new IrInstruction.Const(Value.int32(4), 1)),
                new IrTerminator.Return(Optional.of(Value.int32(4))));
        IrMethod classify = IrMethod.withInferredValues(classifyRef, 1, 5, List.of(),
                List.of(classifyEntry, classifyNegative, classifyPositive));
        IrBasicBlock mainEntry = new IrBasicBlock(0,
                List.of(new IrInstruction.Const(Value.int32(0), 3),
                        new IrInstruction.Call(Optional.of(Value.int32(1)), classifyRef, List.of(Value.int32(0)))),
                new IrTerminator.Return(Optional.of(Value.int32(1))));
        IrMethod main = IrMethod.withInferredValues(mainRef, 0, 2, List.of(), List.of(mainEntry));

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(mainRef, List.of(main, classify))).assembly();

        // Then
        assertThat(assembly).contains("bl juno_fn1", ".global juno_Cond_asm");
    }

    @Test
    @DisplayName("Lowers long arithmetic through the runtime shim helpers")
    void shouldLowerLongArithmeticThroughTheRuntimeShimHelpers() {
        // Given
        // long a = 40L, b = 2L; return (int) (a + b);
        MethodRef entryPoint = new MethodRef("demo/LongMath", "main", "()I");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.LongConst(Value.int32(0), Value.int32(1), 40L),
                new IrInstruction.LongConst(Value.int32(2), Value.int32(3), 2L),
                new IrInstruction.LongBinary(Value.int32(4), Value.int32(5), BinaryOp.ADD,
                        Value.int32(0), Value.int32(1), Value.int32(2), Value.int32(3)),
                new IrInstruction.LongToInt(Value.int32(6), Value.int32(4), Value.int32(5))),
                new IrTerminator.Return(Optional.of(Value.int32(6))));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(result.assembly()).contains("bl juno_ladd");
    }

    @Test
    @DisplayName("The long-add shim helper is declared")
    void shouldDeclareTheLongAddShimHelper() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/LongMath", "main", "()I");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.LongConst(Value.int32(0), Value.int32(1), 40L),
                new IrInstruction.LongConst(Value.int32(2), Value.int32(3), 2L),
                new IrInstruction.LongBinary(Value.int32(4), Value.int32(5), BinaryOp.ADD,
                        Value.int32(0), Value.int32(1), Value.int32(2), Value.int32(3)),
                new IrInstruction.LongToInt(Value.int32(6), Value.int32(4), Value.int32(5))),
                new IrTerminator.Return(Optional.of(Value.int32(6))));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(result.runtimeShim()).contains("extern \"C\" int64_t juno_ladd");
    }

    @Test
    @DisplayName("A program that never touches long does not pull in the long helpers")
    void shouldNotPullInTheLongHelpersForAnIntOnlyProgram() {
        // Given
        MethodRef intOnlyEntry = new MethodRef("demo/IntOnly", "main", "()V");

        // When
        GeneratedCode withoutLong = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(intOnlyEntry,
                List.of(IrMethod.withInferredValues(intOnlyEntry, 0, 0, List.of(),
                        List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty())))))));

        // Then
        assertThat(withoutLong.runtimeShim()).doesNotContain("juno_ladd");
    }

    /**
     * Mirrors {@code WeatherClient}'s exact {@code (int) Json.getDouble(...)} pattern: a double immediately
     * truncated to int (see
     * {@code juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/weather/WeatherClient.java}).
     */
    @Test
    @DisplayName("Lowers a double truncated to int like the Madrid weather program")
    void shouldLowerDoubleToIntLikeMadridWeather() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/WeatherAsm", "main", "()I");
        Value doubleResult = Value.float64(0);
        Value intResult = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.DoubleConst(doubleResult, 21.5),
                new IrInstruction.DoubleToInt(intResult, doubleResult)),
                new IrTerminator.Return(Optional.of(intResult)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(result.assembly()).contains("bl juno_d2i");
    }

    @Test
    @DisplayName("The double-to-int shim includes math.h")
    void shouldIncludeMathHForTheDoubleToIntShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/WeatherAsm", "main", "()I");
        Value doubleResult = Value.float64(0);
        Value intResult = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.DoubleConst(doubleResult, 21.5),
                new IrInstruction.DoubleToInt(intResult, doubleResult)),
                new IrTerminator.Return(Optional.of(intResult)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        // When
        GeneratedCode result = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(result.runtimeShim()).contains("#include <math.h>");
    }

    @Test
    @DisplayName("HTTPS get spills its stack arguments and uses the TLS runtime shim")
    void shouldLowerHttpsGetWithStackSpilledArgumentsAndATlsRuntimeShim() {
        // Given
        // HttpClient.get("https://api.open-meteo.com/v1/forecast", body, 512, headers, 512, out) —
        // 8 argument words exceeds the 4 available AAPCS registers, exercising emitShimCall's
        // stack-spill path.
        MethodRef entryPoint = new MethodRef("demo/HttpsFetch", "main", "()I");
        Value port = Value.int32(0);
        Value bufferPtr = Value.int32(1);
        Value bufferLength = Value.int32(2);
        Value headersPtr = Value.int32(3);
        Value headersLength = Value.int32(4);
        Value outPtr = Value.int32(5);
        Value result = Value.int32(6);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 443),
                new IrInstruction.Const(bufferPtr, 0),
                new IrInstruction.Const(bufferLength, 512),
                new IrInstruction.Const(headersPtr, 0),
                new IrInstruction.Const(headersLength, 512),
                new IrInstruction.Const(outPtr, 0),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.HTTPS_GET, Optional.empty(),
                        List.of(port, bufferPtr, bufferLength, headersPtr, headersLength, outPtr),
                        List.of("api.open-meteo.com", "/v1/forecast"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.assembly()).contains("bl juno_https_get",
                "sub sp, sp, #16", // 8 words spills 4, rounded to 16
                "add sp, sp, #16");
    }

    @Test
    @DisplayName("HTTPS get declares the TLS client and no plain HTTP helper")
    void shouldDeclareTheTlsClientAndNoPlainHttpHelperForHttpsGet() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/HttpsFetch", "main", "()I");
        Value port = Value.int32(0);
        Value bufferPtr = Value.int32(1);
        Value bufferLength = Value.int32(2);
        Value headersPtr = Value.int32(3);
        Value headersLength = Value.int32(4);
        Value outPtr = Value.int32(5);
        Value result = Value.int32(6);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 443),
                new IrInstruction.Const(bufferPtr, 0),
                new IrInstruction.Const(bufferLength, 512),
                new IrInstruction.Const(headersPtr, 0),
                new IrInstruction.Const(headersLength, 512),
                new IrInstruction.Const(outPtr, 0),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.HTTPS_GET, Optional.empty(),
                        List.of(port, bufferPtr, bufferLength, headersPtr, headersLength, outPtr),
                        List.of("api.open-meteo.com", "/v1/forecast"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.runtimeShim()).contains("#include <WiFiSSLClient.h>", "extern \"C\" int32_t juno_https_get")
                .doesNotContain("juno_http_get"); // HTTP (plain) must not be pulled in by an HTTPS-only program.
    }

    @Test
    @DisplayName("SMTP send goes through the STARTTLS shim and not the POP3S client")
    void shouldLowerSmtpSendThroughAStarttlsShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/EmailAlert", "main", "()I");
        Value port = Value.int32(0);
        Value result = Value.int32(1);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 587),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.SMTP_SEND, Optional.empty(),
                        List.of(port),
                        List.of("mail.example.com", "alerts@example.com", "secret", "alerts@example.com",
                                "me@example.com", "Arduino alert", "Alarm activated"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 2, List.of(), List.of(block));

        // When
        GeneratedCode output = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(output.assembly()).contains("bl juno_smtp_send");
        assertThat(output.runtimeShim()).contains(
                "#include <WiFiS3.h>",
                "#include <ESP_SSLClient.h>",
                "extern \"C\" int32_t juno_smtp_send",
                "client.print(\"STARTTLS\\r\\n\")",
                "client.connectSSL()",
                "static int32_t juno_base64_encode(");
    }

    @Test
    @DisplayName("An SMTP-only program does not pull in the POP3S client")
    void shouldNotPullInThePop3sClientForAnSmtpOnlyProgram() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/EmailAlert", "main", "()I");
        Value port = Value.int32(0);
        Value result = Value.int32(1);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 587),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.SMTP_SEND, Optional.empty(),
                        List.of(port),
                        List.of("mail.example.com", "alerts@example.com", "secret", "alerts@example.com",
                                "me@example.com", "Arduino alert", "Alarm activated"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 2, List.of(), List.of(block));

        // When
        GeneratedCode output = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        // POP3S (native WiFiSSLClient) must not be pulled in by an SMTP-only program.
        assertThat(output.runtimeShim()).doesNotContain("#include <WiFiSSLClient.h>", "juno_pop3_message_count", "juno_pop3_read_latest");
    }

    @Test
    @DisplayName("POP3 read-latest spills its stack arguments and uses the POP3S runtime shim")
    void shouldLowerPop3ReadLatestWithStackSpilledArgumentsAndAPop3sRuntimeShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/InboxCount", "main", "()I");
        Value port = Value.int32(0);
        Value headersPtr = Value.int32(1);
        Value headersLength = Value.int32(2);
        Value bodyPtr = Value.int32(3);
        Value bodyLength = Value.int32(4);
        Value statusPtr = Value.int32(5);
        Value result = Value.int32(6);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 995),
                new IrInstruction.Const(headersPtr, 0),
                new IrInstruction.Const(headersLength, 256),
                new IrInstruction.Const(bodyPtr, 0),
                new IrInstruction.Const(bodyLength, 512),
                new IrInstruction.Const(statusPtr, 0),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.POP3_READ_LATEST, Optional.empty(),
                        List.of(port, headersPtr, headersLength, bodyPtr, bodyLength, statusPtr),
                        List.of("mail.example.com", "me@example.com", "secret"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode output = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(output.assembly()).contains("bl juno_pop3_read_latest", "sub sp, sp, #24"); // 9 words spills 5, rounded to 24
    }

    @Test
    @DisplayName("The POP3 read-latest shim declares the POP3S client functions")
    void shouldDeclareThePop3sClientFunctionsForPop3ReadLatest() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/InboxCount", "main", "()I");
        Value port = Value.int32(0);
        Value headersPtr = Value.int32(1);
        Value headersLength = Value.int32(2);
        Value bodyPtr = Value.int32(3);
        Value bodyLength = Value.int32(4);
        Value statusPtr = Value.int32(5);
        Value result = Value.int32(6);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 995),
                new IrInstruction.Const(headersPtr, 0),
                new IrInstruction.Const(headersLength, 256),
                new IrInstruction.Const(bodyPtr, 0),
                new IrInstruction.Const(bodyLength, 512),
                new IrInstruction.Const(statusPtr, 0),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.POP3_READ_LATEST, Optional.empty(),
                        List.of(port, headersPtr, headersLength, bodyPtr, bodyLength, statusPtr),
                        List.of("mail.example.com", "me@example.com", "secret"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode output = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(output.runtimeShim()).contains(
                "#include <WiFiS3.h>",
                "#include <WiFiSSLClient.h>",
                "extern \"C\" int32_t juno_pop3_read_latest",
                "extern \"C\" int32_t juno_pop3_message_count");
    }

    @Test
    @DisplayName("A POP3-only program does not pull in the SMTP client")
    void shouldNotPullInTheSmtpClientForAPop3OnlyProgram() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/InboxCount", "main", "()I");
        Value port = Value.int32(0);
        Value headersPtr = Value.int32(1);
        Value headersLength = Value.int32(2);
        Value bodyPtr = Value.int32(3);
        Value bodyLength = Value.int32(4);
        Value statusPtr = Value.int32(5);
        Value result = Value.int32(6);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 995),
                new IrInstruction.Const(headersPtr, 0),
                new IrInstruction.Const(headersLength, 256),
                new IrInstruction.Const(bodyPtr, 0),
                new IrInstruction.Const(bodyLength, 512),
                new IrInstruction.Const(statusPtr, 0),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.POP3_READ_LATEST, Optional.empty(),
                        List.of(port, headersPtr, headersLength, bodyPtr, bodyLength, statusPtr),
                        List.of("mail.example.com", "me@example.com", "secret"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 7, List.of(), List.of(block));

        // When
        GeneratedCode output = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        // SMTP/STARTTLS must not be pulled in by a POP3-only program.
        assertThat(output.runtimeShim()).doesNotContain("#include <ESP_SSLClient.h>", "juno_smtp_send");
    }

    @Test
    @DisplayName("POP3 read-subject calls the subject helper and declares the POP3S client")
    void shouldLowerPop3ReadSubject() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/InboxList", "main", "()I");
        Value port = Value.int32(0);
        Value messageNumber = Value.int32(1);
        Value subjectPtr = Value.int32(2);
        Value subjectLength = Value.int32(3);
        Value result = Value.int32(4);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(port, 995),
                new IrInstruction.Const(messageNumber, 1),
                new IrInstruction.Const(subjectPtr, 0),
                new IrInstruction.Const(subjectLength, 16),
                new IrInstruction.IntrinsicCall(Optional.of(result), Intrinsic.POP3_READ_SUBJECT, Optional.empty(),
                        List.of(port, messageNumber, subjectPtr, subjectLength),
                        List.of("mail.example.com", "me@example.com", "secret"))),
                new IrTerminator.Return(Optional.of(result)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 5, List.of(), List.of(block));

        // When
        GeneratedCode output = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(output.assembly()).contains("bl juno_pop3_read_subject");
        assertThat(output.runtimeShim()).contains(
                "#include <WiFiS3.h>",
                "#include <WiFiSSLClient.h>",
                "extern \"C\" int32_t juno_pop3_read_subject",
                "client.print(\"TOP \")")
                .doesNotContain("#include <ESP_SSLClient.h>", "juno_smtp_send");
    }

    /**
     * {@code new StringBuilder(8)} then {@code .append('1').append("!!").toString()}. Exercises
     * {@link io.github.jabrena.juno.lowering.BytecodeToIr}'s dedicated construction lowering (a
     * regular {@code new}+{@code invokespecial <init>} pair can't just be one more intrinsic here,
     * since the real JDK constructor is {@code void} — see that class's
     * {@code lowerStringBuilderConstruction} for why) only indirectly, by asserting the resulting
     * IR already carries a real {@code STRING_BUILDER_NEW} call rather than a discarded
     * placeholder; this test's own focus is that the asm backend lowers that IR correctly.
     */
    private static IrMethod stringBuilderMethod(MethodRef entryPoint) {
        Value capacity = Value.int32(0);
        Value builder = Value.int32(1);
        Value character = Value.int32(2);
        Value afterChar = Value.int32(3);
        Value afterString = Value.int32(4);
        Value text = Value.int32(5);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(capacity, 8),
                new IrInstruction.IntrinsicCall(Optional.of(builder), Intrinsic.STRING_BUILDER_NEW,
                        Optional.empty(), List.of(capacity), List.of()),
                new IrInstruction.Const(character, '1'),
                new IrInstruction.IntrinsicCall(Optional.of(afterChar), Intrinsic.STRING_BUILDER_APPEND_CHAR,
                        Optional.of(builder), List.of(character), List.of()),
                new IrInstruction.IntrinsicCall(Optional.of(afterString), Intrinsic.STRING_BUILDER_APPEND_STRING,
                        Optional.of(afterChar), List.of(), List.of("!!")),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_BUILDER_TO_STRING,
                        Optional.of(afterString), List.of(), List.of())),
                new IrTerminator.Return(Optional.empty()));
        return IrMethod.withInferredValues(entryPoint, 1, 6, List.of(), List.of(block));
    }

    @Test
    @DisplayName("String builder construction, append and toString call their assembly helpers")
    void shouldCallTheStringBuilderHelpersInTheAssembly() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/Builder", "main", "([Ljava/lang/String;)V");
        IrMethod method = stringBuilderMethod(entryPoint);

        // When
        String assembly = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).assembly();

        // Then
        assertThat(assembly).contains("bl juno_string_builder_new", "bl juno_string_builder_append_char",
                "ldr r1, =juno_str0", "bl juno_string_builder_append_string", "bl juno_string_builder_to_string");
    }

    @Test
    @DisplayName("The string builder shim declares the builder functions and the shared string slots")
    void shouldDeclareTheStringBuilderFunctionsAndSharedStringSlotsInTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/Builder", "main", "([Ljava/lang/String;)V");
        IrMethod method = stringBuilderMethod(entryPoint);

        // When
        String shim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method))).runtimeShim();

        // Then
        // toString() needs the shared runtime-string-slot pool even though this program never calls
        // String.valueOf/Json.getString itself.
        assertThat(shim).contains(
                "extern \"C\" int32_t juno_string_builder_new(int32_t capacity)",
                "extern \"C\" int32_t juno_string_builder_append_char(int32_t handle, int32_t value)",
                "extern \"C\" int32_t juno_string_builder_append_string(int32_t handle, const char* text)",
                "extern \"C\" int32_t juno_string_builder_to_string(int32_t handle)",
                "juno_string_slots[JUNO_STRING_SLOT_COUNT][JUNO_STRING_SLOT_SIZE]");
    }

    @Test
    @DisplayName("Runtime string operations call their assembly helpers")
    void shouldLowerRuntimeStringOperationsThroughTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/RuntimeText", "main", "()I");
        Value number = Value.int32(0);
        Value text = Value.int32(1);
        Value length = Value.int32(2);
        Value index = Value.int32(3);
        Value character = Value.int32(4);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(number, -12),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_VALUE_OF_INT,
                        Optional.empty(), List.of(number), List.of()),
                new IrInstruction.IntrinsicCall(Optional.of(length), Intrinsic.STRING_LENGTH,
                        Optional.of(text), List.of(), List.of()),
                new IrInstruction.Const(index, 0),
                new IrInstruction.IntrinsicCall(Optional.of(character), Intrinsic.STRING_CHAR_AT,
                        Optional.of(text), List.of(index), List.of())),
                new IrTerminator.Return(Optional.of(length)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 5, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.assembly()).contains("bl juno_string_value_of_int", "bl juno_string_length",
                "bl juno_string_char_at");
    }

    @Test
    @DisplayName("The runtime string shim declares the value-of-int function")
    void shouldDeclareTheStringValueOfIntFunctionInTheShim() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/RuntimeText", "main", "()I");
        Value number = Value.int32(0);
        Value text = Value.int32(1);
        Value length = Value.int32(2);
        Value index = Value.int32(3);
        Value character = Value.int32(4);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(number, -12),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_VALUE_OF_INT,
                        Optional.empty(), List.of(number), List.of()),
                new IrInstruction.IntrinsicCall(Optional.of(length), Intrinsic.STRING_LENGTH,
                        Optional.of(text), List.of(), List.of()),
                new IrInstruction.Const(index, 0),
                new IrInstruction.IntrinsicCall(Optional.of(character), Intrinsic.STRING_CHAR_AT,
                        Optional.of(text), List.of(index), List.of())),
                new IrTerminator.Return(Optional.of(length)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 5, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.runtimeShim()).contains("extern \"C\" int32_t juno_string_value_of_int");
    }

    /** A double like {@code WeatherClient}'s temperature, formatted with its decimal part kept. */
    @Test
    @DisplayName("String valueOf a double passes the whole double argument through the shim")
    void shouldLowerStringValueOfDoubleThroughTheShimAsAWholeDoubleArgument() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/WeatherDecimalAsm", "main", "()I");
        Value doubleResult = Value.float64(0);
        Value text = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.DoubleConst(doubleResult, 21.5),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_VALUE_OF_DOUBLE,
                        Optional.empty(), List.of(doubleResult), List.of())),
                new IrTerminator.Return(Optional.of(text)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 4, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.assembly()).contains("bl juno_string_value_of_double");
    }

    @Test
    @DisplayName("The string valueOf-double shim takes the double by value")
    void shouldDeclareTheStringValueOfDoubleShimTakingTheDoubleByValue() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/WeatherDecimalAsm", "main", "()I");
        Value doubleResult = Value.float64(0);
        Value text = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.DoubleConst(doubleResult, 21.5),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_VALUE_OF_DOUBLE,
                        Optional.empty(), List.of(doubleResult), List.of())),
                new IrTerminator.Return(Optional.of(text)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 4, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.runtimeShim()).contains("extern \"C\" JUNO_ASM_ABI int32_t juno_string_value_of_double(double value)");
    }

    @Test
    @DisplayName("String fromBytes calls the shim as a runtime string")
    void shouldLowerStringFromBytesThroughTheShimAsARuntimeString() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/JsonRawTextAsm", "main", "()I");
        Value buffer = Value.int32(0);
        Value start = Value.int32(1);
        Value end = Value.int32(2);
        Value text = Value.int32(3);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(buffer, 0),
                new IrInstruction.Const(start, 1),
                new IrInstruction.Const(end, 5),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_FROM_BYTES,
                        Optional.empty(), List.of(buffer, start, end), List.of())),
                new IrTerminator.Return(Optional.of(text)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 4, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.assembly()).contains("bl juno_string_from_bytes");
    }

    @Test
    @DisplayName("The string fromBytes shim declares the function and the slot size")
    void shouldDeclareTheStringFromBytesShimAndTheSlotSize() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/JsonRawTextAsm", "main", "()I");
        Value buffer = Value.int32(0);
        Value start = Value.int32(1);
        Value end = Value.int32(2);
        Value text = Value.int32(3);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(buffer, 0),
                new IrInstruction.Const(start, 1),
                new IrInstruction.Const(end, 5),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_FROM_BYTES,
                        Optional.empty(), List.of(buffer, start, end), List.of())),
                new IrTerminator.Return(Optional.of(text)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 4, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.runtimeShim()).contains("extern \"C\" int32_t juno_string_from_bytes(",
                "JUNO_STRING_SLOT_SIZE = 32");
    }

    /**
     * {@code analogRead}/{@code delayMicroseconds} resolve straight to the core's real functions, the
     * same way {@code digitalWrite}/{@code delay} already do — no Juno shim needed.
     */
    @Test
    @DisplayName("Analog read and delayMicroseconds resolve to the core's real functions")
    void shouldLowerAnalogReadAndDelayMicrosToTheCoresRealFunctions() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/AnalogProbe", "main", "()I");
        Value pin = Value.int32(0);
        Value reading = Value.int32(1);
        Value settleMicros = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(pin, 0),
                new IrInstruction.IntrinsicCall(Optional.of(reading), Intrinsic.GPIO_ANALOG_READ,
                        Optional.empty(), List.of(pin), List.of()),
                new IrInstruction.Const(settleMicros, 40),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MICROS,
                        Optional.empty(), List.of(settleMicros), List.of())),
                new IrTerminator.Return(Optional.of(reading)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.assembly()).contains("bl analogRead", "bl delayMicroseconds");
    }

    /** {@code Clock.millis}/{@code Clock.micros} resolve straight to the core's real functions, taking no arguments. */
    @Test
    @DisplayName("Clock millis and micros resolve to the core's real functions")
    void shouldLowerClockMillisAndMicrosToTheCoresRealFunctions() {
        // Given
        MethodRef entryPoint = new MethodRef("demo/ClockProbe", "main", "()I");
        Value millis = Value.int32(0);
        Value micros = Value.int32(1);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.of(millis), Intrinsic.CLOCK_MILLIS,
                        Optional.empty(), List.of(), List.of()),
                new IrInstruction.IntrinsicCall(Optional.of(micros), Intrinsic.CLOCK_MICROS,
                        Optional.empty(), List.of(), List.of())),
                new IrTerminator.Return(Optional.of(millis)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 2, List.of(), List.of(block));

        // When
        GeneratedCode generated = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI)
                .generate(new IrProgram(entryPoint, List.of(method)));

        // Then
        assertThat(generated.assembly()).contains("bl millis", "bl micros");
    }

    @Test
    @DisplayName("The arena size comes from the runtime config")
    void shouldSizeTheArenaFromTheRuntimeConfig() {
        // Given
        MethodRef mainRef = new MethodRef("Main", "main", "([Ljava/lang/String;)V");
        IrMethod main = IrMethod.withInferredValues(mainRef, 1, 0, List.of(),
                List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()))));
        IrProgram program = new IrProgram(mainRef, List.of(main));

        // When
        String defaultShim = new Thumb2AsmBackend(false, Board.UNO_R4_WIFI).generate(program).runtimeShim();
        String configuredShim = new Thumb2AsmBackend(false, io.github.jabrena.juno.board.Board.UNO_R4_WIFI,
                new io.github.jabrena.juno.RuntimeConfig(16384, 4, java.util.OptionalInt.empty()))
                .generate(program).runtimeShim();

        // Then
        assertThat(defaultShim).contains("juno_arena[8192]");
        assertThat(configuredShim).contains("juno_arena[16384]").doesNotContain("juno_arena[8192]");
    }
}
