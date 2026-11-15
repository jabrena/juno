package io.github.jabrena.juno.hardware.security;

import io.github.jabrena.juno.hardware.MCU;

/** Selects the secret store compiled for a board. */
public final class SecretStoreFactory {
    private SecretStoreFactory() {
    }

    /** Whether the board being compiled has a secret store, so {@link #open} has one to return. */
    public static boolean isAvailable() {
        return switch (MCU.getBoardModel()) {
            case PORTENTA_H7, NANO_ESP32, M5_CARDPUTER, AI_THINKER_ESP32_CAM -> true;
            default -> false;
        };
    }

    /**
     * Opens the board's store of named secrets, which is whatever that board actually has:
     *
     * <ul>
     *   <li>the Portenta H7's onboard NXP {@link Se050C2};</li>
     *   <li>the ESP32-family boards' own non-volatile storage ({@link Esp32Nvs}), since they carry no secure element
     *       — weaker than a chip, and its Javadoc says how.</li>
     * </ul>
     *
     * <p>The other boards have no secret store: check {@link #isAvailable} and read their settings from elsewhere,
     * such as a file on the SD card. Opening one there fails.
     *
     * <p>The board is asked here rather than passed in, so the answer is a constant in this method: the switch is
     * decided when the program is linked and the arms the target cannot take are dropped with it, so only the chosen
     * provider reaches the generated program — and a core without the others' shim never has to supply it.
     */
    public static SecretStore open(SecretSlots slots) {
        return switch (MCU.getBoardModel()) {
            case PORTENTA_H7 -> new Se050C2(slots);
            case NANO_ESP32, M5_CARDPUTER, AI_THINKER_ESP32_CAM -> new Esp32Nvs(slots);
            default -> throw new IllegalStateException("This board has no secret store");
        };
    }
}
