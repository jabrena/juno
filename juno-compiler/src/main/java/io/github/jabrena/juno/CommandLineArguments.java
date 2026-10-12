package io.github.jabrena.juno;

import java.util.List;

/** A cursor over command-line arguments that reads options and the values that follow them. */
final class CommandLineArguments {
    private final List<String> arguments;
    private int next;

    CommandLineArguments(List<String> arguments) {
        this.arguments = List.copyOf(arguments);
    }

    boolean hasNext() {
        return next < arguments.size();
    }

    String next() {
        return arguments.get(next++);
    }

    /** The value that follows {@code option}. */
    String valueFor(String option) {
        if (!hasNext()) {
            throw new CompileException("Missing value for " + option);
        }
        return next();
    }

    CompileException unknownOption(String option) {
        return new CompileException("Unknown option '" + option + "'");
    }
}
