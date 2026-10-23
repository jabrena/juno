package io.github.jabrena.juno.lowering;


import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.Program;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bytecode to IR lowering")
class BytecodeToIrTest {
    @TempDir
    Path temporaryDirectory;

    private final BytecodeToIr lowering = new BytecodeToIr();

    @Test
    @DisplayName("Lowers straight-line arithmetic into one block")
    void shouldLowerStraightLineArithmeticIntoOneBlock() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.ArduinoOne;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        ArduinoOne.getGpio().pinMode(13, value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Simple");

        // When
        IrMethod method = entryPointMethod(program);

        // Then
        assertThat(method.blocks()).hasSize(1);
    }

    @Test
    @DisplayName("Types every value of the straight-line method as int32")
    void shouldTypeEveryValueOfTheStraightLineMethodAsInt32() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.ArduinoOne;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        ArduinoOne.getGpio().pinMode(13, value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        IrMethod method = entryPointMethod(CompilerTestSupport.link(temporaryDirectory, "demo.Simple"));

        // When & Then
        assertThat(method.values()).allMatch(value -> value.type() == JunoType.INT32);
    }

    @Test
    @DisplayName("Folds the constant sum and keeps the pin constant")
    void shouldFoldTheConstantSumAndKeepThePinConstant() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.ArduinoOne;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        ArduinoOne.getGpio().pinMode(13, value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        IrMethod method = entryPointMethod(CompilerTestSupport.link(temporaryDirectory, "demo.Simple"));
        List<IrInstruction> instructions = method.blocks().get(0).instructions();

        // When & Then
        // javac constant-folds `1 + 2` into a single iconst_3.
        assertThat(instructionsOfType(instructions, IrInstruction.Const.class))
                .anyMatch(c -> c.value() == 3)
                .anyMatch(c -> c.value() == 13);
    }

    @Test
    @DisplayName("Stores the value into JVM local 1")
    void shouldStoreTheValueIntoJvmLocalOne() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.ArduinoOne;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        ArduinoOne.getGpio().pinMode(13, value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        IrMethod method = entryPointMethod(CompilerTestSupport.link(temporaryDirectory, "demo.Simple"));
        List<IrInstruction> instructions = method.blocks().get(0).instructions();

        // When & Then
        assertThat(instructionsOfType(instructions, IrInstruction.StoreLocal.class))
                .as("expected value to be stored to JVM local 1").anyMatch(s -> s.local() == 1);
    }

    @Test
    @DisplayName("Lowers the intrinsic call with its arguments and no target or receiver")
    void shouldLowerTheIntrinsicCallWithItsArgumentsAndNoTargetOrReceiver() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.util.Delay;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        Delay.millis(value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        IrMethod method = entryPointMethod(CompilerTestSupport.link(temporaryDirectory, "demo.Simple"));
        List<IrInstruction> instructions = method.blocks().get(0).instructions();

        // When
        IrInstruction.IntrinsicCall call = instructionsOfType(instructions, IrInstruction.IntrinsicCall.class)
                .stream().findFirst().orElseThrow();

        // Then
        assertThat(call.intrinsic()).isEqualTo(Intrinsic.DELAY_MILLIS);
        assertThat(call.arguments()).hasSize(1);
        assertThat(call.target()).isEmpty();
        assertThat(call.receiver()).isEmpty();
    }

