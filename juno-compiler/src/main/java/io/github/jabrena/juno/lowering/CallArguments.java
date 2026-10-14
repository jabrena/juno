package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A call's arguments in declaration order, as popped from the operand stack. A string parameter an intrinsic needs
 * as compile-time text arrives as its {@link Literal} rather than as a runtime value.
 */
record CallArguments(List<Argument> arguments) {
    CallArguments {
        arguments = List.copyOf(arguments);
    }

    sealed interface Argument permits RuntimeValue, Literal {
    }

    record RuntimeValue(Value value) implements Argument {
    }

    record Literal(String text) implements Argument {
    }

    /** The runtime values, in order, skipping literals. */
    List<Value> values() {
        return arguments.stream()
                .<Value>mapMulti((argument, sink) -> {
                    if (argument instanceof RuntimeValue(Value value)) {
                        sink.accept(value);
                    }
                })
                .toList();
    }

    /** The compile-time literals, in order. */
    List<String> literals() {
        return arguments.stream()
                .<String>mapMulti((argument, sink) -> {
                    if (argument instanceof Literal(String text)) {
                        sink.accept(text);
                    }
                })
                .toList();
    }

    Value value(int index) {
        return ((RuntimeValue) arguments.get(index)).value();
    }

    /** The literal text of argument {@code index}, or {@code null} when it was passed as a runtime value. */
    @Nullable String literal(int index) {
        return arguments.get(index) instanceof Literal(String text) ? text : null;
    }
}
