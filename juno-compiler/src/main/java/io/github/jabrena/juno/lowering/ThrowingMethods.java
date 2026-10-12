package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.BigNumberMethods;
import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which methods can finish with an exception still unwinding: those with an {@code athrow}, and, transitively,
 * those that call one. This is deliberately conservative (a call wrapped in a catch-all still counts), because
 * the only cost of a false positive is one extra pending-exception poll in the caller; callers of methods
 * outside this set get no poll at all, so programs that never throw across a call are unchanged.
 */
final class ThrowingMethods {
    private final BigNumberMethods bigNumberMethods = new BigNumberMethods();

    private final RandomAccessFileMethods randomAccessFileMethods = new RandomAccessFileMethods();

    private final Set<MethodRef> methods;

    /** No method is known to throw yet: only instructions that raise an exception themselves may throw. */
    ThrowingMethods() {
        this(Set.of());
    }

    private ThrowingMethods(Set<MethodRef> methods) {
        this.methods = Set.copyOf(methods);
    }

    /** The methods among {@code program} that may throw, found by propagating through calls until nothing changes. */
    ThrowingMethods closureOver(List<IrMethod> program) {
        Set<MethodRef> throwing = new HashSet<>(methods);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrMethod method : program) {
                if (!throwing.contains(method.reference()) && mayUnwind(method, throwing)) {
                    throwing.add(method.reference());
                    changed = true;
                }
            }
        }
        return new ThrowingMethods(throwing);
    }

    boolean isEmpty() {
        return methods.isEmpty();
    }

    /** Whether {@code instruction} may leave an exception pending: it raises one, or calls a method that may. */
    boolean mayThrow(IrInstruction instruction) {
        return mayThrow(instruction, methods);
    }

    private boolean mayUnwind(IrMethod method, Set<MethodRef> throwing) {
        return method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .anyMatch(instruction -> mayThrow(instruction, throwing));
    }

    private boolean mayThrow(IrInstruction instruction, Set<MethodRef> throwing) {
        return raises(instruction) || callees(instruction).stream().anyMatch(throwing::contains);
    }

    private boolean raises(IrInstruction instruction) {
        return instruction instanceof IrInstruction.IntrinsicCall call
                && switch (call.intrinsic()) {
                    case THROW_RAISE, THROW_DISPATCH, TASK_SCOPE_OPEN, TASK_SCOPE_FORK_CALLABLE,
                            TASK_SCOPE_FORK_RUNNABLE, TASK_SCOPE_JOIN, TASK_GET, TASK_STATE, TASK_EXCEPTION,
                            TASK_SCOPE_CLOSE, SCOPED_VALUE_GET, SCOPED_CARRIER_RUN, SCOPED_CARRIER_CALL -> true;
                    default -> bigNumberMethods.mayThrow(call.intrinsic())
                            || randomAccessFileMethods.mayThrow(call.intrinsic());
                };
    }

    /** The program methods {@code instruction} may invoke; empty for anything that is not a call. */
    private List<MethodRef> callees(IrInstruction instruction) {
        return switch (instruction) {
            case IrInstruction.Call call -> List.of(call.method());
            case IrInstruction.LambdaCall call -> List.of(call.site().implementation().method());
            case IrInstruction.InterfaceCall call -> call.targets().stream()
                    .map(target -> target.isLambda()
                            ? Objects.requireNonNull(target.lambda()).implementation().method()
                            : target.method())
                    .toList();
            default -> List.of();
        };
    }
}
