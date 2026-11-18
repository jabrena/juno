package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;


/** Calls Juno evaluates while compiling: {@code System.getenv}, which a board program may not read. */
final class CompileTimeCalls {
    private static final MethodRef GETENV = new MethodRef("java/lang/System", "getenv",
            "(Ljava/lang/String;)Ljava/lang/String;");

    private final MethodContext context;
    private final OperandStack stack;

    CompileTimeCalls(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
    }

    boolean isGetenv(MethodRef called) {
        return called.equals(GETENV);
    }

    /**
     * Rejects {@code System.getenv(...)} in board code. Resolving a build-machine value here would emit it as a
     * firmware string constant, so credentials must be loaded at runtime from {@code SecretStore} or board storage.
     */
    void rejectGetenv(Instruction instruction) {
        stack.pop();
        throw new CompileException(context.at(instruction) + ": System.getenv(...) is not supported in board programs; "
                + "its build-time value would become part of the firmware. Use SecretStore or runtime board storage "
                + "for credentials and configuration");
    }
}
