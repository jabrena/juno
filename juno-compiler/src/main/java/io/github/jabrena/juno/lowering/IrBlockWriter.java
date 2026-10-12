package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The IR blocks of one method under construction: the blocks already finished plus the one being written. A
 * bytecode block usually becomes one IR block, but a guarded division or a call that may throw ends the IR block
 * early and continues the same bytecode block in a new one (see {@link #split}).
 */
final class IrBlockWriter {
    /** IR-only blocks live above every bytecode offset (a method's code is at most 65535 bytes). */
    static final int SYNTHETIC_BLOCK_BASE = 0x10000;
    /** Continuation and dispatch blocks after a call that may throw (see {@link CallGuard}). */
    static final int CALL_BLOCK_BASE = 0x40000;
    /** The shared block that returns from a frame with the pending exception still set. */
    static final int PROPAGATE_BLOCK = 0x7FFFFFF0;

    private final List<IrBasicBlock> finished = new ArrayList<>();
    private final List<IrInstruction> current = new ArrayList<>();
    private int currentStart;
    private @Nullable IrTerminator terminator;

    void startBlock(int start) {
        currentStart = start;
        current.clear();
        terminator = null;
    }

    void emit(IrInstruction instruction) {
        current.add(instruction);
    }

    void emitAll(List<IrInstruction> instructions) {
        current.addAll(instructions);
    }

    /** How many instructions the current block holds so far. */
    int size() {
        return current.size();
    }

    IrInstruction get(int index) {
        return current.get(index);
    }

    /** The last {@code count} instructions emitted, oldest first. */
    List<IrInstruction> last(int count) {
        return count > current.size() ? List.of() : List.copyOf(current.subList(current.size() - count, size()));
    }

    void removeLast(int count) {
        for (int removed = 0; removed < count; removed++) {
            current.removeLast();
        }
    }

    /** Removes and returns the instructions emitted from {@code index} on. */
    List<IrInstruction> takeFrom(int index) {
        List<IrInstruction> taken = new ArrayList<>(current.subList(index, current.size()));
        current.subList(index, current.size()).clear();
        return taken;
    }

    /** Ends the current block with {@code terminator}; the bytecode instructions that follow are lowered after it. */
    void terminate(IrTerminator terminator) {
        this.terminator = terminator;
    }

    /** Ends the current block early with {@code terminator} and continues writing into block {@code continuation}. */
    void split(IrTerminator terminator, int continuation) {
        finished.add(new IrBasicBlock(currentStart, List.copyOf(current), terminator));
        current.clear();
        currentStart = continuation;
    }

    /** Adds a complete synthetic block, such as the one that builds and throws an exception. */
    void add(IrBasicBlock block) {
        finished.add(block);
    }

    /** Finishes the current block, ending it with {@code fallthrough} when no instruction terminated it. */
    void finishBlock(Supplier<IrTerminator> fallthrough) {
        IrTerminator end = terminator == null ? fallthrough.get() : terminator;
        finished.add(new IrBasicBlock(currentStart, List.copyOf(current), end));
    }

    /** Every block written, plus the shared propagation block when some terminator jumps to it. */
    List<IrBasicBlock> blocks() {
        boolean propagates = finished.stream().anyMatch(block -> targetsPropagation(block.terminator()));
        if (!propagates) {
            return List.copyOf(finished);
        }
        List<IrBasicBlock> blocks = new ArrayList<>(finished);
        blocks.add(new IrBasicBlock(PROPAGATE_BLOCK, List.of(), new IrTerminator.Return(Optional.empty())));
        return List.copyOf(blocks);
    }

    private boolean targetsPropagation(IrTerminator terminator) {
        return switch (terminator) {
            case IrTerminator.Branch branch -> branch.trueTarget() == PROPAGATE_BLOCK
                    || branch.falseTarget() == PROPAGATE_BLOCK;
            case IrTerminator.Switch switched -> switched.defaultTarget() == PROPAGATE_BLOCK
                    || switched.targets().contains(PROPAGATE_BLOCK);
            case IrTerminator.Jump jump -> jump.target() == PROPAGATE_BLOCK;
            case IrTerminator.Return ignored -> false;
        };
    }
}
