package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/** Discovers the board port and opens an interactive Arduino CLI serial monitor. */
@Mojo(name = "monitor")
public final class MonitorMojo extends AbstractArduinoMojo {
    /** Serial baud rate — matches every juno-examples program's {@code Serial.begin(...)} rate. */
    @Parameter(property = "juno.baudRate", defaultValue = "115200", required = true)
    private int baudRate;

    /** Board whose port to attach to, e.g. {@code arduino-one-q}; required. */
    @Parameter(property = "juno.board")
    private @Nullable String board;

    @Override
    void run() {
        ArduinoCli cli = arduinoCli();
        String boardId = configured(board).orElseThrow(() -> new ArduinoCliException(
                "Missing required target board; configure -Djuno.board=<id>"));
        String fqbn = targetFqbn(Board.fromId(boardId).fqbn());
        String port = resolvePort(cli, fqbn);
        getLog().info("Opening serial monitor at " + baudRate + " baud; press Ctrl+C to stop");
        cli.monitor(port, baudRate);
    }
}
