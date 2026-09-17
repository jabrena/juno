package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.Condition;
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

        // javac constant-folds `1 + 2` into a single iconst_3.
        assertTrue(instructionsOfType(instructions, IrInstruction.Const.class).stream().anyMatch(c -> c.value() == 3));
        assertTrue(instructionsOfType(instructions, IrInstruction.Const.class).stream().anyMatch(c -> c.value() == 13));
        assertTrue(instructionsOfType(instructions, IrInstruction.StoreLocal.class).stream().anyMatch(s -> s.local() == 1),
                "expected value to be stored to JVM local 1");

        IrInstruction.IntrinsicCall call = instructionsOfType(instructions, IrInstruction.IntrinsicCall.class)
                .stream().findFirst().orElseThrow();
        assertEquals(Intrinsic.GPIO_PIN_MODE, call.intrinsic());
        assertEquals(2, call.arguments().size());
        assertTrue(call.target().isEmpty());
        assertTrue(call.receiver().isEmpty());

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
        IrInstruction.Compare compare = instructionsOfType(header.instructions(), IrInstruction.Compare.class)
                .stream().findFirst().orElseThrow();
        assertEquals(Condition.NOT_EQUAL, compare.condition());
        var branch = assertInstanceOf(IrTerminator.Branch.class, header.terminator());
        assertEquals(compare.target(), branch.condition());
        assertEquals(6, branch.trueTarget());
        assertEquals(4, branch.falseTarget());

        IrBasicBlock thenBlock = blockAt(pick, 4);
        assertTrue(instructionsOfType(thenBlock.instructions(), IrInstruction.Const.class).stream()
                .anyMatch(c -> c.value() == 1));
        assertInstanceOf(IrTerminator.Return.class, thenBlock.terminator());

        IrBasicBlock elseBlock = blockAt(pick, 6);
        assertTrue(instructionsOfType(elseBlock.instructions(), IrInstruction.Const.class).stream()
                .anyMatch(c -> c.value() == 2));
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

    /**
     * Regression test for a real bug: a naive single flat stack simulation in textual/offset order assigns a
     * fresh {@link io.github.jabrena.juno.ir.Value} to each push, so two predecessor blocks that each push a
     * value consumed after they merge (e.g. a ternary) ended up with the merge block always reading whichever
     * predecessor was processed last in the flat pass — regardless of which branch actually executed at
     * runtime. The fix represents stack positions as synthetic local slots that every predecessor writes to
     * and the merge block reads from, so this asserts both branches write the same slot and the merge block
     * reads that same slot back.
     */
    @Test
    void ternaryBranchesShareTheSameMergeSlot() throws Exception {
        String source = """
                package demo;
                public final class Ternary {
                    static int pick(int flag) {
                        int result = flag == 0 ? 1 : 2;
                        return result;
                    }
                    public static void main(String[] args) {
                        pick(0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Ternary", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Ternary");

        IrMethod pick = methodNamed(program, "pick");
        assertEquals(4, pick.blocks().size());

        IrBasicBlock thenBlock = blockAt(pick, 4);
        IrBasicBlock elseBlock = blockAt(pick, 8);
        int thenSlot = lastStoreLocal(thenBlock.instructions()).local();
        int elseSlot = lastStoreLocal(elseBlock.instructions()).local();
        assertEquals(thenSlot, elseSlot, "both branches of a ternary must write their result to the same slot");

        IrBasicBlock mergeBlock = blockAt(pick, 9);
        IrInstruction.LoadLocal firstLoad = instructionsOfType(mergeBlock.instructions(), IrInstruction.LoadLocal.class)
                .stream().findFirst().orElseThrow();
        assertEquals(thenSlot, firstLoad.local(),
                "the merge block must read the value back from the same slot both branches wrote");
    }

    private IrInstruction.StoreLocal lastStoreLocal(List<IrInstruction> instructions) {
        List<IrInstruction.StoreLocal> stores = instructionsOfType(instructions, IrInstruction.StoreLocal.class);
        return stores.get(stores.size() - 1);
    }

    private <T extends IrInstruction> List<T> instructionsOfType(List<IrInstruction> instructions, Class<T> type) {
        return instructions.stream().filter(type::isInstance).map(type::cast).toList();
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
