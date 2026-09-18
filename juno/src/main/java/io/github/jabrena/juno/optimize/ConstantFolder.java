package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds {@code Binary}/{@code Unary}/{@code Compare} instructions into {@code Const} when their operands are
 * already known constants earlier in the same block, and folds a {@code Branch} into a {@code Jump} when its
 * condition is a known constant.
 *
 * <p>This tracks constants purely at the {@link Value} level: a value reloaded from a JVM local or a
 * synthetic operand-stack slot via {@code LoadLocal} is never treated as constant here, since its slot may
 * have been written by more than one predecessor block. Seeing through a {@code StoreLocal}/{@code LoadLocal}
 * round trip to the same slot is copy propagation, a separate, later pass.
 */
public final class ConstantFolder implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>();
        for (IrMethod method : program.methods()) {
            methods.add(foldMethod(method));
        }
        return new IrProgram(program.entryPoint(), List.copyOf(methods));
    }

    private IrMethod foldMethod(IrMethod method) {
        List<IrBasicBlock> blocks = new ArrayList<>();
        for (IrBasicBlock block : method.blocks()) {
            blocks.add(foldBlock(block));
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }

    private IrBasicBlock foldBlock(IrBasicBlock block) {
        Map<Value, Integer> constants = new HashMap<>();
        List<IrInstruction> folded = new ArrayList<>();
        for (IrInstruction instruction : block.instructions()) {
            folded.add(foldInstruction(instruction, constants));
        }
        return new IrBasicBlock(block.start(), List.copyOf(folded), foldTerminator(block.terminator(), constants));
    }

    private IrInstruction foldInstruction(IrInstruction instruction, Map<Value, Integer> constants) {
        return switch (instruction) {
            case IrInstruction.Const constant -> {
                constants.put(constant.target(), constant.value());
                yield constant;
            }
            case IrInstruction.Binary binary -> {
                Integer left = constants.get(binary.left());
                Integer right = constants.get(binary.right());
                Integer folded = left != null && right != null
                        ? foldBinary(binary.operation(), left, right) : null;
                if (folded == null) {
                    yield binary;
                }
                constants.put(binary.target(), folded);
                yield new IrInstruction.Const(binary.target(), folded);
            }
            case IrInstruction.Unary unary -> {
                Integer operand = constants.get(unary.value());
                if (operand == null) {
                    yield unary;
                }
                int folded = foldUnary(unary.operation(), operand);
                constants.put(unary.target(), folded);
                yield new IrInstruction.Const(unary.target(), folded);
            }
            case IrInstruction.Compare compare -> {
                Integer left = constants.get(compare.left());
                Integer right = constants.get(compare.right());
                if (left == null || right == null) {
                    yield compare;
                }
                int folded = foldCompare(compare.condition(), left, right) ? 1 : 0;
                constants.put(compare.target(), folded);
                yield new IrInstruction.Const(compare.target(), folded);
            }
            default -> instruction;
        };
    }

    private IrTerminator foldTerminator(IrTerminator terminator, Map<Value, Integer> constants) {
        if (terminator instanceof IrTerminator.Branch branch) {
            Integer condition = constants.get(branch.condition());
            if (condition != null) {
                return new IrTerminator.Jump(condition != 0 ? branch.trueTarget() : branch.falseTarget());
            }
        }
        if (terminator instanceof IrTerminator.Switch switched) {
            Integer selector = constants.get(switched.selector());
            if (selector != null) {
                int target = switched.defaultTarget();
                for (int index = 0; index < switched.keys().size(); index++) {
                    if (switched.keys().get(index) == selector) {
                        target = switched.targets().get(index);
                        break;
                    }
                }
                return new IrTerminator.Jump(target);
            }
        }
        return terminator;
    }

    /** Returns null when the operation is not safe to fold at compile time (division/remainder by zero). */
    private Integer foldBinary(BinaryOp operation, int left, int right) {
        return switch (operation) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> right == 0 ? null : left / right;
            case REMAINDER -> right == 0 ? null : left % right;
            case SHIFT_LEFT -> left << right;
            case SHIFT_RIGHT -> left >> right;
            case UNSIGNED_SHIFT_RIGHT -> left >>> right;
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
        };
    }

    private int foldUnary(UnaryOp operation, int value) {
        return switch (operation) {
            case NEGATE -> -value;
            case TO_BYTE -> (byte) value;
            case TO_CHAR -> (char) value;
            case TO_SHORT -> (short) value;
        };
    }

    private boolean foldCompare(Condition condition, int left, int right) {
        return switch (condition) {
            case EQUAL -> left == right;
            case NOT_EQUAL -> left != right;
            case LESS_THAN -> left < right;
            case GREATER_EQUAL -> left >= right;
            case GREATER_THAN -> left > right;
            case LESS_EQUAL -> left <= right;
        };
    }
}
