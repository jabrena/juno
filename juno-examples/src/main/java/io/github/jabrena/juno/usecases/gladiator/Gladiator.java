package io.github.jabrena.juno.usecases.gladiator;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.gladiator.net.GameSocket;
import io.github.jabrena.juno.usecases.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.usecases.gladiator.net.WifiSettings;
import io.github.jabrena.juno.usecases.gladiator.screen.ArenaFlow;
import io.github.jabrena.juno.usecases.gladiator.screen.FighterFlow;
import io.github.jabrena.juno.usecases.gladiator.screen.FighterSetup;
import io.github.jabrena.juno.usecases.gladiator.screen.ModeMenu;
import io.github.jabrena.juno.usecases.gladiator.screen.Opening;
import io.github.jabrena.juno.usecases.gladiator.screen.RoleMenu;
import io.github.jabrena.juno.usecases.gladiator.screen.SoloFlow;
import io.github.jabrena.juno.usecases.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.usecases.gladiator.view.Hud;
import io.github.jabrena.juno.usecases.gladiator.view.ViewList;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

import java.io.IOException;

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
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class Gladiator {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final BoardModel BOARD = ArduinoUno.getBoardModel();
    private static final int ARENA = 0;

    /** The screen's line list, which every role draws into, and the class models of the title and the Solo screens. */
    private final ViewList list = new ViewList();
    private final ClassWireframe wireframe = new ClassWireframe(list);
    /** Whether Wi-Fi and the UDP socket are up: they are started once, the first time MULTIPLAYER is chosen. */
    private boolean online;
    private GameSocket link;

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        Serial.begin(BaudRate.BAUD_115200);
        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        new Gladiator().play(tft);
    }

    /** The title, then the game menu for ever. */
    private void play(TftTouchShield tft) {
        // Seeded before the title too, so even its first quote is picked at random.
        Random.seed(CLOCK.micros());
        Opening.play(tft, list, wireframe);
        Random.seed(CLOCK.micros());
        while (true) {
            int mode = ModeMenu.choose(tft, BOARD.isQ());
            if (mode == ModeMenu.SOLO) {
                SoloFlow.run(tft, list, wireframe, FighterSetup.choose(tft));
                Opening.play(tft, list, wireframe);
            } else {
                multiplayer(tft);
            }
        }
    }


    private void multiplayer(TftTouchShield tft) {
        if (!connect(tft)) {
            return;
        }
        while (true) {
            int role = chooseRole(tft);
            if (role == RoleMenu.RETURN) {
                return;
            }
            if (role == ARENA) {
                ArenaFlow.run(tft, link);
            } else {
                new FighterFlow(list, role).run(tft, link);
            }
        }
    }

    /**
     * Joins the Wi-Fi network named on the SD card ({@link WifiSettings}; UNO Q uses the connection configured on its
     * Linux side) and opens the UDP socket on {@link GladiatorProtocol#PORT}. Returns false after showing what is missing, when an UNO
     * R4 finds no network to join, once MENU is tapped.
     */
    private boolean connect(TftTouchShield tft) {
        if (online) {
            return true;
        }
        WifiSettings settings = WifiSettings.load(tft.getSdCard());
        if (settings == null) {
            if (!BOARD.isQ()) {
                Hud.message(tft, "NO WIFI SETTINGS", "Put application.properties on the SD card", TftTouchShield.ORANGE);
                waitForMenu(tft);
                return false;
            }
            settings = WifiSettings.NONE;
        }
        tft.fillScreen(ViewList.BACKGROUND);
        Hud.centered(tft, "CONNECTING WIFI", 110, 2, TftTouchShield.CYAN, ViewList.BACKGROUND);
        Serial.println("Connecting Wi-Fi");
        WIFI.begin(settings.ssid(), settings.password());
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        link = listen();
        Serial.println("Listening on UDP port " + GladiatorProtocol.PORT);
        online = true;
        return true;
    }

    /** Opens the game's socket, retrying while another one still holds the port. */
    private static GameSocket listen() {
        while (true) {
            try {
                return GameSocket.open(GladiatorProtocol.PORT);
            } catch (IOException e) {
                Serial.println("UDP port busy, retrying");
                Delay.millis(1000);
            }
        }
    }

    /** Waits for a tap on the MENU button under a full-screen message, and for the finger to lift. */
    private static void waitForMenu(TftTouchShield tft) {
        while (!tft.readTouch() || tft.touchY() < ViewList.VIEW_BOTTOM) {
            Delay.millis(10);
        }
        while (tft.readTouch()) {
            Delay.millis(10);
        }
    }

    /** The role menu: {@link #ARENA}, {@link Brain#AGGRESSIVE}, {@link Brain#AMBUSH} or {@link RoleMenu#RETURN}. */
    public static int chooseRole(TftTouchShield tft) {
        return RoleMenu.choose(tft);
    }

    /** The role whose button is at ({@code x}, {@code y}), {@link RoleMenu#RETURN}, or -1. */
    public static int roleAt(int x, int y) {
        return RoleMenu.roleAt(x, y);
    }
}
