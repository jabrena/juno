package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArduinoCppBackendTest {
    @Test
    void declaresIrValuesUsingTheirJunoTypes() {
        MethodRef entryPoint = new MethodRef("demo/Typed", "main", "()V");
        List<Value> values = List.of(
                Value.int32(0),
                new Value(1, JunoType.FLOAT32),
                new Value(2, JunoType.FLOAT64));
        IrBasicBlock block = new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()));
        IrMethod method = new IrMethod(entryPoint, 0, values, List.of(), List.of(block));

        String generated = new ArduinoCppBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertTrue(generated.contains("  int32_t v0;"));
        assertTrue(generated.contains("  float v1;"));
        assertTrue(generated.contains("  double v2;"));
    }

    @Test
    void pollsTheArduinoRuntimeAtLoopBackedges() {
        MethodRef entryPoint = new MethodRef("demo/Polling", "main", "()V");
        IrBasicBlock loop = new IrBasicBlock(0, List.of(), new IrTerminator.Jump(0));
        IrMethod method = new IrMethod(entryPoint, 0, List.of(), List.of(), List.of(loop));

        String generated = new ArduinoCppBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertTrue(generated.contains("void yield() {"));
        assertTrue(generated.contains("static_cast<void>(static_cast<bool>(Serial));"));
        assertTrue(generated.contains("  yield();\n  goto juno_pc_0;"));
    }
}
