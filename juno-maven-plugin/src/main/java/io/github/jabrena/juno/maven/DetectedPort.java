package io.github.jabrena.juno.maven;

import java.util.List;

/**
 * A serial port {@code arduino-cli board list} reported, with the boards it recognized on it.
 *
 * @param protocol the port protocol, such as {@code serial}; blank when not reported
 */
record DetectedPort(String address, String protocol, List<String> boardNames, List<String> fqbns, String vendorId) {
    DetectedPort {
        boardNames = List.copyOf(boardNames);
        fqbns = List.copyOf(fqbns);
    }

    /** Whether a board with {@code fqbn} is connected on this port. */
    boolean hosts(String fqbn) {
        boolean cardputer = fqbn.equals("m5stack:esp32:m5stack_cardputer")
                || fqbn.startsWith("m5stack:esp32:m5stack_cardputer:");
        return fqbns.contains(fqbn)
                || (cardputer && protocol.equals("serial") && vendorId.equalsIgnoreCase("0x303A"));
    }

    /** One line of {@code juno:boards} output. */
    String describe() {
        String protocolSuffix = protocol.isBlank() ? "" : " [" + protocol + "]";
        String boards = fqbns.isEmpty()
                ? "unknown board"
                : String.join(", ", boardNames) + " (" + String.join(", ", fqbns) + ")";
        return "  " + address + protocolSuffix + " - " + boards;
    }
}
