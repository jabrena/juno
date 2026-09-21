package io.github.jabrena.juno.maven;

import java.util.List;

interface CommandExecutor {
    CommandResult execute(List<String> command, boolean interactive);
}
