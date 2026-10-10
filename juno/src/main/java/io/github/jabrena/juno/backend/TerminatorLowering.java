package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrTerminator;

/** Emits the jump, branch, switch, or return that ends each IR block. */
final class TerminatorLowering {
    private final AsmEmitter asm;
    private final IntArithmeticLowering ints;
    private final String backedgeFunction;

    /** {@code backedgeFunction} is called on every loop backedge: the core's {@code yield}, or the thread runtime's. */
    TerminatorLowering(AsmEmitter asm, IntArithmeticLowering ints, String backedgeFunction) {
        this.asm = asm;
        this.ints = ints;
        this.backedgeFunction = backedgeFunction;
    }

    /**
     * {@code beforeReturn} runs ahead of a {@code Return}'s epilogue (the entry point's escape check). A non-null
     * {@code fused} compare was left out of the block's instructions: the branch tests its condition directly.
     */
    void emit(StringBuilder output, FrameLayout frame, String label, IrBasicBlock block,
              IrInstruction.Compare fused, Runnable beforeReturn) {
        switch (block.terminator()) {
            case IrTerminator.Jump jump -> {
                emitYieldIfBackedge(output, block.start(), jump.target());
                output.append("    b .L").append(label).append("block").append(jump.target()).append('\n');
            }
            case IrTerminator.Branch branch -> {
                emitYieldIfBackedge(output, block.start(), branch.trueTarget());
                emitYieldIfBackedge(output, block.start(), branch.falseTarget());
                String toFalse;
                if (fused != null) {
                    ints.emitFlags(output, frame, fused.left(), fused.right());
                    toFalse = "b" + IntArithmeticLowering.suffix(IntArithmeticLowering.inverse(fused.condition()));
                } else {
                    asm.load(output, frame, "r0", branch.condition());
                    output.append("    cmp r0, #0\n");
                    toFalse = "beq";
                }
                // A conditional branch (beq/bne/...) only has a short encoded range; the true/false
                // blocks can be arbitrarily far away in a large method. So the *conditional* hop only
                // ever jumps a few bytes, to a label right here, and the actual (possibly far) jumps
                // are unconditional `b`, which the assembler widens to whatever range it needs.
                String falseLabel = asm.newLabel(".Lbranchfalse");
                output.append("    ").append(toFalse).append(' ').append(falseLabel).append('\n')
                        .append("    b .L").append(label).append("block").append(branch.trueTarget()).append('\n')
                        .append(falseLabel).append(":\n")
                        .append("    b .L").append(label).append("block").append(branch.falseTarget()).append('\n');
            }
            case IrTerminator.Return returned -> {
                returned.value().ifPresent(value -> {
                    if (FrameLayout.isWide(value.type())) {
                        asm.load64(output, frame, "r0", "r1", value);
                    } else {
                        asm.load(output, frame, "r0", value);
                    }
                });
                beforeReturn.run();
                if (frame.frameSize() > 0) {
                    asm.emitLoadImmediate(output, "r12", frame.frameSize());
                    output.append("    add sp, sp, r12\n");
                }
                output.append("    pop {r4-r11, pc}\n");
            }
            case IrTerminator.Switch switched -> {
                for (int target : switched.targets()) {
                    emitYieldIfBackedge(output, block.start(), target);
                }
                emitYieldIfBackedge(output, block.start(), switched.defaultTarget());
                asm.load(output, frame, "r0", switched.selector());
                for (int i = 0; i < switched.keys().size(); i++) {
                    // Same short-conditional-hop/long-unconditional-jump idiom as Branch, chained:
                    // each case either jumps straight to its (possibly far) target, or falls through
                    // to the next case's check.
                    String nextCheckLabel = asm.newLabel(".Lswitchnext");
                    int key = switched.keys().get(i);
                    if (AsmEmitter.isModifiedImmediate(key)) {
                        output.append("    cmp r0, #").append(key).append('\n');
                    } else {
                        asm.emitLoadImmediate(output, "r1", key);
                        output.append("    cmp r0, r1\n");
                    }
                    output.append("    bne ").append(nextCheckLabel).append('\n')
                            .append("    b .L").append(label).append("block")
                            .append(switched.targets().get(i)).append('\n')
                            .append(nextCheckLabel).append(":\n");
                }
                output.append("    b .L").append(label).append("block")
                        .append(switched.defaultTarget()).append('\n');
            }
            default -> throw Thumb2AsmBackend.unsupported(block.terminator().getClass().getSimpleName());
        }
    }

    /** Keeps the core's USB service polled on every loop backedge. */
    private void emitYieldIfBackedge(StringBuilder output, int blockStart, int target) {
        if (target <= blockStart) {
            output.append("    bl ").append(backedgeFunction).append('\n');
        }
    }

}
