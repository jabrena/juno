package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        Optional.empty(), List.of(Value.int32(0))),
                new IrInstruction.StoreLocal(2, Value.int32(2)),
                new IrInstruction.StoreLocal(1, Value.int32(2))),
                new IrTerminator.Jump(6));
        IrBasicBlock loopBlock = new IrBasicBlock(6, List.of(
                new IrInstruction.LoadLocal(Value.int32(4), 1),
                new IrInstruction.StoreLocal(2, Value.int32(4)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DIGITAL_OUTPUT_HIGH,
                        Optional.of(Value.int32(4)), List.of()),
                new IrInstruction.Const(Value.int32(6), 500),
                new IrInstruction.StoreLocal(2, Value.int32(6)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(6))),
                new IrInstruction.LoadLocal(Value.int32(8), 1),
                new IrInstruction.StoreLocal(2, Value.int32(8)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DIGITAL_OUTPUT_LOW,
                        Optional.of(Value.int32(8)), List.of()),
                new IrInstruction.Const(Value.int32(10), 500),
                new IrInstruction.StoreLocal(2, Value.int32(10)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(10)))),
                new IrTerminator.Jump(6));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 3, 11, List.of(), List.of(setupBlock, loopBlock));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertTrue(assembly.contains(".global juno_Blink_asm"));
        assertTrue(assembly.contains("bl pinMode"));
        assertTrue(assembly.contains("bl digitalWrite"));
        assertTrue(assembly.contains("bl delay"));
        // Loop backedge: yield is emitted right before the branch back to the loop block.
        assertTrue(assembly.contains("bl yield\n    b ."));
        assertTrue(result.runtimeShim().contains("extern \"C\" void* juno_alloc"));
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
                        Optional.empty(), List.of())),
                new IrTerminator.Jump(3));
        IrBasicBlock loopBlock = new IrBasicBlock(3, List.of(
                new IrInstruction.Const(Value.int32(0), 0x3184A444),
                new IrInstruction.StoreLocal(1, Value.int32(0)),
                new IrInstruction.Const(Value.int32(1), 0x44042081),
                new IrInstruction.StoreLocal(2, Value.int32(1)),
                new IrInstruction.Const(Value.int32(2), 0x100A0040),
                new IrInstruction.StoreLocal(3, Value.int32(2)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.LED_MATRIX_LOAD_FRAME,
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1), Value.int32(2))),
                new IrInstruction.Const(Value.int32(6), 500),
                new IrInstruction.StoreLocal(1, Value.int32(6)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(6))),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.LED_MATRIX_CLEAR,
                        Optional.empty(), List.of()),
                new IrInstruction.Const(Value.int32(8), 500),
                new IrInstruction.StoreLocal(1, Value.int32(8)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.DELAY_MILLIS,
                        Optional.empty(), List.of(Value.int32(8)))),
                new IrTerminator.Jump(3));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 4, 9, List.of(), List.of(setupBlock, loopBlock));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertTrue(assembly.contains("bl juno_led_matrix_begin"));
        assertTrue(assembly.contains("bl juno_led_matrix_load_frame"));
        assertTrue(assembly.contains("bl juno_led_matrix_clear"));
        assertTrue(assembly.contains("movt r0, #12676")); // high 16 bits of 0x3184A444
        assertTrue(assembly.contains("bl delay"));

        String shim = result.runtimeShim();
        assertTrue(shim.contains("extern \"C\" void juno_led_matrix_begin()"));
        assertTrue(shim.contains("extern \"C\" void juno_led_matrix_load_frame(int32_t word0, int32_t word1, int32_t word2)"));
        assertTrue(shim.contains("extern \"C\" void juno_led_matrix_clear()"));
        assertTrue(shim.contains("ArduinoLEDMatrix"));
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
                        Optional.empty(), List.of(Value.int32(0))),
                new IrInstruction.Const(Value.int32(2), 0),
                new IrInstruction.StoreLocal(2, Value.int32(2)),
                new IrInstruction.StoreLocal(1, Value.int32(2))),
                new IrTerminator.Jump(8));
        IrBasicBlock loopBlock = new IrBasicBlock(8, List.of(
                new IrInstruction.LoadLocal(Value.int32(4), 1),
                new IrInstruction.StoreLocal(2, Value.int32(4)),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.SERIAL_PRINTLN,
                        Optional.empty(), List.of(Value.int32(4))),
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
                        Optional.empty(), List.of(Value.int32(12)))),
                new IrTerminator.Jump(8));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 4, 13, List.of(), List.of(setupBlock, loopBlock));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));
        String assembly = result.assembly();

        assertTrue(assembly.contains("bl juno_serial_begin"));
        assertTrue(assembly.contains("bl juno_serial_println"));
        assertTrue(assembly.contains("add r0, r0, r1"));
        assertTrue(assembly.contains("bl delay"));

        String shim = result.runtimeShim();
        assertTrue(shim.contains("extern \"C\" void juno_serial_begin(int32_t baud)"));
        assertTrue(shim.contains("extern \"C\" void juno_serial_println(int32_t value)"));
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

        assertTrue(assembly.contains("    .bss"));
        assertTrue(assembly.contains("juno_static_demo_Arena_base_I:"));
        assertTrue(assembly.contains("bl juno_fn0")); // entry point calls <clinit> first
        assertTrue(assembly.contains("bl juno_alloc")); // NewObject
        assertTrue(assembly.contains("ldr r1, [r0, #0]")); // LoadField at the (only) field's offset 0
        assertTrue(assembly.contains("str r1, [r0, #0]")); // StoreField
        assertTrue(assembly.contains("bne .Lswitchnext")); // Switch dispatch
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
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MOUSE_BEGIN, Optional.empty(), List.of()),
                new IrInstruction.Const(Value.int32(0), 50),
                new IrInstruction.Const(Value.int32(1), 0),
                new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MOUSE_MOVE,
                        Optional.empty(), List.of(Value.int32(0), Value.int32(1)))),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 2, List.of(), List.of(block));

        CortexM4AsmBackend.Output result = new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertTrue(result.assembly().contains("bl juno_mouse_begin"));
        assertTrue(result.assembly().contains("bl juno_mouse_move"));
        assertTrue(result.runtimeShim().contains("#include <Mouse.h>"));
        assertTrue(result.runtimeShim().contains("extern \"C\" void juno_mouse_move(int32_t x, int32_t y)"));

        // A program that never touches Mouse must not require that library to compile.
        MethodRef noArgsEntry = new MethodRef("RatonLoco", "main", "()V");
        CortexM4AsmBackend.Output withoutMouse = new CortexM4AsmBackend().generate(
                new IrProgram(noArgsEntry, List.of(IrMethod.withInferredValues(noArgsEntry, 0, 0, List.of(),
                        List.of(new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty())))))));
        assertFalse(withoutMouse.runtimeShim().contains("Mouse"));
    }

    @Test
    void rejectsProgramsOutsideTheSupportedSubset() {
        MethodRef entryPoint = new MethodRef("demo/Floaty", "main", "()V");
        IrBasicBlock block = new IrBasicBlock(0,
                List.of(new IrInstruction.FloatConst(Value.float32(0), 1.0f)),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = IrMethod.withInferredValues(entryPoint, 0, 1, List.of(), List.of(block));

        CompileException exception = assertThrows(CompileException.class,
                () -> new CortexM4AsmBackend().generate(new IrProgram(entryPoint, List.of(method))));
        assertTrue(exception.getMessage().contains("does not support"));
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

        assertTrue(assembly.contains("cmp r0, r1"));
        assertTrue(assembly.contains("blt .Lcmptrue"));
        assertTrue(assembly.contains("bl juno_fn1")); // main calling classify by its assigned label
        assertTrue(assembly.contains(".global juno_Cond_asm")); // only the entry point is exported
    }
}
