package io.github.jabrena.juno.maven;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Queues canned {@link CommandResult}s and records every {@link Command} it was asked to run, so goal and
 * {@link ArduinoCli} tests never touch a real process or board. */
final class FakeExecutor implements CommandExecutor {
    private final Deque<CommandResult> responses = new ArrayDeque<>();
    private final List<Command> invocations = new ArrayList<>();

    FakeExecutor respond(int exitCode, String output) {
        responses.addLast(new CommandResult(exitCode, output));
        return this;
    }

    @Override
    public CommandResult execute(Command command) {
        invocations.add(command);
        return responses.removeFirst();
    }

    List<Command> invocations() {
        return List.copyOf(invocations);
    }
}
