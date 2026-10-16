package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ClojureSupport;

import java.util.List;
import java.util.Optional;

/**
 * Lowers the {@link ClojureSupport} calls. A var is the handle of its function, so reading a var leaves the handle
 * on the stack, and {@code IFn.applyTo} is a direct call to the one function the program binds.
 */
final class ClojureLowering {
    private final MethodContext context;
    private final OperandStack stack;

    ClojureLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
    }

    void lower(Instruction instruction, MethodRef called) {
        if (called.equals(ClojureSupport.VAR_INTERN)) {
            internPrivate(instruction);
        } else if (called.equals(ClojureSupport.VAR_IS_BOUND)) {
            // Every var a program reaches is bound to a function; an unbound name has handle 0, which is never read.
            stack.pop();
            stack.pushInt(1);
        } else if (called.equals(ClojureSupport.VAR_GET) || called.equals(ClojureSupport.VAR_GET_RAW_ROOT)
                || called.equals(ClojureSupport.VAR_DEREF)) {
            // The receiver is the var's handle already, so it stays on the stack as the result.
        } else if (called.equals(ClojureSupport.RT_SEQ)) {
            // The entry point's arguments are always empty on a board, so the sequence is nil.
            stack.pop();
            stack.pushInt(0);
        } else if (called.equals(ClojureSupport.LOAD_WITH_CLASS)) {
            // Loading a namespace from its class is what Juno's linking already did, so nothing is pushed.
            stack.pop();
            stack.pop();
            stack.pushInt(0);
        } else if (called.equals(ClojureSupport.APPLY_TO)) {
            applyTo(instruction);
        } else {
            throw new CompileException(context.at(instruction) + ": Clojure call not supported by Juno: "
                    + called.displayName());
        }
    }

    /** {@code Var.internPrivate(ns, name)}: the handle of the function the two literal names identify, or 0. */
    private void internPrivate(Instruction instruction) {
        Value name = stack.pop();
        Value namespace = stack.pop();
        String namespaceName = context.facts().knownString(namespace);
        String varName = context.facts().knownString(name);
        if (namespaceName == null || varName == null) {
            throw new CompileException(context.at(instruction)
                    + ": Var.internPrivate needs literal namespace and variable names");
        }
        String functionClass = ClojureSupport.functionClass(namespaceName, varName);
        List<MethodRef> functions = context.program().clojureFunctions();
        int handle = 0;
        for (int index = 0; index < functions.size(); index++) {
            if (functions.get(index).owner().equals(functionClass)) {
                handle = index + 1;
            }
        }
        stack.pushInt(handle);
    }

    /**
     * {@code IFn.applyTo(args)} on a var handle. Juno cannot pick the function from a handle at run time yet, so a
     * program may bind only one function; the call goes to it directly.
     */
    private void applyTo(Instruction instruction) {
        Value arguments = stack.pop();
        stack.pop();
        List<MethodRef> functions = context.program().clojureFunctions();
        if (functions.size() != 1) {
            throw new CompileException(context.at(instruction)
                    + ": calling a Clojure function through IFn needs exactly one bound function, found "
                    + functions.size());
        }
        Value result = context.values().int32();
        context.emit(new IrInstruction.Call(Optional.of(result), functions.getFirst(), List.of(arguments)));
        stack.push(result);
    }
}
