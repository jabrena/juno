package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Discovers the board port and opens an interactive Arduino CLI serial monitor. */
@Mojo(name = "monitor")
public final class MonitorMojo extends AbstractArduinoMojo {
    /** Serial baud rate — matches every juno-examples program's {@code Serial.begin(...)} rate. */
    @Parameter(property = "juno.baudRate", defaultValue = "115200", required = true)
    private int baudRate;

    @Override
    public void execute() throws MojoFailureException {
        try {
            ArduinoCli cli = arduinoCli();
            String fqbn = defaultFqbn();
            String port = resolvePort(cli, fqbn);
            getLog().info("Opening serial monitor at " + baudRate + " baud; press Ctrl+C to stop");
            cli.monitor(port, baudRate);
        } catch (ArduinoCliException exception) {
            throw failure(exception);
        }
    }
}
