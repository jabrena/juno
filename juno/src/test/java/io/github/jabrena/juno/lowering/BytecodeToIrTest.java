package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.Program;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeToIrTest {
    @TempDir
    Path temporaryDirectory;

    private final BytecodeToIr lowering = new BytecodeToIr();

    @Test
    void lowersStraightLineArithmeticAndAnIntrinsicCall() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        Gpio.pinMode(13, value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Simple");

        IrMethod method = entryPointMethod(program);

        assertEquals(1, method.blocks().size());
        List<IrInstruction> instructions = method.blocks().get(0).instructions();
        // javac constant-folds `1 + 2` into a single iconst_3, so this is Const, StoreLocal,
        // Const, LoadLocal, IntrinsicCall — no Binary instruction.
        assertEquals(5, instructions.size());

        var three = assertInstanceOf(IrInstruction.Const.class, instructions.get(0));
        assertEquals(3, three.value());
        var storedValue = assertInstanceOf(IrInstruction.StoreLocal.class, instructions.get(1));
        assertEquals(three.target(), storedValue.value());

        var pin = assertInstanceOf(IrInstruction.Const.class, instructions.get(2));
        assertEquals(13, pin.value());
        var loadedValue = assertInstanceOf(IrInstruction.LoadLocal.class, instructions.get(3));
        assertEquals(storedValue.local(), loadedValue.local());
        var call = assertInstanceOf(IrInstruction.IntrinsicCall.class, instructions.get(4));
        assertEquals(Intrinsic.GPIO_PIN_MODE, call.intrinsic());
        assertEquals(List.of(pin.target(), loadedValue.target()), call.arguments());
        assertTrue(call.target().isEmpty());

        assertInstanceOf(IrTerminator.Return.class, method.blocks().get(0).terminator());
        assertTrue(((IrTerminator.Return) method.blocks().get(0).terminator()).value().isEmpty());
    }

    @Test
    void lowersAnIfIntoThreeBlocksWithACompareAndBranch() throws Exception {
        String source = """
                package demo;
                public final class Branchy {
                    static int pick(int flag) {
                        if (flag == 0) {
                            return 1;
                        }
                        return 2;
                    }
                    public static void main(String[] args) {
                        pick(0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Branchy", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Branchy");

        IrMethod pick = methodNamed(program, "pick");
        assertEquals(3, pick.blocks().size());

        IrBasicBlock header = blockAt(pick, 0);
        assertEquals(3, header.instructions().size());
        assertInstanceOf(IrInstruction.LoadLocal.class, header.instructions().get(0));
        assertInstanceOf(IrInstruction.Const.class, header.instructions().get(1));
        var compare = assertInstanceOf(IrInstruction.Compare.class, header.instructions().get(2));
        assertEquals(io.github.jabrena.juno.ir.Condition.NOT_EQUAL, compare.condition());
        var branch = assertInstanceOf(IrTerminator.Branch.class, header.terminator());
        assertEquals(compare.target(), branch.condition());
        assertEquals(6, branch.trueTarget());
        assertEquals(4, branch.falseTarget());

        IrBasicBlock thenBlock = blockAt(pick, 4);
        var thenConst = assertInstanceOf(IrInstruction.Const.class, thenBlock.instructions().get(0));
        assertEquals(1, thenConst.value());
        var thenReturn = assertInstanceOf(IrTerminator.Return.class, thenBlock.terminator());
        assertEquals(thenConst.target(), thenReturn.value().orElseThrow());

        IrBasicBlock elseBlock = blockAt(pick, 6);
        var elseConst = assertInstanceOf(IrInstruction.Const.class, elseBlock.instructions().get(0));
        assertEquals(2, elseConst.value());
        assertInstanceOf(IrTerminator.Return.class, elseBlock.terminator());
    }

    @Test
    void lowersALoopWithoutLosingValuesAcrossTheBackEdge() throws Exception {
        String source = """
                package demo;
                public final class Loop {
                    static int addTo(int limit) {
                        int value = 0;
                        for (int i = 0; i < limit; i++) value += i;
                        return value;
                    }
                    public static void main(String[] args) {
                        addTo(4);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Loop", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Loop");

        IrMethod addTo = methodNamed(program, "addTo");

        assertTrue(addTo.blocks().size() > 1);
        boolean sawBackEdge = addTo.blocks().stream()
                .anyMatch(block -> block.terminator() instanceof IrTerminator.Jump jump
                        && jump.target() < block.start());
        assertTrue(sawBackEdge, "expected a jump back to an earlier block for the loop");
        boolean sawReturn = addTo.blocks().stream()
                .anyMatch(block -> block.terminator() instanceof IrTerminator.Return);
        assertTrue(sawReturn);
    }

    private IrMethod entryPointMethod(Program program) {
        return lowering.lower(program).methods().stream()
                .filter(method -> method.reference().equals(program.entryPoint()))
                .findFirst()
                .orElseThrow();
    }

    private IrMethod methodNamed(Program program, String name) {
        LinkedMethod linked = program.methods().stream()
                .filter(method -> method.method().reference().name().equals(name))
                .findFirst()
                .orElseThrow();
        return lowering.lower(linked);
    }

    private IrBasicBlock blockAt(IrMethod method, int start) {
        return method.blocks().stream().filter(block -> block.start() == start).findFirst().orElseThrow();
    }
}
