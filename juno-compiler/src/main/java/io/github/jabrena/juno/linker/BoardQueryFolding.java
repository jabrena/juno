package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.EnumSwitchMap;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ExceptionHandler;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.jabrena.juno.bytecode.Opcodes.GETSTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.GOTO;
import static io.github.jabrena.juno.bytecode.Opcodes.IALOAD;
import static io.github.jabrena.juno.bytecode.Opcodes.IF_ACMPEQ;
import static io.github.jabrena.juno.bytecode.Opcodes.IF_ACMPNE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEVIRTUAL;
import static io.github.jabrena.juno.bytecode.Opcodes.LOOKUPSWITCH;
import static io.github.jabrena.juno.bytecode.Opcodes.POP;
import static io.github.jabrena.juno.bytecode.Opcodes.TABLESWITCH;

/**
 * Decides a branch on {@code MCU.getBoardModel()} against the board being compiled, so the code the target can never
 * reach is gone before reachability looks at the method.
 *
 * <p>The backend already turns the query into its constant, but that happens when instructions are emitted, long after
 * the linker has decided which methods and shim features the program needs. A query left standing until then makes
 * every arm reachable, which costs the dead arms' code and, worse, fails outright when their shim symbols do not exist
 * for the target: {@code MCU.getSecretStore()} has one arm per secret store, so without this a Portenta's SE050C2 arm
 * would demand an SE050 shim from boards whose core has none.
 *
 * <p>Two shapes are folded, both of them what javac emits for a constant the linker knows:
 *
 * <ul>
 *   <li><b>a switch over the model</b> — {@code getstatic $SwitchMap$…}, the query, {@code ordinal()}, {@code iaload}
 *       and a {@code tableswitch} or {@code lookupswitch}. The synthetic map says which key this board's ordinal
 *       carries, and the switch says where that key goes;</li>
 *   <li><b>a comparison against one constant</b> — the query beside a {@code getstatic} of a {@code BoardModel}
 *       constant, then {@code if_acmpeq} or {@code if_acmpne}.</li>
 * </ul>
 *
 * <p>In both cases the branch becomes a {@code pop} of what it would have tested, plus a {@code goto} when the jump is
 * always taken, and the blocks left without a path from the method's entry are dropped. Anything else is left exactly
 * as it was, so a method that asks no board query keeps its instructions, its graph and its generated output
 * unchanged.
 */
final class BoardQueryFolding {

    /** A method's instructions after folding, and whether any branch was decided away. */
    record Folded(List<Instruction> instructions, boolean changed) {
    }

    private static final String BOARD_MODEL = "Lio/github/jabrena/juno/hardware/BoardModel;";
    private static final MethodRef QUERY = new MethodRef("io/github/jabrena/juno/hardware/internal/NativeBoardModel",
            "boardModel", "()" + BOARD_MODEL);
    private static final MethodRef MCU_QUERY =
            new MethodRef("io/github/jabrena/juno/hardware/MCU", "getBoardModel", "()" + BOARD_MODEL);

    private final EnumSwitchMap switchMaps;

    BoardQueryFolding(Map<String, JavaClass> classes, BytecodeDecoder decoder) {
        this.switchMaps = new EnumSwitchMap(classes, decoder);
    }

    /**
     * Rewrites every branch on the board model in {@code instructions}, reporting {@code changed} only when one was
     * decided.
     */
    @SuppressWarnings("EnumOrdinal") // The board ABI deliberately encodes BoardModel as its declared ordinal.
    Folded fold(List<Instruction> instructions, JavaClass owner, Board board) {
        int ordinal = board.boardModel().ordinal();
        List<Instruction> rewritten = new ArrayList<>(instructions.size() + 2);
        boolean changed = false;
        int index = 0;
        while (index < instructions.size()) {
            Instruction instruction = instructions.get(index);
            rewritten.add(instruction);
            index++;
            if (!isQuery(instruction, owner) || index >= instructions.size()) {
                continue;
            }
            Integer target = comparisonTarget(instructions, index, owner, ordinal);
            if (target == null) {
                target = switchTarget(instructions, index, owner, ordinal);
            }
            if (target == null) {
                continue;
            }
            index = redirect(instructions, index, rewritten, target);
            changed = true;
        }
        return new Folded(changed ? List.copyOf(rewritten) : instructions, changed);
    }

    /**
     * Copies the instructions between the query and its branch, then replaces the branch with a {@code pop} of the
     * value it would have tested and, unless control simply falls through, a {@code goto} to where it goes.
     *
     * @return the index just past the branch that was replaced
     */
    private int redirect(List<Instruction> instructions, int index, List<Instruction> rewritten, int target) {
        int cursor = index;
        while (!isBranch(instructions.get(cursor).opcode())) {
            rewritten.add(instructions.get(cursor));
            cursor++;
        }
        Instruction branch = instructions.get(cursor);
        int offset = branch.offset();
        rewritten.add(new Instruction(offset, POP, 0, 0));
        int fallthrough = cursor + 1 < instructions.size() ? instructions.get(cursor + 1).offset() : -1;
        if (target != fallthrough) {
            // One byte along, inside the bytes the branch occupied: nothing else starts there, and no branch can
            // target the middle of an instruction.
            rewritten.add(new Instruction(offset + 1, GOTO, target - (offset + 1), 0));
        }
        return cursor + 1;
    }

