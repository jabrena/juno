package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.led.LedMatrix;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Gladiator, a network game for boards with the ELEGOO 2.8" TFT touch shield: two gladiators fight among the
 * pillars of a Roman arena, each seen through its own eyes on its own board, while a third board keeps the arena.
 * Every board runs this same program and picks its role on the touch screen:
 *
 * <ul>
 *   <li><b>ARENA</b> ({@link ArenaFlow}): keeps the state of the battle and shows the coliseum from above. It
 *       sends the map once to each gladiator and decides every strike. Meant for the most powerful board, an UNO
 *       Q, one per network.</li>
 *   <li><b>AGGRESSIVE</b> and <b>AMBUSH</b> ({@link FighterFlow}): a gladiator with that behaviour ({@link
 *       Aggressive}, {@link Ambush}), shown in first person in wireframe like DOOM ({@link View}). It sees its
 *       opponent only when its eyes do ({@link Eyes}): in front of it and not behind a pillar.</li>
 * </ul>
 *
 * When the arena has two gladiators the battle appears on every screen: the first to join enters by the left
 * gate, the second by the right. The CPU fights by its behaviour; CPU/HUMAN under the view hands the gladiator to
 * the touch screen. A strike the victim did not see coming does double damage, which is what the AMBUSH gladiator
 * plays for. All roles use UDP port {@link #PORT}; see {@link GladiatorProtocol} for the packets.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Gladiator {
    /** UDP port every board listens on and broadcasts to. */
    static final int PORT = 4211;

    private static final int ARENA = 0;

    private Gladiator() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Opening.play();
        Random.seed(Clock.micros());

        while (true) {
            int mode = ModeMenu.choose(soloAvailable());
            if (mode == ModeMenu.SOLO) {
                SoloFlow.run(FighterSetup.choose());
                Opening.play();
            } else {
                multiplayer();
            }
        }
    }

    /** Solo is intentionally limited to the UNO Q; its 13-column matrix identifies that compilation target. */
    static boolean soloAvailable() {
        return LedMatrix.columns() == 13;
    }

    private static void multiplayer() {
        // One set of packet buffers shared by whichever network role is running.
        byte[] incoming = new byte[GladiatorProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[GladiatorProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        connect();
        while (true) {
            int role = chooseRole();
            if (role == ARENA) {
                ArenaFlow.run(incoming, outgoing, source);
            } else {
                FighterFlow.run(role, incoming, outgoing, source);
            }
        }
    }

    /**
     * Joins Wi-Fi with build-time {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} (UNO Q uses the connection
     * configured on its Linux side) and opens the UDP socket on {@link #PORT}.
     */
    private static void connect() {
        Hud.centered("CONNECTING WIFI", 110, 2, TftTouchShield.CYAN, ViewList.BACKGROUND);
        Serial.println("Connecting Wi-Fi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        while (!Udp.listen(PORT)) {
            Serial.println("UDP port busy, retrying");
            Delay.millis(1000);
        }
        Serial.println("Listening on UDP port " + PORT);
    }

    /** The role menu: {@link #ARENA}, {@link Brain#AGGRESSIVE} or {@link Brain#AMBUSH}. */
    static int chooseRole() {
        return RoleMenu.choose();
    }

    /** The role whose button is at ({@code x}, {@code y}), or -1. */
    static int roleAt(int x, int y) {
        return RoleMenu.roleAt(x, y);
    }
}
