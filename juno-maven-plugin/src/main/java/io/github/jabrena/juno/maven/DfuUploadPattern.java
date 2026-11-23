package io.github.jabrena.juno.maven;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A board's {@code dfu-util} upload recipe, read from the properties {@code arduino-cli} resolves for it
 * ({@code compile --show-properties=unexpanded}), so the upload can be narrowed to one device by USB serial number.
 * The recipe stays the core's own, placeholders included; only {@code -S "<serial>"} is appended, since dfu-util stops
 * when more than one DFU device matches its {@code --device} filter (a Nano ESP32 offers DFU whenever it is plugged in).
 *
 * @param tool the board's upload tool, the {@code <tool>} in {@code tools.<tool>.upload.pattern}
 * @param pattern that tool's unexpanded upload pattern
 */
record DfuUploadPattern(String tool, String pattern) {
    private static final Pattern SERIAL = Pattern.compile("[0-9A-Za-z._-]+");

    /** The dfu-util upload recipe in {@code properties}, or an error naming {@code fqbn} when the board has none. */
    static DfuUploadPattern from(String fqbn, String properties) {
        Map<String, String> values = new HashMap<>();
        properties.lines().forEach(line -> {
            int equals = line.indexOf('=');
            if (equals > 0) {
                values.putIfAbsent(line.substring(0, equals).strip(), line.substring(equals + 1));
            }
        });
        String tool = values.getOrDefault("upload.tool", values.get("upload.tool.default"));
        String command = tool == null ? null : values.get("tools." + tool + ".cmd");
        String pattern = tool == null ? null : values.get("tools." + tool + ".upload.pattern");
        if (tool == null || command == null || !command.contains("dfu-util") || pattern == null) {
            throw new ArduinoCliException("-Djuno.dfuSerial applies only to boards that upload with dfu-util; " + fqbn
                    + " uploads with " + (tool == null ? "no known tool" : tool));
        }
        return new DfuUploadPattern(tool, pattern);
    }

    /**
     * The same recipe without dfu-util's {@code :leave}: the board stays in its bootloader after this upload, so a
     * second image can follow before either one runs.
     */
    DfuUploadPattern staying() {
        return new DfuUploadPattern(tool, pattern.replace(":leave", ""));
    }

    /** The {@code --upload-property} value that runs this recipe as it is. */
    String asProperty() {
        return "upload.pattern=" + pattern;
    }

    /**
     * The {@code --upload-property} value that makes the upload pick the DFU device with USB serial {@code serial}. It
     * overrides {@code upload.pattern}, the key arduino-cli runs once it has merged the tool's properties in (an
     * override of {@code tools.<tool>.upload.pattern} is ignored, as arduino-cli 1.5.1 shows).
     */
    String selecting(String serial) {
        if (!SERIAL.matcher(serial).matches()) {
            throw new ArduinoCliException("-Djuno.dfuSerial must be a USB serial number (letters, digits, '.', '_' or "
                    + "'-'), not '" + serial + "'");
        }
        return asProperty() + " -S \"" + serial + "\"";
    }
}
