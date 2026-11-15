package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.security.SecretStoreFactory;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Asks the board's secure element what it holds, without writing a single byte: the first question to put to a chip
 * before trusting a program that reads credentials from it.
 *
 * <p>It is written against {@link SecretStore} alone and takes its instance from
 * {@link SecretStoreFactory#open}, so the same program runs on every board that has a store and names no chip: the
 * factory picks the Portenta H7's onboard NXP SE050C2 or the ESP32-family boards' own non-volatile storage (they have
 * no secure element, and that store is weaker — see {@code Esp32Nvs}). The board query is answered when the
 * program is linked, so only the chosen provider reaches the image.
 *
 * <p>For each name it prints the length the chip returned, using the slot numbers of
 * {@link SecretProvisioner}'s table:
 *
 * <ul>
 *   <li>{@code read <n>} — the name holds {@code n} bytes. The value itself is never printed;</li>
 *   <li>{@code read -1} — the store reports the name missing. On an ESP32 board that is unambiguous: NVS is always
 *       there, so the name simply holds nothing. On the Portenta H7 it means an empty object <em>or</em> a chip
 *       that never answered, because both look the same through the interface.</li>
 * </ul>
 *
 * <p>Nothing here writes, clears or locks anything, so it is safe to flash on a board whose chip is already
 * provisioned. Flash it and read the output:
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProbe \
 *     -Djuno.board=arduino-uno-r3-wifi-rev2
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r3-wifi-rev2 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class,
        M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class, ArduinoPortentaH7Board.class})
public final class SecretProbe {
    /** The largest value Juno's secure-store providers accept. */
    private static final int MAX_VALUE_BYTES = 63;
    private static final String SSID_NAME = "wifi.ssid";
    private static final String PASSWORD_NAME = "wifi.password";

    private final SecretStore secrets;
    private final byte[] readBack = new byte[MAX_VALUE_BYTES];

    SecretProbe(SecretStore secrets) {
        this.secrets = secrets;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        SecretSlots slots = new SecretSlots().add(SSID_NAME, 8).add(PASSWORD_NAME, 9);
        Serial.println("probe start");
        SecretProbe probe = new SecretProbe(SecretStoreFactory.open(slots));
        Serial.println("store open");
        while (true) {
            probe.report(1, SSID_NAME);
            probe.report(2, PASSWORD_NAME);
            Delay.millis(2000);
        }
    }

    /** Reads {@code name} once and prints the length the chip returned, never the value. */
    private void report(int index, String name) {
        int length = secrets.read(name, readBack);
        Serial.print("name ");
        Serial.println(index);
        Serial.print("read ");
        Serial.println(length);
    }
}
