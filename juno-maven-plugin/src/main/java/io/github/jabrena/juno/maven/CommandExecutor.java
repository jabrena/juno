package io.github.jabrena.juno.maven;

/** Runs external commands; tests substitute a fake so no real process (or board) is ever touched. */
interface CommandExecutor {
    CommandResult execute(Command command);
}
