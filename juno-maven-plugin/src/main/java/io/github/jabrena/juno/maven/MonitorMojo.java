package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Discovers the board port and opens an interactive Arduino CLI serial monitor. */
@Mojo(name = "monitor")
public final class MonitorMojo extends AbstractArduinoMojo {
    /** Serial baud rate. */
    @Parameter(property = "juno.baudRate", defaultValue = "9600", required = true)
    private int baudRate;

    @Override
    public void execute() throws MojoFailureException {
        try {
            ArduinoCli cli = arduinoCli();
            String fqbn = defaultFqbn();
            String port = resolvePort(cli, fqbn);
            getLog().info("Opening serial monitor at " + baudRate + " baud; press Ctrl+C to stop");
            cli.monitor(fqbn, port, baudRate);
        } catch (ArduinoCliException exception) {
            throw failure(exception);
        }
    }
}
