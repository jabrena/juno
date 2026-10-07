package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;

/** Lists the boards and serial ports currently detected by Arduino CLI. Read-only: never touches a board. */
@Mojo(name = "boards")
public final class BoardsMojo extends AbstractArduinoMojo {
    @Override
    public void execute() throws MojoFailureException {
        try {
            List<BoardListJsonParser.DetectedPort> ports = arduinoCli().boardList();
            if (ports.isEmpty()) {
                getLog().info("No serial ports detected. Connect a board and try again.");
                return;
            }
            getLog().info("Detected ports (use -Djuno.port=<port> to select one):");
            for (BoardListJsonParser.DetectedPort port : ports) {
                getLog().info(describe(port));
            }
        } catch (ArduinoCliException exception) {
            throw failure(exception);
        }
    }

    static String describe(BoardListJsonParser.DetectedPort port) {
        String protocol = port.protocol().isBlank() ? "" : " [" + port.protocol() + "]";
        if (port.fqbns().isEmpty()) {
            return "  " + port.address() + protocol + " - unknown board";
        }
        return "  " + port.address() + protocol + " - " + String.join(", ", port.boardNames())
                + " (" + String.join(", ", port.fqbns()) + ")";
    }
}
