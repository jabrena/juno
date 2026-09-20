package io.github.jabrena.juno.backend;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CortexM4AsmBackendTest#lowersABlinkShapedProgramToLinkableCortexM4Assembly} builds the exact IR
 * {@code Blink} lowers to (see {@code juno-examples/src/main/java/Blink.java} and {@code juno inspect --ir
 * --main Blink}). That exact backend output has separately been assembled and linked against the real
 * {@code arduino:renesas_uno} toolchain from a tiny {@code extern "C"} wrapper sketch, with
 * {@code pinMode}/{@code digitalWrite}/{@code delay}/{@code yield} all resolving to the core's real
 * addresses (see {@link CortexM4AsmBackend}'s class doc) — this test only re-checks the generated text.
 */
class CortexM4AsmBackendTest {
    @Test
    void lowersABlinkShapedProgramToLinkableCortexM4Assembly() {
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

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertThat(assembly.contains(".global juno_Blink_asm")).isTrue();
        assertThat(assembly.contains("bl pinMode")).isTrue();
        assertThat(assembly.contains("bl digitalWrite")).isTrue();
        assertThat(assembly.contains("bl delay")).isTrue();
        // Loop backedge: yield is emitted right before the branch back to the loop block.
        assertThat(assembly.contains("bl yield\n    b .")).isTrue();
        assertThat(result.runtimeShim().contains("extern \"C\" void* juno_alloc")).isTrue();
        assertThat(result.runtimeShim()).contains(
                "if (juno_yield_active) return;",
                "juno_yield_active = true;\n  static_cast<void>(static_cast<bool>(Serial));\n  juno_yield_active = false;");
    }

    /**
     * Mirrors {@code LedMatrixHeart}'s IR exactly (see {@code juno inspect --ir --main
     * LedMatrixHeart}), including the full-32-bit frame word constants that first exposed the
     * {@code movw}/{@code movt} immediate-loading fix. This assembly + shim pairing has separately
     * been assembled, linked, uploaded to, and run on a real UNO R4 WiFi (see
     * {@link CortexM4AsmBackend}'s class doc).
     */
    @Test
    void lowersALedMatrixShapedProgramAndEmitsARuntimeShim() {
        MethodRef entryPoint = new MethodRef("LedMatrixHeart", "main", "([Ljava/lang/String;)V");
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
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1), Value.int32(2)), List.of()),
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
        IrMethod method = IrMethod.withInferredValues(entryPoint, 4, 9, List.of(), List.of(setupBlock, loopBlock));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertThat(assembly.contains("bl juno_led_matrix_begin")).isTrue();
        assertThat(assembly.contains("bl juno_led_matrix_load_frame")).isTrue();
        assertThat(assembly.contains("bl juno_led_matrix_clear")).isTrue();
        assertThat(assembly.contains("movt r0, #12676")).isTrue(); // high 16 bits of 0x3184A444
        assertThat(assembly.contains("bl delay")).isTrue();

        String shim = result.runtimeShim();
        assertThat(shim.contains("extern \"C\" void juno_led_matrix_begin()")).isTrue();
        assertThat(shim.contains("extern \"C\" void juno_led_matrix_load_frame(int32_t word0, int32_t word1, int32_t word2)")).isTrue();
        assertThat(shim.contains("extern \"C\" void juno_led_matrix_clear()")).isTrue();
        assertThat(shim.contains("ArduinoLEDMatrix")).isTrue();
    }

    /**
     * Mirrors {@code SerialCounter}'s IR exactly (see {@code juno inspect --ir --main
     * SerialCounter}): {@code Serial.begin}/{@code Serial.println} plus a {@code Binary} ADD.
     */
    @Test
    void lowersSerialIntrinsicsAndEmitsARuntimeShim() {
        MethodRef entryPoint = new MethodRef("SerialCounter", "main", "([Ljava/lang/String;)V");
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
        IrMethod method = IrMethod.withInferredValues(entryPoint, 4, 13, List.of(), List.of(setupBlock, loopBlock));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertThat(assembly.contains("bl juno_serial_begin")).isTrue();
        assertThat(assembly.contains("bl juno_serial_println")).isTrue();
        assertThat(assembly.contains("add r0, r0, r1")).isTrue();
        assertThat(assembly.contains("bl delay")).isTrue();

        String shim = result.runtimeShim();
        assertThat(shim.contains("extern \"C\" void juno_serial_begin(int32_t baud)")).isTrue();
        assertThat(shim.contains("extern \"C\" void juno_serial_println(int32_t value)")).isTrue();
    }

    /** A string literal gets its own {@code .rodata} symbol; the call site just loads its address. */
    @Test
    void lowersSerialStringLiteralIntrinsicsToARodataSymbol() {
        MethodRef entryPoint = new MethodRef("demo/Greeting", "main", "([Ljava/lang/String;)V");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINT_STRING,
                        Optional.empty(), List.of(), List.of("hello")),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINTLN_STRING,
                        Optional.empty(), List.of(), List.of("world"))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 1, 0, List.of(), List.of(block));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertThat(assembly.contains(".section .rodata")).isTrue();
        assertThat(assembly.contains("juno_str0:\n    .asciz \"hello\"")).isTrue();
        assertThat(assembly.contains("juno_str1:\n    .asciz \"world\"")).isTrue();
        assertThat(assembly.contains("ldr r0, =juno_str0")).isTrue();
        assertThat(assembly.contains("bl juno_serial_print_str")).isTrue();
        assertThat(assembly.contains("ldr r0, =juno_str1")).isTrue();
        assertThat(assembly.contains("bl juno_serial_println_str")).isTrue();

        String shim = result.runtimeShim();
        assertThat(shim.contains("extern \"C\" void juno_serial_print_str(const char* value)")).isTrue();
        assertThat(shim.contains("extern \"C\" void juno_serial_println_str(const char* value)")).isTrue();
    }

    /** WiFi credentials are two string literals; {@code status()} returns through r0 like any other call. */
    @Test
    void lowersWifiIntrinsicsToLiteralAddressesAndAShim() {
        MethodRef entryPoint = new MethodRef("demo/WifiConnect", "main", "([Ljava/lang/String;)V");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.WIFI_BEGIN,
                        Optional.empty(), List.of(), List.of("network", "password")),
                new IrInstruction.IntrinsicCall(Optional.of(Value.int32(0)), Intrinsic.WIFI_STATUS,
                        Optional.empty(), List.of(), List.of())),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 1, 1, List.of(), List.of(block));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertThat(assembly.contains("juno_str0:\n    .asciz \"network\"")).isTrue();
        assertThat(assembly.contains("juno_str1:\n    .asciz \"password\"")).isTrue();
        assertThat(assembly.contains("ldr r0, =juno_str0")).isTrue();
        assertThat(assembly.contains("ldr r1, =juno_str1")).isTrue();
        assertThat(assembly.contains("bl juno_wifi_begin")).isTrue();
        assertThat(assembly.contains("bl juno_wifi_status")).isTrue();

        String shim = result.runtimeShim();
        assertThat(shim.contains("#include <WiFiS3.h>")).isTrue();
        assertThat(shim.contains("extern \"C\" void juno_wifi_begin(const char* ssid, const char* password)")).isTrue();
        assertThat(shim.contains("extern \"C\" int32_t juno_wifi_status()")).isTrue();
    }

    /**
     * Mirrors {@code ArenaFeaturesPulse}'s shape (arena object with a field, a mutable static field
     * set in {@code <clinit>}, and an enum-style {@code switch}) — see {@code juno inspect --ir --main
     * ArenaFeaturesPulse}. This exact combination has separately been assembled, linked, and uploaded
     * to real UNO R4 WiFi hardware (see {@link CortexM4AsmBackend}'s class doc).
     */
    @Test
    void lowersArenaObjectsStaticFieldsAndSwitchDispatch() {
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

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend()
                .generate(new IrProgram(mainRef, List.of(clinit, main)));
        String assembly = result.assembly();

        assertThat(assembly.contains("    .bss")).isTrue();
        assertThat(assembly.contains("juno_static_demo_Arena_base_I:")).isTrue();
        assertThat(assembly.contains("bl juno_fn0")).isTrue(); // entry point calls <clinit> first
        assertThat(assembly.contains("bl juno_alloc")).isTrue(); // NewObject
        assertThat(assembly.contains("ldr r1, [r0, #0]")).isTrue(); // LoadField at the (only) field's offset 0
        assertThat(assembly.contains("str r1, [r0, #0]")).isTrue(); // StoreField
        assertThat(assembly.contains("bne .Lswitchnext")).isTrue(); // Switch dispatch
    }

    /**
     * Mirrors {@code RatonLoco}'s {@code Mouse.begin()}/{@code Mouse.move(x, y)} usage.
     * {@code Mouse} is an optional Arduino library (unlike the core-bundled LED matrix/Serial), so
     * the {@code #include <Mouse.h>} and the wrapper functions must only appear when a program
     * actually uses it — otherwise every other generated sketch would need that library installed
     * just to compile.
     */
    @Test
    void lowersMouseIntrinsicsOnlyWhenUsed() {
        MethodRef entryPoint = new MethodRef("RatonLoco", "main", "()V");
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MOUSE_BEGIN, Optional.empty(), List.of(), List.of()),
                new IrInstruction.Const(Value.int32(0), 50),
                new IrInstruction.Const(Value.int32(1), 0),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MOUSE_MOVE,
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1)), List.of())),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 2, List.of(), List.of(block));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(result.assembly().contains("bl juno_mouse_begin")).isTrue();
        assertThat(result.assembly().contains("bl juno_mouse_move")).isTrue();
        assertThat(result.runtimeShim().contains("#include <Mouse.h>")).isTrue();
        assertThat(result.runtimeShim().contains("extern \"C\" void juno_mouse_move(int32_t x, int32_t y)")).isTrue();

        // A program that never touches Mouse must not require that library to compile.
        MethodRef noArgsEntry = new MethodRef("RatonLoco", "main", "()V");
        CortexM4AsmBackend.Output withoutMouse = new CortexM4AsmBackend().generate(
                new IrProgram(noArgsEntry, List.of(IrMethod.withInferredValues(noArgsEntry, 0, 0, List.of(),
                        List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty())))))));
        assertThat(withoutMouse.runtimeShim().contains("Mouse")).isFalse();
    }

    @Test
    void emitsCompilerGeneratedEnumArrays() {
        MethodRef entryPoint = new MethodRef("demo/EnumMap", "main", "()V");
        IrBasicBlock block = new IrBasicBlock(0,
                List.of(
                        new IrInstruction.IntArrayConst(Value.int32(0), List.of(9600, 19200, 115200)),
                        new IrInstruction.Const(Value.int32(1), 2),
                        new IrInstruction.ArrayLoad(Value.int32(2), ArrayElementType.INT,
                                Value.int32(0), Value.int32(1))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        String assembly = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method))).assembly();

        assertThat(assembly.contains(".word 9600, 19200, 115200")).isTrue();
        assertThat(assembly.contains("ldr r0, =juno_int_array0")).isTrue();
        assertThat(assembly.contains("ldr r2, [r0, r1]")).isTrue();
    }

    @Test
    void lowersBranchesArithmeticAndMultiMethodCallsForAConditionalHelper() {
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

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend()
                .generate(new IrProgram(mainRef, List.of(main, classify)));
        String assembly = result.assembly();

        assertThat(assembly.contains("cmp r0, r1")).isTrue();
        assertThat(assembly.contains("blt .Lcmptrue")).isTrue();
        assertThat(assembly.contains("bl juno_fn1")).isTrue(); // main calling classify by its assigned label
        assertThat(assembly.contains(".global juno_Cond_asm")).isTrue(); // only the entry point is exported
    }

    @Test
    void lowersLongArithmeticThroughRuntimeShimHelpers() {
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

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(result.assembly().contains("bl juno_ladd")).isTrue();
        assertThat(result.runtimeShim().contains("extern \"C\" int64_t juno_ladd")).isTrue();
        // A program that never touches long must not pull the helper block in at all.
        MethodRef intOnlyEntry = new MethodRef("demo/IntOnly", "main", "()V");
        CortexM4AsmBackend.Output withoutLong = new CortexM4AsmBackend().generate(new IrProgram(intOnlyEntry,
                List.of(IrMethod.withInferredValues(intOnlyEntry, 0, 0, List.of(),
                        List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty())))))));
        assertThat(withoutLong.runtimeShim().contains("juno_ladd")).isFalse();
    }

    /**
     * Mirrors {@code WeatherClient}'s exact {@code (int) Json.getDouble(...)} pattern: a JSON double
     * field read, immediately truncated to int (see
     * {@code juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/weather/WeatherClient.java}).
     */
    @Test
    void lowersJsonGetDoubleThroughDoubleToIntLikeMadridWeather() {
        MethodRef entryPoint = new MethodRef("demo/WeatherAsm", "main", "()I");
        Value buffer = Value.int32(0);
        Value length = Value.int32(1);
        Value doubleResult = Value.float64(2);
        Value intResult = Value.int32(3);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(buffer, 0),
                new IrInstruction.Const(length, 10),
                new IrInstruction.IntrinsicCall(Optional.of(doubleResult), Intrinsic.JSON_GET_DOUBLE,
                        Optional.empty(), List.of(buffer, length), List.of("current.temperature_2m")),
                new IrInstruction.DoubleToInt(intResult, doubleResult)),
                new IrTerminator.Return(Optional.of(intResult)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 4, List.of(), List.of(block));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(result.assembly().contains("bl juno_json_get_double")).isTrue();
        assertThat(result.assembly().contains("bl juno_d2i")).isTrue();
        assertThat(result.runtimeShim().contains("extern \"C\" double juno_json_get_double")).isTrue();
        assertThat(result.runtimeShim().contains("#include <math.h>")).isTrue();
        assertThat(result.runtimeShim().contains("#include <string.h>")).isTrue();
    }

    @Test
    void lowersHttpsGetWithStackSpilledArgumentsAndATlsRuntimeShim() {
        // HttpsClient.get("api.open-meteo.com", 443, "/v1/forecast", body, 512, headers, 512, out) —
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

        CortexM4AsmBackend.Output result2 = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(result2.assembly().contains("bl juno_https_get")).isTrue();
        assertThat(result2.assembly().contains("sub sp, sp, #16")).isTrue(); // 8 words spills 4, rounded to 16
        assertThat(result2.assembly().contains("add sp, sp, #16")).isTrue();
        assertThat(result2.runtimeShim().contains("#include <WiFiSSLClient.h>")).isTrue();
        assertThat(result2.runtimeShim().contains("extern \"C\" int32_t juno_https_get")).isTrue();
        // HTTP (plain) must not be pulled in by an HTTPS-only program.
        assertThat(result2.runtimeShim().contains("juno_http_get")).isFalse();
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
    @Test
    void lowersStringBuilderConstructionAppendAndToStringThroughTheShim() {
        MethodRef entryPoint = new MethodRef("demo/Builder", "main", "([Ljava/lang/String;)V");
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
        IrMethod method = IrMethod.withInferredValues(entryPoint, 1, 6, List.of(), List.of(block));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertThat(assembly.contains("bl juno_string_builder_new")).isTrue();
        assertThat(assembly.contains("bl juno_string_builder_append_char")).isTrue();
        assertThat(assembly.contains("ldr r1, =juno_str0")).isTrue();
        assertThat(assembly.contains("bl juno_string_builder_append_string")).isTrue();
        assertThat(assembly.contains("bl juno_string_builder_to_string")).isTrue();

        String shim = result.runtimeShim();
        assertThat(shim.contains("extern \"C\" int32_t juno_string_builder_new(int32_t capacity)")).isTrue();
        assertThat(shim.contains("extern \"C\" int32_t juno_string_builder_append_char(int32_t handle, int32_t value)")).isTrue();
        assertThat(shim.contains("extern \"C\" int32_t juno_string_builder_append_string(int32_t handle, const char* text)")).isTrue();
        assertThat(shim.contains("extern \"C\" int32_t juno_string_builder_to_string(int32_t handle)")).isTrue();
        // toString() needs the shared runtime-string-slot pool even though this program never calls
        // String.valueOf/Json.getString itself.
        assertThat(shim.contains("juno_string_slots[JUNO_STRING_SLOT_COUNT][JUNO_STRING_SLOT_SIZE]")).isTrue();
    }

    @Test
    void lowersRuntimeStringOperationsThroughTheShim() {
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

        CortexM4AsmBackend.Output generated = new CortexM4AsmBackend()
                .generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(generated.assembly()).contains("bl juno_string_value_of_int");
        assertThat(generated.assembly()).contains("bl juno_string_length");
        assertThat(generated.assembly()).contains("bl juno_string_char_at");
        assertThat(generated.runtimeShim()).contains("extern \"C\" int32_t juno_string_value_of_int");
    }

    /** Same JSON-double read as {@code WeatherClient}, but formatted with its decimal part kept. */
    @Test
    void lowersStringValueOfDoubleThroughTheShimAsAWholeDoubleArgument() {
        MethodRef entryPoint = new MethodRef("demo/WeatherDecimalAsm", "main", "()I");
        Value buffer = Value.int32(0);
        Value length = Value.int32(1);
        Value doubleResult = Value.float64(2);
        Value text = Value.int32(3);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(buffer, 0),
                new IrInstruction.Const(length, 10),
                new IrInstruction.IntrinsicCall(Optional.of(doubleResult), Intrinsic.JSON_GET_DOUBLE,
                        Optional.empty(), List.of(buffer, length), List.of("current.temperature_2m")),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.STRING_VALUE_OF_DOUBLE,
                        Optional.empty(), List.of(doubleResult), List.of())),
                new IrTerminator.Return(Optional.of(text)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 4, List.of(), List.of(block));

        CortexM4AsmBackend.Output generated = new CortexM4AsmBackend()
                .generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(generated.assembly()).contains("bl juno_string_value_of_double");
        assertThat(generated.runtimeShim()).contains("extern \"C\" int32_t juno_string_value_of_double(double value)");
    }

    @Test
    void lowersJsonGetStringValueThroughTheShimAsARuntimeString() {
        MethodRef entryPoint = new MethodRef("demo/JsonStringValueAsm", "main", "()I");
        Value buffer = Value.int32(0);
        Value length = Value.int32(1);
        Value text = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(buffer, 0),
                new IrInstruction.Const(length, 10),
                new IrInstruction.IntrinsicCall(Optional.of(text), Intrinsic.JSON_GET_STRING_VALUE,
                        Optional.empty(), List.of(buffer, length), List.of("current.temperature_2m"))),
                new IrTerminator.Return(Optional.of(text)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        CortexM4AsmBackend.Output generated = new CortexM4AsmBackend()
                .generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(generated.assembly()).contains("bl juno_json_get_string_value");
        assertThat(generated.runtimeShim()).contains("extern \"C\" int32_t juno_json_get_string_value(");
        assertThat(generated.runtimeShim()).contains("JUNO_STRING_SLOT_SIZE = 32");
    }

    /** Mirrors {@link io.github.jabrena.juno.JunoCompilerTest}'s same-named regression test for the C++ backend. */
    @Test
    void omitsJsonGetStringValueHelperWhenOnlyOtherJsonIntrinsicsAreUsed() {
        MethodRef entryPoint = new MethodRef("demo/JsonIntOnlyAsm", "main", "()I");
        Value buffer = Value.int32(0);
        Value length = Value.int32(1);
        Value temperature = Value.int32(2);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(buffer, 0),
                new IrInstruction.Const(length, 10),
                new IrInstruction.IntrinsicCall(Optional.of(temperature), Intrinsic.JSON_GET_INT,
                        Optional.empty(), List.of(buffer, length), List.of("data.sensor.temp"))),
                new IrTerminator.Return(Optional.of(temperature)));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 3, List.of(), List.of(block));

        CortexM4AsmBackend.Output generated = new CortexM4AsmBackend()
                .generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(generated.assembly()).contains("bl juno_json_get_int");
        assertThat(generated.runtimeShim()).doesNotContain("juno_json_get_string_value");
        assertThat(generated.runtimeShim()).doesNotContain("JUNO_STRING_SLOT_SIZE");
    }
}
