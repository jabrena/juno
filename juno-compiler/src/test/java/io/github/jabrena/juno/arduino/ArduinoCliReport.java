package io.github.jabrena.juno.arduino;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the two figures the core-drift check needs from {@code arduino-cli}'s English text output. The container runs
 * with {@code LC_ALL=C}, so the wording below is the one it prints; the JSON form is not used because the test has no
 * JSON library.
 */
final class ArduinoCliReport {

    private static final Pattern GLOBAL_VARIABLES = Pattern.compile("Global variables use (\\d+) bytes");

    private ArduinoCliReport() {
    }

    /** The static RAM of a compile, from its {@code Global variables use N bytes} line. */
    static int staticRamBytes(String compileOutput) {
        Matcher matcher = GLOBAL_VARIABLES.matcher(compileOutput);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No 'Global variables use' line in arduino-cli output:\n"
                    + compileOutput);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** The installed version of {@code platformId} in {@code arduino-cli core list} output. */
    static String installedVersion(String coreList, String platformId) {
        for (String line : coreList.split("\n")) {
            String[] columns = line.strip().split("\\s+");
            if (columns.length >= 2 && columns[0].equals(platformId)) {
                return columns[1];
            }
        }
        throw new IllegalArgumentException(platformId + " is not listed by arduino-cli core list:\n" + coreList);
    }

    /** The platform of an FQBN, for example {@code arduino:avr} for {@code arduino:avr:uno}. */
    static String platformOf(String fqbn) {
        String[] parts = fqbn.split(":");
        return parts[0] + ":" + parts[1];
    }
}
