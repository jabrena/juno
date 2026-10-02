package io.github.jabrena.juno.games.pokemon;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Udp;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Two-board Pokémon-style battle for an ELEGOO 2.8" TFT touch shield.
 *
 * <p>Both an UNO R4 WiFi and an UNO Q run this same program. UNO R4 connects with build-time
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}; UNO Q uses the Wi-Fi connection configured on
 * its Linux side. Each player first picks the UDP port both boards will use (tap - / + and CONNECT), then
 * selects a Pokémon and taps DISCOVER. The next board to join finds it through a small UDP broadcast
 * handshake; once both peers exchange selections, the battle starts automatically. A port can be refused
 * (the UNO Q keeps a port busy after a reflash until its router releases it) and a board may simply not
 * find its peer: BACK on the discovery screen returns to the port selector to try another port. Both
 * boards must be on the same port.
 *
 * <p>The demo deliberately uses the raw, reusable {@link Udp} buffers: broadcast is discovery,
 * {@link Udp#send} is the producer, and {@link Udp#receive} is the consumer. Battle packets use an
 * alternating sequence number and resend timeout so a lost or duplicated UDP datagram does not
 * apply an attack twice or permanently stall the match.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class PokemonBattle {
    private static final int FIRST_PORT = 5077;
    private static final int PORT_COUNT = 20;
    private static final int DISCOVERY_INTERVAL_MILLIS = 700;
    private static final int RESEND_MILLIS = 1000;

    private static final int BACKGROUND = 0x0841;
    private static final int PANEL = 0x18C3;
    private static final int SELECTED = 0x0320;
    private static final int BUTTON_X = 20;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 40;

    private PokemonBattle() {
    }

    public static void main(String[] args) {
        byte[] incoming = new byte[PokemonProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[PokemonProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        int[] peer = new int[Udp.ENDPOINT_SIZE];
        int[] opponent = new int[2];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        connectWifi();
        int nodeId = Clock.micros();
        if (nodeId == 0) {
            nodeId = 1;
        }

        int port = FIRST_PORT;
        while (true) {
            port = choosePort(port);
            boolean connected = true;
            while (connected) {
                int selected = choosePokemon();
                if (!discover(port, selected, nodeId, peer, opponent, incoming, source, outgoing)) {
                    connected = false;
                    Udp.stop();
                } else {
                    battle(selected, opponent[0], nodeId, opponent[1], peer, incoming, source, outgoing);
                    Delay.millis(3500);
                }
            }
        }
    }

    /** Lets the player pick the UDP port, then listens on it; a refused port returns to the selector. */
    private static int choosePort(int port) {
        int offset = port - FIRST_PORT;
        drawPortSelection(FIRST_PORT + offset);
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();
            if (y >= 100 && y < 170 && x < 120) {
                offset = (offset + PORT_COUNT - 1) % PORT_COUNT;
                drawPortSelection(FIRST_PORT + offset);
            } else if (y >= 100 && y < 170) {
                offset = (offset + 1) % PORT_COUNT;
                drawPortSelection(FIRST_PORT + offset);
            } else if (y >= 235 && y < 290) {
                if (Udp.listen(FIRST_PORT + offset)) {
                    return FIRST_PORT + offset;
                }
                showMessage("PORT IN USE", TftTouchShield.RED);
                Delay.millis(1500);
                drawPortSelection(FIRST_PORT + offset);
            }
        }
    }

    private static void connectWifi() {
        showMessage("CONNECTING WIFI", TftTouchShield.CYAN);
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        showMessage("WIFI CONNECTED", TftTouchShield.GREEN);
        Delay.millis(800);
    }

    private static int choosePokemon() {
        int selected = PokemonProtocol.PIKACHU;
        drawSelection(selected);
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int y = TftTouchShield.touchY();
            waitForRelease();
            if (y >= 65 && y < 105) {
                selected = PokemonProtocol.PIKACHU;
                drawSelection(selected);
            } else if (y >= 115 && y < 155) {
                selected = PokemonProtocol.CHARMANDER;
                drawSelection(selected);
            } else if (y >= 165 && y < 205) {
                selected = PokemonProtocol.SQUIRTLE;
                drawSelection(selected);
            } else if (y >= 235 && y < 290) {
                return selected;
            }
        }
    }

    /** Returns true once a peer is found, false if the player taps BACK to choose another port. */
    private static boolean discover(int port, int selected, int nodeId, int[] peer, int[] opponent,
                                    byte[] incoming, int[] source, byte[] outgoing) {
        drawWaiting(selected, port);
        int lastBroadcast = Clock.millis() - DISCOVERY_INTERVAL_MILLIS;
        while (true) {
            int now = Clock.millis();
            if (now - lastBroadcast >= DISCOVERY_INTERVAL_MILLIS) {
                PokemonProtocol.write(outgoing, PokemonProtocol.DISCOVER, selected, 0, 0, nodeId);
                Udp.broadcast(port, outgoing, PokemonProtocol.PACKET_SIZE);
                lastBroadcast = now;
            }
            if (TftTouchShield.readTouch() && TftTouchShield.touchY() >= 235) {
                waitForRelease();
                return false;
            }

            int length = Udp.receive(incoming, PokemonProtocol.PACKET_SIZE, source);
            if (!PokemonProtocol.valid(incoming, length) || PokemonProtocol.nodeId(incoming) == nodeId) {
                Delay.millis(10);
                continue;
            }
            int type = PokemonProtocol.unsigned(incoming[PokemonProtocol.TYPE]);
            int remotePokemon = PokemonProtocol.unsigned(incoming[PokemonProtocol.POKEMON]);
            int remoteNodeId = PokemonProtocol.nodeId(incoming);
            if (remotePokemon < PokemonProtocol.PIKACHU || remotePokemon > PokemonProtocol.SQUIRTLE) {
                continue;
            }

            if (type == PokemonProtocol.DISCOVER) {
                copyEndpoint(source, peer);
                PokemonProtocol.write(outgoing, PokemonProtocol.HERE, selected, 0, 0, nodeId);
                Udp.send(peer, peer[Udp.PORT], outgoing, PokemonProtocol.PACKET_SIZE);
            } else if (type == PokemonProtocol.HERE) {
                copyEndpoint(source, peer);
                opponent[0] = remotePokemon;
                opponent[1] = remoteNodeId;
                PokemonProtocol.write(outgoing, PokemonProtocol.START, selected, 0, 0, nodeId);
                sendRepeated(peer, outgoing);
                return true;
            } else if (type == PokemonProtocol.START) {
                copyEndpoint(source, peer);
                opponent[0] = remotePokemon;
                opponent[1] = remoteNodeId;
                return true;
            }
        }
    }

    private static void battle(int selected, int opponent, int nodeId, int opponentNodeId,
                               int[] peer, byte[] incoming, int[] source, byte[] outgoing) {
        int hitPoints = PokemonProtocol.hitPoints(selected);
        int opponentHitPoints = PokemonProtocol.hitPoints(opponent);
        int lastRemoteSequence = -1;
        int sequence = 1;
        boolean haveReply = false;
        int lastSent = 0;
        drawBattle(selected, opponent, hitPoints, opponentHitPoints);

        if (nodeId < opponentNodeId) {
            PokemonProtocol.write(outgoing, PokemonProtocol.ATTACK, selected, sequence,
                    PokemonProtocol.attack(selected), nodeId);
            Udp.send(peer, peer[Udp.PORT], outgoing, PokemonProtocol.PACKET_SIZE);
            lastSent = Clock.millis();
            haveReply = true;
            opponentHitPoints = opponentHitPoints - PokemonProtocol.damage(
                    PokemonProtocol.attack(selected), PokemonProtocol.defense(opponent));
            drawHealth(hitPoints, opponentHitPoints, "YOU ATTACK");
        } else {
            drawHealth(hitPoints, opponentHitPoints, "THEY ATTACK");
        }

        while (true) {
            int length = Udp.receive(incoming, PokemonProtocol.PACKET_SIZE, source);
            if (PokemonProtocol.valid(incoming, length) && samePeer(source, peer)
                    && PokemonProtocol.nodeId(incoming) == opponentNodeId) {
                int type = PokemonProtocol.unsigned(incoming[PokemonProtocol.TYPE]);
                int remoteSequence = PokemonProtocol.unsigned(incoming[PokemonProtocol.SEQUENCE]);
                if (type == PokemonProtocol.FAINTED) {
                    drawHealth(hitPoints, 0, "YOU WIN!");
                    return;
                }
                if (type == PokemonProtocol.ATTACK) {
                    if (remoteSequence != lastRemoteSequence) {
                        lastRemoteSequence = remoteSequence;
                        int power = PokemonProtocol.unsigned(incoming[PokemonProtocol.VALUE]);
                        hitPoints = hitPoints - PokemonProtocol.damage(power, PokemonProtocol.defense(selected));
                        if (hitPoints <= 0) {
                            PokemonProtocol.write(outgoing, PokemonProtocol.FAINTED, selected,
                                    remoteSequence, 0, nodeId);
                            sendRepeated(peer, outgoing);
                            drawHealth(0, opponentHitPoints, "YOU FAINTED");
                            return;
                        }
                        sequence = (remoteSequence + 1) & 0xff;
                        PokemonProtocol.write(outgoing, PokemonProtocol.ATTACK, selected, sequence,
                                PokemonProtocol.attack(selected), nodeId);
                        opponentHitPoints = opponentHitPoints - PokemonProtocol.damage(
                                PokemonProtocol.attack(selected), PokemonProtocol.defense(opponent));
                        if (opponentHitPoints < 0) {
                            opponentHitPoints = 0;
                        }
                        drawHealth(hitPoints, opponentHitPoints, "COUNTER ATTACK");
                        haveReply = true;
                    }
                    if (haveReply) {
                        Udp.send(peer, peer[Udp.PORT], outgoing, PokemonProtocol.PACKET_SIZE);
                        lastSent = Clock.millis();
                    }
                }
            }

            int now = Clock.millis();
            if (haveReply && now - lastSent >= RESEND_MILLIS) {
                Udp.send(peer, peer[Udp.PORT], outgoing, PokemonProtocol.PACKET_SIZE);
                lastSent = now;
            }
            Delay.millis(10);
        }
    }

    private static void drawSelection(int selected) {
        TftTouchShield.fillScreen(BACKGROUND);
        title("CHOOSE POKEMON");
        choice(65, PokemonProtocol.PIKACHU, selected);
        choice(115, PokemonProtocol.CHARMANDER, selected);
        choice(165, PokemonProtocol.SQUIRTLE, selected);
        TftTouchShield.fillRect(BUTTON_X, 235, BUTTON_WIDTH, 55, TftTouchShield.CYAN);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.CYAN);
        TftTouchShield.setCursor(62, 254);
        TftTouchShield.print("DISCOVER");
    }

    private static void choice(int y, int pokemon, int selected) {
        int color = pokemon == selected ? SELECTED : PANEL;
        TftTouchShield.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, color);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(42, y + 11);
        TftTouchShield.print(PokemonProtocol.name(pokemon));
    }

    private static void drawPortSelection(int port) {
        TftTouchShield.fillScreen(BACKGROUND);
        title("CHOOSE PORT");
        TftTouchShield.fillRect(BUTTON_X, 100, 60, 70, PANEL);
        TftTouchShield.fillRect(160, 100, 60, 70, PANEL);
        TftTouchShield.setTextSize(4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PANEL);
        TftTouchShield.setCursor(37, 117);
        TftTouchShield.print("-");
        TftTouchShield.setCursor(177, 117);
        TftTouchShield.print("+");
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        TftTouchShield.setCursor(84, 123);
        TftTouchShield.print(port);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BACKGROUND);
        TftTouchShield.setCursor(20, 190);
        TftTouchShield.print("Both boards must use the same port");
        TftTouchShield.fillRect(BUTTON_X, 235, BUTTON_WIDTH, 55, TftTouchShield.CYAN);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.CYAN);
        TftTouchShield.setCursor(62, 254);
        TftTouchShield.print("CONNECT");
    }

    private static void drawWaiting(int selected, int port) {
        TftTouchShield.fillScreen(BACKGROUND);
        title("DISCOVERING...");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        TftTouchShield.setCursor(42, 130);
        TftTouchShield.print(PokemonProtocol.name(selected));
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BACKGROUND);
        TftTouchShield.setCursor(35, 180);
        TftTouchShield.print("Waiting for another board");
        TftTouchShield.setCursor(35, 195);
        TftTouchShield.print("on UDP port ");
        TftTouchShield.print(port);
        TftTouchShield.fillRect(BUTTON_X, 235, BUTTON_WIDTH, 55, PANEL);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PANEL);
        TftTouchShield.setCursor(86, 254);
        TftTouchShield.print("BACK");
    }

    private static void drawBattle(int selected, int opponent, int hitPoints, int opponentHitPoints) {
        TftTouchShield.fillScreen(BACKGROUND);
        title("BATTLE!");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, BACKGROUND);
        TftTouchShield.setCursor(16, 74);
        TftTouchShield.print(PokemonProtocol.name(selected));
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, BACKGROUND);
        TftTouchShield.setCursor(16, 170);
        TftTouchShield.print(PokemonProtocol.name(opponent));
        drawHealth(hitPoints, opponentHitPoints, "READY");
    }

    private static void drawHealth(int hitPoints, int opponentHitPoints, String status) {
        TftTouchShield.fillRect(16, 101, 208, 36, PANEL);
        TftTouchShield.fillRect(16, 197, 208, 36, PANEL);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PANEL);
        TftTouchShield.setCursor(25, 111);
        TftTouchShield.print("HP ");
        TftTouchShield.print(hitPoints);
        TftTouchShield.setCursor(25, 207);
        TftTouchShield.print("HP ");
        TftTouchShield.print(opponentHitPoints);
        TftTouchShield.fillRect(0, 260, 240, 35, BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        TftTouchShield.setCursor(18, 270);
        TftTouchShield.print(status);
    }

    private static void showMessage(String message, int color) {
        TftTouchShield.fillScreen(BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, BACKGROUND);
        TftTouchShield.setCursor(22, 145);
        TftTouchShield.print(message);
    }

    private static void title(String text) {
        TftTouchShield.fillRect(0, 0, 240, 44, PANEL);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PANEL);
        TftTouchShield.setCursor(28, 15);
        TftTouchShield.print(text);
    }

    private static void sendRepeated(int[] peer, byte[] packet) {
        for (int i = 0; i < 3; i++) {
            Udp.send(peer, peer[Udp.PORT], packet, PokemonProtocol.PACKET_SIZE);
            Delay.millis(20);
        }
    }

    private static boolean samePeer(int[] endpoint, int[] peer) {
        return endpoint[0] == peer[0] && endpoint[1] == peer[1] && endpoint[2] == peer[2]
                && endpoint[3] == peer[3] && endpoint[Udp.PORT] == peer[Udp.PORT];
    }

    private static void copyEndpoint(int[] source, int[] target) {
        for (int i = 0; i < Udp.ENDPOINT_SIZE; i++) {
            target[i] = source[i];
        }
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
