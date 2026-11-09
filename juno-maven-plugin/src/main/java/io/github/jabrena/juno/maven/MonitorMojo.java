package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.Clock;

/**
 * Discovers the board port and opens an interactive Arduino CLI serial monitor; with {@code juno.syslog}, receives the
 * board's syslog datagrams over the network instead, so no board needs to be connected.
 */
@Mojo(name = "monitor")
public final class MonitorMojo extends AbstractArduinoMojo {
    /** Listen forever: a syslog monitor runs until Ctrl+C, like the serial one. */
    private static final int UNTIL_INTERRUPTED = -1;

    /** Serial baud rate — matches every juno-examples program's {@code Serial.begin(...)} rate. */
    @Parameter(property = "juno.baudRate", defaultValue = "115200", required = true)
    private int baudRate;

    /** Board whose port to attach to, e.g. {@code arduino-uno-q}; required for the serial monitor. */
    @Parameter(property = "juno.board")
    private @Nullable String board;

    /**
     * UDP port to receive RFC 5424 syslog on, e.g. {@code 5514} (the board-side {@code Syslog.DEFAULT_PORT}); when set,
     * the goal listens on every interface, so broadcast messages arrive too, and opens no serial port.
     */
    @Parameter(property = "juno.syslog")
    private @Nullable Integer syslog;

    /** How many syslog datagrams to receive before returning; only tests set it, to end an otherwise endless goal. */
    private int syslogDatagrams = UNTIL_INTERRUPTED;

    @Override
    void run() {
        Integer syslogPort = syslog;
        if (syslogPort != null) {
            listenForSyslog(syslogPort);
            return;
        }
        ArduinoCli cli = arduinoCli();
        String boardId = configured(board).orElseThrow(() -> new ArduinoCliException(
                "Missing required target board; configure -Djuno.board=<id>"));
        String fqbn = targetFqbn(Board.fromId(boardId).fqbn());
        String port = resolvePort(cli, fqbn);
        getLog().info("Opening serial monitor at " + baudRate + " baud; press Ctrl+C to stop");
        cli.monitor(port, baudRate);
    }

    /** Test seam: returns after {@code datagrams} syslog datagrams instead of listening until interrupted. */
    void stopSyslogAfter(int datagrams) {
        this.syslogDatagrams = datagrams;
    }

    private void listenForSyslog(int port) {
        if (port < 1 || port > 65535) {
            throw new ArduinoCliException("Invalid syslog port " + port + "; configure -Djuno.syslog=<1-65535>");
        }
        try (DatagramSocket socket = new DatagramSocket(port)) {
            getLog().info("Listening for syslog on UDP port " + port + "; press Ctrl+C to stop");
            new SyslogListener(socket, line -> getLog().info(line), Clock.systemDefaultZone()).listen(syslogDatagrams);
        } catch (SocketException failed) {
            throw new ArduinoCliException("Cannot listen for syslog on UDP port " + port + ": " + failed.getMessage(),
                    failed);
        } catch (UncheckedIOException failed) {
            throw new ArduinoCliException(String.valueOf(failed.getMessage()), failed);
        }
    }
}
