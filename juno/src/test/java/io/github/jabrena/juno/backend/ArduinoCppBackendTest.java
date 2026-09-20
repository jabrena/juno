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

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(generated.contains("  int32_t v0;")).isTrue();
        assertThat(generated.contains("  float v1;")).isTrue();
        assertThat(generated.contains("  double v2;")).isTrue();
    }

    @Test
    void pollsTheArduinoRuntimeAtLoopBackedges() {
        MethodRef entryPoint = new MethodRef("demo/Polling", "main", "()V");
        IrBasicBlock loop = new IrBasicBlock(0, List.of(), new IrTerminator.Jump(0));
        IrMethod method = new IrMethod(entryPoint, 0, List.of(), List.of(), List.of(loop));

        String generated = new ArduinoCppBackend().generate(new IrProgram(entryPoint, List.of(method)));

        assertThat(generated.contains("void yield() {")).isTrue();
        assertThat(generated.contains("if (juno_yield_active) return;")).isTrue();
        assertThat(generated.contains("juno_yield_active = true;\n  static_cast<void>(static_cast<bool>(Serial));\n  juno_yield_active = false;")).isTrue();
        assertThat(generated.contains("static_cast<void>(static_cast<bool>(Serial));")).isTrue();
        assertThat(generated.contains("  yield();\n  goto juno_pc_0;")).isTrue();
    }
}
