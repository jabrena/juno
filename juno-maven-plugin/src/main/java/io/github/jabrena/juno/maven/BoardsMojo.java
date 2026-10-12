package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;

/** Lists the boards and serial ports currently detected by Arduino CLI. Read-only: never touches a board. */
@Mojo(name = "boards")
public final class BoardsMojo extends AbstractArduinoMojo {
    @Override
    void run() {
        List<DetectedPort> ports = arduinoCli().boardList();
        if (ports.isEmpty()) {
            getLog().info("No serial ports detected. Connect a board and try again.");
            return;
        }
        getLog().info("Detected ports (use -Djuno.port=<port> to select one):");
        ports.stream().map(DetectedPort::describe).forEach(getLog()::info);
    }
}
