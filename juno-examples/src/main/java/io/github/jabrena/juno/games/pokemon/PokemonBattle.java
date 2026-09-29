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
 * <p>Both an UNO R4 WiFi and an UNO Q run this same program. Each board connects with build-time
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}, lets its player select a Pokémon, and waits
 * when DISCOVER is tapped. The next board to join finds it through a small UDP broadcast handshake;
 * once both peers have exchanged their selections, the battle starts automatically.
 *
 * <p>The demo deliberately uses the raw, reusable {@link Udp} buffers: broadcast is discovery,
 * {@link Udp#send} is the producer, and {@link Udp#receive} is the consumer. Battle packets use an
 * alternating sequence number and resend timeout so a lost or duplicated UDP datagram does not
 * apply an attack twice or permanently stall the match.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class PokemonBattle {
    private static final int PORT = 5077;
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
        int[] localAddress = new int[4];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        int[] peer = new int[Udp.ENDPOINT_SIZE];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        connectWifi();
        Wifi.localIP(localAddress);
        while (!Udp.listen(PORT)) {
            showMessage("UDP ERROR - RETRY", TftTouchShield.RED);
            Delay.millis(1000);
        }

        while (true) {
            int selected = choosePokemon();
            int opponent = discover(selected, localAddress, peer, incoming, source, outgoing);
            battle(selected, opponent, localAddress, peer, incoming, source, outgoing);
            Delay.millis(3500);
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

    private static int discover(int selected, int[] localAddress, int[] peer, byte[] incoming,
                                int[] source, byte[] outgoing) {
        drawWaiting(selected);
        int lastBroadcast = Clock.millis() - DISCOVERY_INTERVAL_MILLIS;
        while (true) {
            int now = Clock.millis();
            if (now - lastBroadcast >= DISCOVERY_INTERVAL_MILLIS) {
                PokemonProtocol.write(outgoing, PokemonProtocol.DISCOVER, selected, 0, 0);
                Udp.broadcast(PORT, outgoing, PokemonProtocol.PACKET_SIZE);
                lastBroadcast = now;
            }

            int length = Udp.receive(incoming, PokemonProtocol.PACKET_SIZE, source);
            if (!PokemonProtocol.valid(incoming, length) || isLocal(source, localAddress)) {
                Delay.millis(10);
                continue;
            }
            int type = PokemonProtocol.unsigned(incoming[PokemonProtocol.TYPE]);
            int opponent = PokemonProtocol.unsigned(incoming[PokemonProtocol.POKEMON]);
            if (opponent < PokemonProtocol.PIKACHU || opponent > PokemonProtocol.SQUIRTLE) {
                continue;
            }

            if (type == PokemonProtocol.DISCOVER) {
                copyEndpoint(source, peer);
                PokemonProtocol.write(outgoing, PokemonProtocol.HERE, selected, 0, 0);
                Udp.send(peer, peer[Udp.PORT], outgoing, PokemonProtocol.PACKET_SIZE);
            } else if (type == PokemonProtocol.HERE) {
                copyEndpoint(source, peer);
                PokemonProtocol.write(outgoing, PokemonProtocol.START, selected, 0, 0);
                sendRepeated(peer, outgoing);
                return opponent;
            } else if (type == PokemonProtocol.START) {
                copyEndpoint(source, peer);
                return opponent;
            }
        }
    }

    private static void battle(int selected, int opponent, int[] localAddress, int[] peer,
                               byte[] incoming, int[] source, byte[] outgoing) {
        int hitPoints = PokemonProtocol.hitPoints(selected);
        int opponentHitPoints = PokemonProtocol.hitPoints(opponent);
        int lastRemoteSequence = -1;
        int sequence = 1;
        boolean haveReply = false;
        int lastSent = 0;
        drawBattle(selected, opponent, hitPoints, opponentHitPoints);

        if (compareAddress(localAddress, peer) < 0) {
            PokemonProtocol.write(outgoing, PokemonProtocol.ATTACK, selected, sequence,
                    PokemonProtocol.attack(selected));
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
            if (PokemonProtocol.valid(incoming, length) && samePeer(source, peer)) {
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
                            PokemonProtocol.write(outgoing, PokemonProtocol.FAINTED, selected, remoteSequence, 0);
                            sendRepeated(peer, outgoing);
                            drawHealth(0, opponentHitPoints, "YOU FAINTED");
                            return;
                        }
                        sequence = (remoteSequence + 1) & 0xff;
                        PokemonProtocol.write(outgoing, PokemonProtocol.ATTACK, selected, sequence,
                                PokemonProtocol.attack(selected));
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

    private static void drawWaiting(int selected) {
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

    private static boolean isLocal(int[] endpoint, int[] localAddress) {
        return endpoint[0] == localAddress[0] && endpoint[1] == localAddress[1]
                && endpoint[2] == localAddress[2] && endpoint[3] == localAddress[3];
    }

    private static boolean samePeer(int[] endpoint, int[] peer) {
        return endpoint[0] == peer[0] && endpoint[1] == peer[1] && endpoint[2] == peer[2]
                && endpoint[3] == peer[3] && endpoint[Udp.PORT] == peer[Udp.PORT];
    }

    private static int compareAddress(int[] localAddress, int[] peer) {
        for (int i = 0; i < 4; i++) {
            if (localAddress[i] < peer[i]) {
                return -1;
            }
            if (localAddress[i] > peer[i]) {
                return 1;
            }
        }
        return 0;
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
