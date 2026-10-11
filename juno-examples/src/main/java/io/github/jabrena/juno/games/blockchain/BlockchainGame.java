package io.github.jabrena.juno.games.blockchain;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * A network game for several boards with the ELEGOO 2.8" TFT touch shield: factor numbers together
 * and chain the verified answers. Every board runs this same program. After joining Wi-Fi, the
 * touch screen asks which role this board plays:
 *
 * <ul>
 *   <li><b>LEADER</b> ({@link LeaderFlow}): hands out numbers to factor and keeps a SHA-256 hash chain
 *       of the verified answers. Meant for an UNO Q, one per network.</li>
 *   <li><b>WORKER</b> ({@link WorkerFlow}): finds the leader by broadcast, factors what it is given and
 *       reports back. Meant for UNO R4 WiFi boards, as many as you like.</li>
 *   <li><b>MINER</b> ({@link MinerFlow}): no leader at all. Mines a Bitcoin-style proof-of-work chain
 *       whose blocks each carry a verified factorization; the branch with the most work wins.</li>
 * </ul>
 *
 * The screen then shows that role's progress, also logged over serial at 115200 baud, and MENU
 * returns to the role menu. All roles share UDP port {@link LedgerIo#PORT}, so a board can change
 * roles without reconnecting.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class BlockchainGame {
    private static final int LEADER = 1;
    private static final int WORKER = 2;
    private static final int MINER = 3;

    private static final int BUTTON_X = 20;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 62;
    private static final int LEADER_Y = 66;
    private static final int WORKER_Y = 138;
    private static final int MINER_Y = 210;

    private BlockchainGame() {
    }

    public static void main(String[] args) {
        // One set of packet buffers, sized for the largest packet, shared by whichever role is running.
        byte[] incoming = new byte[ChainProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[ChainProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];

        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        GameScreen.message("CONNECTING WIFI", TftTouchShield.CYAN);
        LedgerIo.connect();

        while (true) {
            int role = chooseRole();
            if (role == LEADER) {
                LeaderFlow.run(incoming, outgoing, source);
            } else if (role == WORKER) {
                WorkerFlow.run(incoming, outgoing, source);
            } else {
                MinerFlow.run(incoming, outgoing, source);
            }
        }
    }

    private static int chooseRole() {
        drawMenu();
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int y = TftTouchShield.touchY();
            GameScreen.waitForRelease();
            if (y >= LEADER_Y && y < LEADER_Y + BUTTON_HEIGHT) {
                return LEADER;
            }
            if (y >= WORKER_Y && y < WORKER_Y + BUTTON_HEIGHT) {
                return WORKER;
            }
            if (y >= MINER_Y && y < MINER_Y + BUTTON_HEIGHT) {
                return MINER;
            }
        }
    }

    private static void drawMenu() {
        TftTouchShield.fillScreen(GameScreen.BACKGROUND);
        GameScreen.title("BLOCKCHAIN", TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, GameScreen.BACKGROUND);
        TftTouchShield.setCursor(20, 52);
        TftTouchShield.print("Choose this board's role");
        button(LEADER_Y, "LEADER", "Gives out work, keeps chain", TftTouchShield.YELLOW);
        button(WORKER_Y, "WORKER", "Factors numbers for leader", TftTouchShield.CYAN);
        button(MINER_Y, "MINER", "Mines blocks, no leader", TftTouchShield.GREEN);
    }

    private static void button(int y, String label, String detail, int color) {
        TftTouchShield.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, GameScreen.PANEL);
        TftTouchShield.fillRect(BUTTON_X, y, 6, BUTTON_HEIGHT, color);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, GameScreen.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 14);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, GameScreen.PANEL);
        TftTouchShield.setCursor(BUTTON_X + 16, y + 40);
        TftTouchShield.print(detail);
    }
}
