package io.github.jabrena.juno.maven;

/** How a {@link Command} ended: its exit code and, for a captured command, its output. */
record CommandResult(int exitCode, String output) {
    boolean succeeded() {
        return exitCode == 0;
    }

    boolean hasOutput() {
        return !output.isBlank();
    }
}
