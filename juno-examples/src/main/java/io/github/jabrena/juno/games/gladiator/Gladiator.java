package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.games.gladiator.net.WifiSettings;
import io.github.jabrena.juno.games.gladiator.screen.ArenaFlow;
import io.github.jabrena.juno.games.gladiator.screen.FighterFlow;
import io.github.jabrena.juno.games.gladiator.screen.FighterSetup;
import io.github.jabrena.juno.games.gladiator.screen.ModeMenu;
import io.github.jabrena.juno.games.gladiator.screen.Opening;
import io.github.jabrena.juno.games.gladiator.screen.RoleMenu;
import io.github.jabrena.juno.games.gladiator.screen.SoloFlow;
import io.github.jabrena.juno.games.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.ViewList;
import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Udp;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

/**
 * Gladiator, a network game for boards with the ELEGOO 2.8" TFT touch shield: two gladiators fight among the
 * pillars of a Roman arena, each seen through its own eyes on its own board, while a third board keeps the arena.
 * Every board runs this same program and picks its role on the touch screen:
 *
 * <ul>
 *   <li><b>ARENA</b> ({@link ArenaFlow}): keeps the state of the battle and shows the coliseum from above. It
 *       sends the map once to each gladiator and decides every strike. Meant for the most powerful board, an UNO
 *       Q, one per network.</li>
 *   <li><b>PLAYER 1</b> and <b>PLAYER 2</b> ({@link FighterFlow}): an AGGRESSIVE Secutor or an AMBUSH Retiarius
 *       ({@link Aggressive}, {@link Ambush}), shown in first person in wireframe like DOOM ({@link View}). It sees
 *       its opponent only when its eyes do ({@link Eyes}): in front of it and not behind a pillar.</li>
 * </ul>
 *
 * RETURN goes back to the game menu.
 *
 * When the arena has two gladiators the battle appears on every screen: the first to join enters by the left
 * gate, the second by the right. The CPU fights by its behaviour; CPU/HUMAN under the view hands the gladiator to
 * the touch screen. A strike the victim did not see coming does double damage, which is what the AMBUSH gladiator
 * plays for. All roles use UDP port {@link GladiatorProtocol#PORT}; see {@link GladiatorProtocol} for the packets.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Gladiator {
    private static final Clock CLOCK = MCU.getClock();
    private static final BoardModel BOARD = MCU.getBoardModel();
    private static final int ARENA = 0;

    /** The screen's line list, which every role draws into, and the class models of the title and the Solo screens. */
    private final ViewList list = new ViewList();
    private final ClassWireframe wireframe = new ClassWireframe(list);
    /** Whether Wi-Fi and the UDP socket are up: they are started once, the first time MULTIPLAYER is chosen. */
    private boolean online;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        new Gladiator().play();
    }

    /** The title, then the game menu for ever. */
    private void play() {
        // Seeded before the title too, so even its first quote is picked at random.
        Random.seed(CLOCK.micros());
        Opening.play(list, wireframe);
        Random.seed(CLOCK.micros());
        while (true) {
            int mode = ModeMenu.choose(BOARD.isQ());
            if (mode == ModeMenu.SOLO) {
                SoloFlow.run(list, wireframe, FighterSetup.choose());
                Opening.play(list, wireframe);
            } else {
                multiplayer();
            }
        }
    }


    private void multiplayer() {
        // One set of packet buffers shared by whichever network role is running.
        byte[] incoming = new byte[GladiatorProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[GladiatorProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        if (!connect()) {
            return;
        }
        while (true) {
            int role = chooseRole();
            if (role == RoleMenu.RETURN) {
                return;
            }
            if (role == ARENA) {
                ArenaFlow.run(incoming, outgoing, source);
            } else {
                new FighterFlow(list, role).run(incoming, outgoing, source);
            }
        }
    }

    /**
     * Joins the Wi-Fi network named on the SD card ({@link WifiSettings}; UNO Q uses the connection configured on its
     * Linux side) and opens the UDP socket on {@link GladiatorProtocol#PORT}. Returns false after showing what is missing, when an UNO
     * R4 finds no network to join, once MENU is tapped.
     */
    private boolean connect() {
        if (online) {
            return true;
        }
        WifiSettings settings = WifiSettings.load();
        if (settings == null) {
            if (!BOARD.isQ()) {
                Hud.message("NO WIFI SETTINGS", "Put application.properties on the SD card", TftTouchShield.ORANGE);
                waitForMenu();
                return false;
            }
            settings = WifiSettings.NONE;
        }
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        Hud.centered("CONNECTING WIFI", 110, 2, TftTouchShield.CYAN, ViewList.BACKGROUND);
        Serial.println("Connecting Wi-Fi");
        Wifi.begin(settings.ssid(), settings.password());
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        while (!Udp.listen(GladiatorProtocol.PORT)) {
            Serial.println("UDP port busy, retrying");
            Delay.millis(1000);
        }
        Serial.println("Listening on UDP port " + GladiatorProtocol.PORT);
        online = true;
        return true;
    }

    /** Waits for a tap on the MENU button under a full-screen message, and for the finger to lift. */
    private static void waitForMenu() {
        while (!TftTouchShield.readTouch() || TftTouchShield.touchY() < ViewList.VIEW_BOTTOM) {
            Delay.millis(10);
        }
        while (TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
    }

    /** The role menu: {@link #ARENA}, {@link Brain#AGGRESSIVE}, {@link Brain#AMBUSH} or {@link RoleMenu#RETURN}. */
    public static int chooseRole() {
        return RoleMenu.choose();
    }

    /** The role whose button is at ({@code x}, {@code y}), {@link RoleMenu#RETURN}, or -1. */
    public static int roleAt(int x, int y) {
        return RoleMenu.roleAt(x, y);
    }
}
