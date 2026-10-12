package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.classfile.ExceptionHandler;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which of one method's {@code catch}/{@code finally} handlers receives an exception thrown at a bytecode offset,
 * resolved at compile time from the exception table (first match wins, as the JVM does).
 */
final class ExceptionRouting {
    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    static final String ARITHMETIC_EXCEPTION = "java/lang/ArithmeticException";

    private final LinkedMethod method;
    private final Map<String, JavaClass> classes;

    ExceptionRouting(LinkedMethod method, Map<String, JavaClass> classes) {
        this.method = method;
        this.classes = classes;
    }

    /** The handler that catches an {@code ArithmeticException} thrown at {@code offset}, if any. */
    OptionalInt arithmeticHandlerAt(int offset) {
        return method.method().exceptionHandlers().stream()
                .filter(handler -> catches(handler, offset, ARITHMETIC_EXCEPTION))
                .mapToInt(ExceptionHandler::handlerPc)
                .findFirst();
    }

    /** The handler of each of the program's throwable classes (by class id) at {@code offset}. */
    HandlerTable handlersAt(int offset, List<String> throwableClasses) {
        Map<Integer, Integer> handlerByClassId = new TreeMap<>();
        for (int classId = 0; classId < throwableClasses.size(); classId++) {
            String thrown = throwableClasses.get(classId);
            OptionalInt handler = method.method().exceptionHandlers().stream()
                    .filter(candidate -> catches(candidate, offset, thrown))
                    .mapToInt(ExceptionHandler::handlerPc)
                    .findFirst();
            if (handler.isPresent()) {
                handlerByClassId.put(classId, handler.getAsInt());
            }
        }
        return new HandlerTable(handlerByClassId, throwableClasses.size());
    }

    private boolean catches(ExceptionHandler handler, int offset, String thrown) {
        return handler.covers(offset)
                && (handler.catchesAny() || throwableTypes.isSubtype(thrown, handler.catchType(), classes));
    }

    /**
     * The handlers that catch each throwable class at one offset.
     *
     * @param handlerByClassId the handler offset of each class some handler catches, by class id
     * @param classCount how many throwable classes the program has
     */
    record HandlerTable(Map<Integer, Integer> handlerByClassId, int classCount) {
        boolean isEmpty() {
            return handlerByClassId.isEmpty();
        }

        /** The caught-class bit mask passed to {@code THROW_DISPATCH}/{@code THROW_CATCH}. */
        int caughtMask() {
            return handlerByClassId.keySet().stream().mapToInt(classId -> 1 << classId).reduce(0, (a, b) -> a | b);
        }

        /**
         * Routes a caught exception's class id to its handler. A class no handler matches keeps unwinding through
         * the shared {@link IrBlockWriter#PROPAGATE_BLOCK}, unless every class is caught here.
         */
        IrTerminator dispatch(Value classId) {
            List<Integer> keys = List.copyOf(handlerByClassId.keySet());
            List<Integer> targets = keys.stream().map(handlerByClassId::get).toList();
            if (handlerByClassId.size() == classCount) {
                return Set.copyOf(targets).size() == 1
                        ? new IrTerminator.Jump(targets.getFirst())
                        : new IrTerminator.Switch(classId, keys, targets, targets.getFirst());
            }
            return new IrTerminator.Switch(classId, keys, targets, IrBlockWriter.PROPAGATE_BLOCK);
        }
    }
}
