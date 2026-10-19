package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes instructions that only compute a value nothing reads: constants, local and static loads, field loads,
 * integer arithmetic other than division and remainder (which can panic on zero), comparisons and conversions.
 * Anything with an effect stays (calls, stores, allocations, array accesses, which are bounds-checked). A field load
 * is a plain {@code ldr} with no null check, so an unread one has no effect; one whose receiver is a compile-time
 * {@code null} stays anyway, for the runtime-risk report to flag. Inlining, folding and scalar replacement leave such
 * orphans behind; removing one can orphan its operands, so this repeats until nothing changes.
 */
public final class DeadValueElimination implements CompilerPass {

    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>(program.methods().size());
        for (IrMethod method : program.methods()) {
            methods.add(eliminate(method));
        }
        return program.withMethods(methods);
    }

    private IrMethod eliminate(IrMethod method) {
        List<IrBasicBlock> blocks = method.blocks();
        Set<Value> nulls = nullConstants(blocks);
        boolean removed = true;
        while (removed) {
            Set<Value> read = readValues(blocks, nulls);
            removed = false;
            List<IrBasicBlock> kept = new ArrayList<>(blocks.size());
            for (IrBasicBlock block : blocks) {
                List<IrInstruction> instructions = new ArrayList<>(block.instructions().size());
                for (IrInstruction instruction : block.instructions()) {
                    Value target = pureTarget(instruction, nulls);
                    if (target != null && !read.contains(target)) {
                        removed = true;
                    } else {
                        instructions.add(instruction);
                    }
                }
                kept.add(new IrBasicBlock(block.start(), List.copyOf(instructions), block.terminator()));
            }
            blocks = kept;
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }

    /** Every value some instruction or terminator reads (a pure instruction's own result is not a read). */
    private Set<Value> readValues(List<IrBasicBlock> blocks, Set<Value> nulls) {
        Set<Value> read = new HashSet<>();
        for (IrBasicBlock block : blocks) {
            for (IrInstruction instruction : block.instructions()) {
                Value target = pureTarget(instruction, nulls);
                for (Value value : instruction.referencedValues()) {
                    if (!value.equals(target)) {
                        read.add(value);
                    }
                }
            }
            read.addAll(block.terminator().referencedValues());
        }
        return read;
    }

    /** The single value a side-effect-free instruction defines, or null when the instruction must stay. */
    private @Nullable Value pureTarget(IrInstruction instruction, Set<Value> nulls) {
        return switch (instruction) {
            case IrInstruction.Const node -> node.target();
            case IrInstruction.StringConst node -> node.target();
            case IrInstruction.FloatConst node -> node.target();
            case IrInstruction.DoubleConst node -> node.target();
            case IrInstruction.LoadLocal node -> node.target();
            case IrInstruction.LoadStatic node -> node.target();
            case IrInstruction.LoadField node -> nulls.contains(node.receiver()) ? null : node.target();
            case IrInstruction.ConstantTableRef node -> node.target();
            case IrInstruction.Binary node ->
                    node.operation() == BinaryOp.DIVIDE || node.operation() == BinaryOp.REMAINDER ? null : node.target();
            case IrInstruction.Unary node -> node.target();
            case IrInstruction.Compare node -> node.target();
            case IrInstruction.FloatBinary node -> node.target();
            case IrInstruction.FloatNegate node -> node.target();
            case IrInstruction.FloatCompare node -> node.target();
            case IrInstruction.IntToFloat node -> node.target();
            case IrInstruction.DoubleBinary node -> node.target();
            case IrInstruction.DoubleNegate node -> node.target();
            case IrInstruction.DoubleCompare node -> node.target();
            case IrInstruction.IntToDouble node -> node.target();
            case IrInstruction.FloatToDouble node -> node.target();
            case IrInstruction.DoubleToFloat node -> node.target();
            default -> null;
        };
    }

    /** The values a {@code 0} constant defines: {@code null} references among them, which a dereference must keep. */
    private Set<Value> nullConstants(List<IrBasicBlock> blocks) {
        Set<Value> nulls = new HashSet<>();
        for (IrBasicBlock block : blocks) {
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.Const constant && constant.value() == 0) {
                    nulls.add(constant.target());
                }
            }
        }
        return nulls;
    }
}
