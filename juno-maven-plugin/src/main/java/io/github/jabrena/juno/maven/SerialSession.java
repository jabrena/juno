package io.github.jabrena.juno.maven;

import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A line-based conversation with a board over its USB serial port. Lines the board prints are read on a background thread
 * and handed out in order by {@link #nextLine}; {@link #send} writes one request line.
 */
final class SerialSession implements AutoCloseable {
    static final int BAUD_RATE = 115200;

    private final InputStream in;
    private final OutputStream out;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final Thread reader;

    SerialSession(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
        this.reader = Thread.ofPlatform().daemon().name("juno-secret-serial").unstarted(this::readLines);
        this.reader.start();
    }

    /**
     * Opens {@code port}, then configures it for raw 115200 baud with the executor while it stays open. The order matters
     * on a USB-serial adapter, such as the ESP32-CAM's: its driver resets the line settings once the last handle closes,
     * so settings made before the port is opened are gone by the time it is read. The board re-enumerates after an upload,
     * and a Pico 2 W flashed from BOOTSEL takes a moment to bring its port back, so both steps are retried until
     * {@code timeout} passes.
     */
    static SerialSession open(Path port, CommandExecutor executor, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = Files.newInputStream(port);
                out = Files.newOutputStream(port);
                configure(port, executor);
                return new SerialSession(in, out);
            } catch (IOException | ArduinoCliException exception) {
                closeQuietly(in);
                closeQuietly(out);
                if (System.nanoTime() > deadline) {
                    throw new ArduinoCliException("Could not open " + port + " for the secret session: "
                            + exception.getMessage());
                }
                sleep(250);
            }
        }
    }

    private static void configure(Path port, CommandExecutor executor) {
        boolean bsd = System.getProperty("os.name", "").startsWith("Mac");
        List<String> arguments = List.of("stty", bsd ? "-f" : "-F", port.toString(), String.valueOf(BAUD_RATE),
                "raw", "-echo");
        CommandResult result = executor.execute(new Command(arguments, Command.Mode.CAPTURED));
        if (!result.succeeded()) {
            throw new ArduinoCliException("Could not configure " + port + " with stty: " + result.output());
        }
    }

    /** Writes {@code line} followed by a line feed. Never log the line: it may carry a value. */
    void send(String line) {
        try {
            out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException exception) {
            throw new ArduinoCliException("Could not write to the board: " + exception.getMessage());
        }
    }

    /** The next line the board printed, waiting up to {@code timeout}. */
    String nextLine(Duration timeout) {
        try {
            String line = lines.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new ArduinoCliException("The board did not answer within " + timeout.toSeconds() + " seconds");
            }
            return line;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArduinoCliException("Interrupted while waiting for the board");
        }
    }

    @Override
    public void close() {
        reader.interrupt();
        try {
            in.close();
            out.close();
        } catch (IOException exception) {
            // The port is being released; nothing else to do.
        }
    }

    private void readLines() {
        StringBuilder current = new StringBuilder();
        try {
            int received;
            while ((received = in.read()) != -1) {
                if (received == '\n') {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current.setLength(0);
                } else if (received != '\r') {
                    current.append((char) received);
                }
            }
        } catch (IOException exception) {
            // The session was closed or the port went away; waiting callers time out.
        }
    }

    private static void closeQuietly(@Nullable Closeable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException exception) {
            // A port that failed to open or configure is being released; nothing else to do.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArduinoCliException("Interrupted while waiting for the port");
        }
    }
}
