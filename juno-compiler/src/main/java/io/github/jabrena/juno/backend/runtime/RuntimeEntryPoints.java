package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.ScopedValueSupport;
import io.github.jabrena.juno.linker.StructuredTaskSupport;
import io.github.jabrena.juno.linker.ThreadSupport;

import java.util.Map;

/**
 * What every backend needs to know about the concurrency runtime: the generated functions the shim calls back into,
 * and whether a program needs the preemptive scheduler at all.
 */
public final class RuntimeEntryPoints {
    /**
     * Where the runtime shim enters generated code: a started task's {@code Runnable} or {@code Callable}, and a
     * {@code ScopedValue.Carrier.call} operation. Each takes one reference and returns nothing or one word.
     */
    public static final Map<MethodRef, String> SYMBOLS = Map.of(
            ThreadSupport.ENTRY_METHOD, "juno_thread_entry",
            StructuredTaskSupport.ENTRY_METHOD, "juno_task_entry",
            ScopedValueSupport.ENTRY_METHOD, "juno_scoped_call_entry");

    private RuntimeEntryPoints() {
    }

    /**
     * Whether the program starts a thread, forks a task or takes a lock, so it needs the preemptive thread runtime.
     */
    public static boolean usesThreads(IrProgram program) {
        return program.methods().stream().anyMatch(method -> method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .anyMatch(instruction -> instruction instanceof IrInstruction.IntrinsicCall call
                        && switch (call.intrinsic()) {
                            case THREAD_START_VIRTUAL, THREAD_JOIN, THREAD_IS_ALIVE,
                                    TASK_SCOPE_OPEN_DEFAULT, TASK_SCOPE_OPEN, TASK_SCOPE_FORK_CALLABLE,
                                    TASK_SCOPE_FORK_RUNNABLE, TASK_SCOPE_JOIN, TASK_SCOPE_IS_CANCELLED, TASK_GET,
                                    TASK_STATE, TASK_EXCEPTION, TASK_SCOPE_CLOSE,
                                    MONITOR_ENTER, MONITOR_EXIT, REENTRANT_LOCK_NEW, REENTRANT_LOCK_LOCK,
                                    REENTRANT_LOCK_TRY_LOCK, REENTRANT_LOCK_UNLOCK -> true;
                            default -> false;
                        }));
    }

    /**
     * The generated functions, bracketed by {@code juno_java_code_start}/{@code juno_java_code_end} in a program with
     * threads: the Thumb-2 preemption tick only interrupts a thread whose pc lies between them, so the runtime and the
     * core's libraries always run to their next return undisturbed.
     */
    public static CharSequence preemptibleCode(boolean usesThreads, CharSequence code) {
        if (!usesThreads) {
            return code;
        }
        return "    .global juno_java_code_start\njuno_java_code_start:\n" + code
                + "\n    .global juno_java_code_end\njuno_java_code_end:\n";
    }
}
