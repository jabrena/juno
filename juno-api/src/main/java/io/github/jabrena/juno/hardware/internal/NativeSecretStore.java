package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.AvrEeprom;
import io.github.jabrena.juno.hardware.security.Esp32Nvs;
import io.github.jabrena.juno.hardware.security.Pico2WTrustZoneStore;
import io.github.jabrena.juno.hardware.security.Se050C2;
import io.github.jabrena.juno.hardware.security.SecretStore;
import org.jspecify.annotations.Nullable;

/** The selected board's secret store, opened on first use and shared after that. */
public final class NativeSecretStore {
    private static @Nullable SecretStore store;

    private NativeSecretStore() {
    }

    /** The store the MCU facade hands out, opened the first time it is asked for. */
    public static SecretStore secretStore() {
        SecretStore opened = store;
        if (opened == null) {
            opened = open();
            store = opened;
        }
        return opened;
    }

    /** Replaces the store for a whole-program emulator in a test, never on the board; {@code null} reopens it. */
    public static void setSecretStore(@Nullable SecretStore replacement) {
        store = replacement;
    }

    /**
     * Opens whatever store the board actually has. The board is asked here rather than passed in, so the answer is a
     * constant in this method: the switch is decided when the program is linked and the arms the target cannot take
     * are dropped with it, so only the chosen provider reaches the generated program — and a core without the
     * others' shim never has to supply it.
     */
    private static SecretStore open() {
        return switch (MCU.getBoardModel()) {
            case PORTENTA_H7 -> new Se050C2();
            case NANO_ESP32, M5_CARDPUTER, AI_THINKER_ESP32_CAM, UNO_R4_WIFI -> new Esp32Nvs();
            case PICO_2_W -> new Pico2WTrustZoneStore();
            case UNO_R3 -> new AvrEeprom();
            default -> throw new IllegalStateException("This board has no secret store");
        };
    }
}
