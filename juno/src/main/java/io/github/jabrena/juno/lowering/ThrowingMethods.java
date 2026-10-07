package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.BigNumberMethods;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which methods can finish with an exception still unwinding: those with an {@code athrow}, and, transitively,
 * those that call one. This is deliberately conservative (a call wrapped in a catch-all still counts), because
 * the only cost of a false positive is one extra pending-exception poll in the caller; callers of methods
 * outside this set get no poll at all, so programs that never throw across a call are unchanged.
 */
final class ThrowingMethods {
    private ThrowingMethods() {
    }

    static Set<MethodRef> of(List<IrMethod> methods) {
        Set<MethodRef> throwing = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrMethod method : methods) {
                if (!throwing.contains(method.reference()) && mayUnwind(method, throwing)) {
                    throwing.add(method.reference());
                    changed = true;
                }
            }
        }
        return Set.copyOf(throwing);
    }

    private static boolean mayUnwind(IrMethod method, Set<MethodRef> throwing) {
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                if (raises(instruction) || callees(instruction).stream().anyMatch(throwing::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean raises(IrInstruction instruction) {
        return instruction instanceof IrInstruction.IntrinsicCall call
                && switch (call.intrinsic()) {
                    case THROW_RAISE, THROW_DISPATCH, TASK_SCOPE_OPEN, TASK_SCOPE_FORK_CALLABLE,
                            TASK_SCOPE_FORK_RUNNABLE,
                            TASK_SCOPE_JOIN, TASK_GET, TASK_STATE, TASK_EXCEPTION, TASK_SCOPE_CLOSE,
                            SCOPED_VALUE_GET, SCOPED_CARRIER_RUN, SCOPED_CARRIER_CALL -> true;
                    default -> BigNumberMethods.mayThrow(call.intrinsic());
                };
    }

    /** The program methods {@code instruction} may invoke; empty for anything that is not a call. */
    static List<MethodRef> callees(IrInstruction instruction) {
        List<MethodRef> callees = new ArrayList<>();
        switch (instruction) {
            case IrInstruction.Call call -> callees.add(call.method());
            case IrInstruction.LambdaCall call -> callees.add(call.site().implementation().method());
            case IrInstruction.InterfaceCall call -> call.targets().forEach(target ->
                    callees.add(target.isLambda() ? target.lambda().implementation().method() : target.method()));
            default -> {
            }
        }
        return callees;
    }
}
