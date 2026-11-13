package io.github.jabrena.juno.maven;

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
     * Configures {@code port} for raw 115200 baud with the executor, then opens it. The board re-enumerates after an upload,
     * so the open is retried until {@code timeout} passes.
     */
    static SerialSession open(Path port, CommandExecutor executor, Duration timeout) {
        configure(port, executor);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            try {
                InputStream in = Files.newInputStream(port);
                OutputStream out = Files.newOutputStream(port);
                return new SerialSession(in, out);
            } catch (IOException exception) {
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArduinoCliException("Interrupted while waiting for the port");
        }
    }
}