    @Test
    @DisplayName("Returns void from the straight-line method")
    void shouldReturnVoidFromTheStraightLineMethod() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.ArduinoOne;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                public final class Simple {
                    public static void main(String[] args) {
                        int value = 1 + 2;
                        ArduinoOne.getGpio().pinMode(13, value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Simple", source);
        IrMethod method = entryPointMethod(CompilerTestSupport.link(temporaryDirectory, "demo.Simple"));

        // When
        IrTerminator terminator = method.blocks().get(0).terminator();

        // Then
        assertThat(terminator).isInstanceOfSatisfying(IrTerminator.Return.class,
                returned -> assertThat(returned.value()).isEmpty());
    }

    @Test
    @DisplayName("Lowers an if into three blocks with a compare and branch")
    void shouldLowerAnIfIntoThreeBlocksWithACompareAndBranch() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Branchy"), "pick");

        // When & Then
        assertThat(pick.blocks()).hasSize(3);
    }

    @Test
    @DisplayName("The header compares the flag against zero and branches on the result")
    void shouldCompareTheFlagAgainstZeroAndBranchOnTheResult() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Branchy"), "pick");
        IrBasicBlock header = blockAt(pick, 0);

        // When
        IrInstruction.Compare compare = instructionsOfType(header.instructions(), IrInstruction.Compare.class)
                .stream().findFirst().orElseThrow();

        // Then
        assertThat(compare.condition()).isEqualTo(Condition.NOT_EQUAL);
    }

    @Test
    @DisplayName("The header branches on the compare result to the then and else blocks")
    void shouldBranchOnTheCompareResultToTheThenAndElseBlocks() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Branchy"), "pick");
        IrBasicBlock header = blockAt(pick, 0);
        IrInstruction.Compare compare = instructionsOfType(header.instructions(), IrInstruction.Compare.class)
                .stream().findFirst().orElseThrow();

        // When
        assertThat(header.terminator()).isInstanceOf(IrTerminator.Branch.class);
        var branch = (IrTerminator.Branch) header.terminator();

