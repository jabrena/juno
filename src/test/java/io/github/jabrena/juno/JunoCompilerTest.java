package io.github.jabrena.juno;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunoCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesReachableMethodsBranchesAndHardwareIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class Main {
                    public static int addTo(int limit) {
                        int value = 0;
                        for (int i = 0; i < limit; i++) value += i;
                        return value;
                    }
                    public static int unused() { return 99; }
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                        Gpio.digitalWrite(13, addTo(4) == 6);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Main", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Main");

        assertTrue(generated.contains("Closed-world entry point: demo.Main.main"));
        assertTrue(generated.contains("pinMode(call_arg0, call_arg1)"));
        assertTrue(generated.contains("digitalWrite(call_arg0, call_arg1 ? HIGH : LOW)"));
        assertTrue(generated.contains("juno_demo_Main_addTo"));
        assertFalse(generated.contains("juno_demo_Main_unused"));
        assertTrue(generated.contains("goto juno_pc_"));
    }

    @Test
    void erasesDigitalOutputObjectsToPinNumbers() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.DigitalOutput;
                public final class ObjectStyleApi {
                    public static void main(String[] args) {
                        DigitalOutput led = DigitalOutput.of(13);
                        led.high();
                        led.low();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ObjectStyleApi", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ObjectStyleApi");

        assertTrue(generated.contains("stack[sp++] = juno_digital_output_of(call_arg0)"));
        assertTrue(generated.contains("pinMode(pin, OUTPUT)"));
        assertTrue(generated.contains("digitalWrite(call_receiver, HIGH)"));
        assertTrue(generated.contains("digitalWrite(call_receiver, LOW)"));
        assertFalse(generated.contains("new DigitalOutput"));
    }

    @Test
    void reportsUnsupportedBytecodeWithMethodAndOffset() throws Exception {
        String source = """
                package demo;
                public final class Objects {
                    public static void main(String[] args) { new Object(); }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Objects", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Objects"));

        assertTrue(exception.getMessage().contains("demo.Objects.main"));
        assertTrue(exception.getMessage().contains("unsupported opcode"));
        assertTrue(exception.getMessage().contains("bytecode offset"));
    }

    @Test
    void preservesJavaIntegerOverflowUsingUnsignedCppOperations() throws Exception {
        String source = """
                package demo;
                public final class MathProgram {
                    static int calculate(int a, int b) { return -((a + b) * (a - b)); }
                    public static void main() { calculate(Integer.MAX_VALUE, 2); }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MathProgram", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MathProgram");

        assertTrue(generated.contains("static_cast<uint32_t>(a) + static_cast<uint32_t>(b)"));
        assertTrue(generated.contains("juno_imul(lhs, rhs)"));
        assertTrue(generated.contains("juno_ineg(stack[sp - 1])"));
    }
}