    private static boolean isBranch(int opcode) {
        return opcode == IF_ACMPEQ || opcode == IF_ACMPNE || opcode == TABLESWITCH || opcode == LOOKUPSWITCH;
    }

    /** Where {@code if_acmpeq}/{@code if_acmpne} against one {@code BoardModel} constant sends this board. */
    private @Nullable Integer comparisonTarget(List<Instruction> instructions, int index, JavaClass owner,
            int ordinal) {
        Instruction constant = instructions.get(index);
        if (constant.opcode() != GETSTATIC || index + 1 >= instructions.size()) {
            return null;
        }
        int compared = switchMaps.ordinalOf(owner.constantPool().fieldRef(constant.operandA()));
        Instruction branch = instructions.get(index + 1);
        if (compared < 0 || (branch.opcode() != IF_ACMPEQ && branch.opcode() != IF_ACMPNE)) {
            return null;
        }
        boolean equal = compared == ordinal;
        boolean taken = branch.opcode() == IF_ACMPEQ ? equal : !equal;
        if (taken) {
            return branch.offset() + branch.operandA();
        }
        return index + 2 < instructions.size() ? instructions.get(index + 2).offset() : null;
    }

    /** Where a {@code switch} over the model sends this board, through javac's synthetic map. */
    private @Nullable Integer switchTarget(List<Instruction> instructions, int index, JavaClass owner, int ordinal) {
        // The map is pushed before the query, so the shape runs backwards from the switch: map, query, ordinal, iaload.
        if (index + 1 >= instructions.size() || instructions.get(index).opcode() != INVOKEVIRTUAL
                || instructions.get(index + 1).opcode() != IALOAD) {
            return null;
        }
        Instruction mapLoad = mapLoadBefore(instructions, index);
        if (mapLoad == null || index + 2 >= instructions.size()) {
            return null;
        }
        Instruction switched = instructions.get(index + 2);
        if (switched.opcode() != TABLESWITCH && switched.opcode() != LOOKUPSWITCH) {
            return null;
        }
        FieldRef map = owner.constantPool().fieldRef(mapLoad.operandA());
        List<Integer> mapping = EnumSwitchMap.isSwitchMap(map) ? switchMaps.resolve(map) : null;
        if (mapping == null || ordinal >= mapping.size()) {
            return null;
        }
        return caseTarget(switched, mapping.get(ordinal));
    }

    /** The {@code getstatic} of the synthetic map, which javac pushes just before the query. */
    private @Nullable Instruction mapLoadBefore(List<Instruction> instructions, int index) {
        for (int cursor = index - 2; cursor >= 0 && cursor >= index - 4; cursor--) {
            Instruction candidate = instructions.get(cursor);
            if (candidate.opcode() == GETSTATIC) {
                return candidate;
            }
        }
        return null;
    }

    /** Where {@code switched} sends {@code key}: its own case, or the default. */
    private int caseTarget(Instruction switched, int key) {
        List<Integer> keys = switched.switchKeys();
        List<Integer> targets = switched.switchOffsets();
        for (int entry = 0; entry < keys.size() && entry < targets.size(); entry++) {
            if (keys.get(entry) == key) {
                return switched.offset() + targets.get(entry);
            }
        }
        return switched.offset() + switched.operandA();
    }

    private boolean isQuery(Instruction instruction, JavaClass owner) {
        if (instruction.opcode() != INVOKESTATIC) {
            return false;
        }
        MethodRef called = owner.constantPool().methodRef(instruction.operandA());
        return called.equals(QUERY) || called.equals(MCU_QUERY);
    }

    /**
     * Drops the blocks of {@code graph} that no longer have a path from the entry. Every exception handler's block is a
     * root as well: the graph only draws an edge to a handler from an explicit {@code athrow}, so a handler reached
     * another way must not be mistaken for dead code.
     */
    ControlFlowGraph prune(ControlFlowGraph graph, List<ExceptionHandler> handlers) {
        Set<Integer> live = new HashSet<>();
        Deque<Integer> work = new ArrayDeque<>();
        work.add(graph.entry().start());
        handlers.forEach(handler -> work.add(handler.handlerPc()));
        while (!work.isEmpty()) {
            int start = work.poll();
            if (!live.add(start)) {
                continue;
            }
            graph.blockAt(start).ifPresent(block -> work.addAll(successors(block.terminator())));
        }
        return new ControlFlowGraph(graph.blocks().stream().filter(block -> live.contains(block.start())).toList());
    }

    /** The instructions of {@code graph}'s blocks, in offset order: what is left of the method after pruning. */
    List<Instruction> instructions(ControlFlowGraph graph) {
        return graph.blocks().stream().flatMap(block -> block.instructions().stream()).toList();
    }

    private List<Integer> successors(Terminator terminator) {
        return switch (terminator) {
            case Terminator.Jump jump -> List.of(jump.target());
            case Terminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case Terminator.Fallthrough fallthrough -> List.of(fallthrough.target());
            case Terminator.Switch switched -> switchTargets(switched);
            case Terminator.Throw thrown -> thrown.handlers();
            case Terminator.Return ignored -> List.of();
        };
    }

    private List<Integer> switchTargets(Terminator.Switch switched) {
        List<Integer> targets = new ArrayList<>(switched.targets());
        targets.add(switched.defaultTarget());
        return targets;
    }
}