        // Then
        assertThat(branch.condition()).isEqualTo(compare.target());
        assertThat(new int[] {branch.trueTarget(), branch.falseTarget()}).containsExactly(6, 4);
    }

    @Test
    @DisplayName("The then block returns the constant one")
    void shouldReturnTheConstantOneFromTheThenBlock() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Branchy"), "pick");
        IrBasicBlock thenBlock = blockAt(pick, 4);

        // When & Then
        assertThat(instructionsOfType(thenBlock.instructions(), IrInstruction.Const.class))
                .anyMatch(c -> c.value() == 1);
        assertThat(thenBlock.terminator()).isInstanceOf(IrTerminator.Return.class);
    }

    @Test
    @DisplayName("The else block returns the constant two")
    void shouldReturnTheConstantTwoFromTheElseBlock() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Branchy"), "pick");
        IrBasicBlock elseBlock = blockAt(pick, 6);

        // When & Then
        assertThat(instructionsOfType(elseBlock.instructions(), IrInstruction.Const.class))
                .anyMatch(c -> c.value() == 2);
        assertThat(elseBlock.terminator()).isInstanceOf(IrTerminator.Return.class);
    }

    @Test
    @DisplayName("Lowers a loop without losing values across the back edge")
    void shouldLowerALoopWithoutLosingValuesAcrossTheBackEdge() throws Exception {
        // Given
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
        IrMethod addTo = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Loop"), "addTo");

        // When & Then
        assertThat(addTo.blocks()).hasSizeGreaterThan(1)
                .anyMatch(block -> block.terminator() instanceof IrTerminator.Jump jump
                        && jump.target() < block.start());
    }

    @Test
    @DisplayName("A loop method returns a value")
    void shouldReturnAValueFromTheLoopMethod() throws Exception {
        // Given
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
        IrMethod addTo = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Loop"), "addTo");

        // When & Then
        assertThat(addTo.blocks()).anyMatch(block -> block.terminator() instanceof IrTerminator.Return);
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
    @DisplayName("Ternary branches share the same merge slot")
    void shouldShareTheSameMergeSlotBetweenTernaryBranches() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Ternary"), "pick");
        IrBasicBlock thenBlock = blockAt(pick, 4);
        IrBasicBlock elseBlock = blockAt(pick, 8);

        // When
        int thenSlot = lastStoreLocal(thenBlock.instructions()).local();
        int elseSlot = lastStoreLocal(elseBlock.instructions()).local();

        // Then
        assertThat(elseSlot).as("both branches of a ternary must write their result to the same slot").isEqualTo(thenSlot);
    }

    @Test
    @DisplayName("The merge block reads the value back from the slot both branches wrote")
    void shouldReadTheMergeValueFromTheSlotBothBranchesWrote() throws Exception {
        // Given
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
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.Ternary"), "pick");
        int thenSlot = lastStoreLocal(blockAt(pick, 4).instructions()).local();
        IrBasicBlock mergeBlock = blockAt(pick, 9);

        // When
        IrInstruction.LoadLocal firstLoad = instructionsOfType(mergeBlock.instructions(), IrInstruction.LoadLocal.class)
                .stream().findFirst().orElseThrow();

        // Then
        assertThat(firstLoad.local()).as("the merge block must read the value back from the same slot both branches wrote").isEqualTo(thenSlot);
    }

    @Test
    @DisplayName("Lowers typed float arguments, call result and returns")
    void shouldLowerTypedFloatArgumentsCallResultAndReturns() throws Exception {
        // Given
        String source = """
                package demo;
                public final class FloatCalls {
                    static float pick(float left, int useLeft, float right) {
                        if (useLeft != 0) return left;
                        return right;
                    }
                    public static void main(String[] args) {
                        float value = pick(1.25f, 0, 2.5f);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatCalls", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.FloatCalls");
        IrMethod main = entryPointMethod(program);
        IrInstruction.Call call = main.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(IrInstruction.Call.class::isInstance)
                .map(IrInstruction.Call.class::cast)
                .findFirst()
                .orElseThrow();

        // When & Then
        assertThat(call.arguments().stream().map(value -> value.type()).toList())
                .isEqualTo(List.of(JunoType.FLOAT32, JunoType.INT32, JunoType.FLOAT32));
    }

    @Test
    @DisplayName("A float call produces a float32 target")
    void shouldProduceAFloat32TargetFromAFloatCall() throws Exception {
        // Given
        String source = """
                package demo;
                public final class FloatCalls {
                    static float pick(float left, int useLeft, float right) {
                        if (useLeft != 0) return left;
                        return right;
                    }
                    public static void main(String[] args) {
                        float value = pick(1.25f, 0, 2.5f);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatCalls", source);
        IrMethod main = entryPointMethod(CompilerTestSupport.link(temporaryDirectory, "demo.FloatCalls"));
        IrInstruction.Call call = main.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(IrInstruction.Call.class::isInstance)
                .map(IrInstruction.Call.class::cast)
                .findFirst()
                .orElseThrow();

        // When & Then
        assertThat(call.target().orElseThrow().type()).isEqualTo(JunoType.FLOAT32);
    }

    @Test
    @DisplayName("A float method returns float32 values from both of its returns")
    void shouldReturnFloat32ValuesFromBothReturnsOfAFloatMethod() throws Exception {
        // Given
        String source = """
                package demo;
                public final class FloatCalls {
                    static float pick(float left, int useLeft, float right) {
                        if (useLeft != 0) return left;
                        return right;
                    }
                    public static void main(String[] args) {
                        float value = pick(1.25f, 0, 2.5f);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatCalls", source);
        IrMethod pick = methodNamed(CompilerTestSupport.link(temporaryDirectory, "demo.FloatCalls"), "pick");

        // When
        List<IrTerminator.Return> returns = pick.blocks().stream()
                .map(IrBasicBlock::terminator)
                .filter(IrTerminator.Return.class::isInstance)
                .map(IrTerminator.Return.class::cast)
                .filter(returned -> returned.value().isPresent())
                .toList();

        // Then
        assertThat(returns).hasSize(2)
                .allMatch(returned -> returned.value().orElseThrow().type() == JunoType.FLOAT32);
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
        return lowering.lower(linked, Map.of(), Board.UNO_R4_WIFI);
    }

    private IrBasicBlock blockAt(IrMethod method, int start) {
        return method.blocks().stream().filter(block -> block.start() == start).findFirst().orElseThrow();
    }
}
