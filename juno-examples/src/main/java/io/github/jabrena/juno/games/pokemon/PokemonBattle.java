package io.github.jabrena.juno.games.pokemon;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.games.net.GameSocket;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.net.InetAddress;

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
 * <p>The demo uses one {@link GameSocket} with its reusable packet buffers: broadcast is discovery,
 * {@link GameSocket#send} is the producer, and {@link GameSocket#receive} is the consumer. Battle packets use an
 * alternating sequence number and resend timeout so a lost or duplicated UDP datagram does not
 * apply an attack twice or permanently stall the match.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class PokemonBattle {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int DISCOVERY_INTERVAL_MILLIS = 700;
    private static final int RESEND_MILLIS = 1000;

    static final int BACKGROUND = 0x0841;
    static final int PANEL = 0x18C3;
    private static final int SELECTED = 0x0320;
    static final int BUTTON_X = 20;
    static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 40;

    private PokemonBattle() {
    }

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        Peer peer = new Peer();
        int[] opponent = new int[2];

        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        connectWifi(tft);
        int nodeId = CLOCK.micros();
        if (nodeId == 0) {
            nodeId = 1;
        }

        int port = PortSelector.FIRST_PORT;
        while (true) {
            GameSocket link = PortSelector.choose(tft, port);
            port = link.localPort();
            boolean connected = true;
            while (connected) {
                int selected = choosePokemon(tft);
                if (!discover(tft, link, selected, nodeId, peer, opponent)) {
                    connected = false;
                    link.close();
                } else {
                    battle(tft, link, selected, opponent[0], nodeId, opponent[1], peer);
                    Delay.millis(3500);
                }
            }
        }
    }

    private static void connectWifi(TftTouchShield tft) {
        showMessage(tft, "CONNECTING WIFI", TftTouchShield.CYAN);
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        showMessage(tft, "WIFI CONNECTED", TftTouchShield.GREEN);
        Delay.millis(800);
    }

    private static int choosePokemon(TftTouchShield tft) {
        int selected = PokemonProtocol.PIKACHU;
        drawSelection(tft, selected);
        while (true) {
            if (!tft.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int y = tft.touchY();
            waitForRelease(tft);
            if (y >= 65 && y < 105) {
                selected = PokemonProtocol.PIKACHU;
                drawSelection(tft, selected);
            } else if (y >= 115 && y < 155) {
                selected = PokemonProtocol.CHARMANDER;
                drawSelection(tft, selected);
            } else if (y >= 165 && y < 205) {
                selected = PokemonProtocol.SQUIRTLE;
                drawSelection(tft, selected);
            } else if (y >= 235 && y < 290) {
                return selected;
            }
        }
    }

    /** Returns true once a peer is found, false if the player taps BACK to choose another port. */
    private static boolean discover(TftTouchShield tft, GameSocket link, int selected, int nodeId, Peer peer,
                                    int[] opponent) {
        drawWaiting(tft, selected, link.localPort());
        int lastBroadcast = CLOCK.millis() - DISCOVERY_INTERVAL_MILLIS;
        byte[] outgoing = link.outgoing();
        while (true) {
            int now = CLOCK.millis();
            if (now - lastBroadcast >= DISCOVERY_INTERVAL_MILLIS) {
                PokemonProtocol.write(outgoing, PokemonProtocol.DISCOVER, selected, 0, 0, nodeId);
                link.broadcast(link.localPort(), PokemonProtocol.PACKET_SIZE);
                lastBroadcast = now;
            }
            if (tft.readTouch() && tft.touchY() >= 235) {
                waitForRelease(tft);
                return false;
            }

            int length = link.receive();
            byte[] incoming = link.incoming();
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
                peer.hear(link);
                PokemonProtocol.write(outgoing, PokemonProtocol.HERE, selected, 0, 0, nodeId);
                link.send(peer.address, peer.port, PokemonProtocol.PACKET_SIZE);
            } else if (type == PokemonProtocol.HERE) {
                peer.hear(link);
                opponent[0] = remotePokemon;
                opponent[1] = remoteNodeId;
                PokemonProtocol.write(outgoing, PokemonProtocol.START, selected, 0, 0, nodeId);
                sendRepeated(link, peer);
                return true;
            } else if (type == PokemonProtocol.START) {
                peer.hear(link);
                opponent[0] = remotePokemon;
                opponent[1] = remoteNodeId;
                return true;
            }
        }
    }

    private static void battle(TftTouchShield tft, GameSocket link, int selected, int opponent, int nodeId,
                               int opponentNodeId, Peer peer) {
        int hitPoints = PokemonProtocol.hitPoints(selected);
        int opponentHitPoints = PokemonProtocol.hitPoints(opponent);
        int lastRemoteSequence = -1;
        int sequence = 1;
        boolean haveReply = false;
        int lastSent = 0;
        byte[] outgoing = link.outgoing();
        drawBattle(tft, selected, opponent, hitPoints, opponentHitPoints);

        if (nodeId < opponentNodeId) {
            PokemonProtocol.write(outgoing, PokemonProtocol.ATTACK, selected, sequence,
                    PokemonProtocol.attack(selected), nodeId);
            link.send(peer.address, peer.port, PokemonProtocol.PACKET_SIZE);
            lastSent = CLOCK.millis();
            haveReply = true;
            opponentHitPoints = opponentHitPoints - PokemonProtocol.damage(
                    PokemonProtocol.attack(selected), PokemonProtocol.defense(opponent));
            drawHealth(tft, hitPoints, opponentHitPoints, "YOU ATTACK");
        } else {
            drawHealth(tft, hitPoints, opponentHitPoints, "THEY ATTACK");
        }

        while (true) {
            int length = link.receive();
            byte[] incoming = link.incoming();
            if (PokemonProtocol.valid(incoming, length) && peer.sentBy(link)
                    && PokemonProtocol.nodeId(incoming) == opponentNodeId) {
                int type = PokemonProtocol.unsigned(incoming[PokemonProtocol.TYPE]);
                int remoteSequence = PokemonProtocol.unsigned(incoming[PokemonProtocol.SEQUENCE]);
                if (type == PokemonProtocol.FAINTED) {
                    drawHealth(tft, hitPoints, 0, "YOU WIN!");
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
                            sendRepeated(link, peer);
                            drawHealth(tft, 0, opponentHitPoints, "YOU FAINTED");
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
                        drawHealth(tft, hitPoints, opponentHitPoints, "COUNTER ATTACK");
                        haveReply = true;
                    }
                    if (haveReply) {
                        link.send(peer.address, peer.port, PokemonProtocol.PACKET_SIZE);
                        lastSent = CLOCK.millis();
                    }
                }
            }

            int now = CLOCK.millis();
            if (haveReply && now - lastSent >= RESEND_MILLIS) {
                link.send(peer.address, peer.port, PokemonProtocol.PACKET_SIZE);
                lastSent = now;
            }
            Delay.millis(10);
        }
    }

    private static void drawSelection(TftTouchShield tft, int selected) {
        tft.fillScreen(BACKGROUND);
        title(tft, "CHOOSE POKEMON");
        choice(tft, 65, PokemonProtocol.PIKACHU, selected);
        choice(tft, 115, PokemonProtocol.CHARMANDER, selected);
        choice(tft, 165, PokemonProtocol.SQUIRTLE, selected);
        tft.fillRect(BUTTON_X, 235, BUTTON_WIDTH, 55, TftTouchShield.CYAN);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.BLACK, TftTouchShield.CYAN);
        tft.setCursor(62, 254);
        tft.print("DISCOVER");
    }

    private static void choice(TftTouchShield tft, int y, int pokemon, int selected) {
        int color = pokemon == selected ? SELECTED : PANEL;
        tft.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, color);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, color);
        tft.setCursor(42, y + 11);
        tft.print(PokemonProtocol.name(pokemon));
    }

    private static void drawWaiting(TftTouchShield tft, int selected, int port) {
        tft.fillScreen(BACKGROUND);
        title(tft, "DISCOVERING...");
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        tft.setCursor(42, 130);
        tft.print(PokemonProtocol.name(selected));
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, BACKGROUND);
        tft.setCursor(35, 180);
        tft.print("Waiting for another board");
        tft.setCursor(35, 195);
        tft.print("on UDP port ");
        tft.print(port);
        tft.fillRect(BUTTON_X, 235, BUTTON_WIDTH, 55, PANEL);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, PANEL);
        tft.setCursor(86, 254);
        tft.print("BACK");
    }

    private static void drawBattle(TftTouchShield tft, int selected, int opponent, int hitPoints, int opponentHitPoints) {
        tft.fillScreen(BACKGROUND);
        title(tft, "BATTLE!");
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.CYAN, BACKGROUND);
        tft.setCursor(16, 74);
        tft.print(PokemonProtocol.name(selected));
        tft.setTextColor(TftTouchShield.ORANGE, BACKGROUND);
        tft.setCursor(16, 170);
        tft.print(PokemonProtocol.name(opponent));
        drawHealth(tft, hitPoints, opponentHitPoints, "READY");
    }

    private static void drawHealth(TftTouchShield tft, int hitPoints, int opponentHitPoints, String status) {
        tft.fillRect(16, 101, 208, 36, PANEL);
        tft.fillRect(16, 197, 208, 36, PANEL);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, PANEL);
        tft.setCursor(25, 111);
        tft.print("HP ");
        tft.print(hitPoints);
        tft.setCursor(25, 207);
        tft.print("HP ");
        tft.print(opponentHitPoints);
        tft.fillRect(0, 260, 240, 35, BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.YELLOW, BACKGROUND);
        tft.setCursor(18, 270);
        tft.print(status);
    }

    static void showMessage(TftTouchShield tft, String message, int color) {
        tft.fillScreen(BACKGROUND);
        tft.setTextSize(2);
        tft.setTextColor(color, BACKGROUND);
        tft.setCursor(22, 145);
        tft.print(message);
    }

    static void title(TftTouchShield tft, String text) {
        tft.fillRect(0, 0, 240, 44, PANEL);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, PANEL);
        tft.setCursor(28, 15);
        tft.print(text);
    }

    private static void sendRepeated(GameSocket link, Peer peer) {
        for (int i = 0; i < 3; i++) {
            link.send(peer.address, peer.port, PokemonProtocol.PACKET_SIZE);
            Delay.millis(20);
        }
    }

    /** Where the opponent's packets come from, kept to answer them. */
    private static final class Peer {
        private InetAddress address;
        private int port;

        /** Remembers the sender of the datagram {@code link} just received. */
        void hear(GameSocket link) {
            address = link.sender();
            port = link.senderPort();
        }

        /** Whether the datagram {@code link} just received came from this peer. */
        boolean sentBy(GameSocket link) {
            return address != null && port == link.senderPort() && address.equals(link.sender());
        }
    }

    static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
