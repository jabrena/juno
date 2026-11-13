package io.github.jabrena.juno.usecases.gladiator.screen;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.gladiator.arena.Coliseum;
import io.github.jabrena.juno.usecases.gladiator.arena.ColiseumMap;
import io.github.jabrena.juno.usecases.gladiator.arena.Match;
import io.github.jabrena.juno.usecases.gladiator.arena.Side;
import io.github.jabrena.juno.usecases.gladiator.gladiator.Brain;
import io.github.jabrena.juno.usecases.gladiator.net.GameSocket;
import io.github.jabrena.juno.usecases.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.usecases.gladiator.view.ArenaView;
import io.github.jabrena.juno.usecases.gladiator.view.Figure;
import io.github.jabrena.juno.util.Delay;

/**
 * The ARENA role: builds the {@link Coliseum}, keeps the {@link Match} and shows it from above ({@link ArenaView}).
 * Meant for the most powerful board on the network, an UNO Q, one per network.
 *
 * <p>Log lines are printed in pieces: a {@code String} built at runtime holds at most 31 characters on the boards.
 *
 * <p>Gladiators find it by broadcasting {@code JOIN}; each is answered once with the map and its gate, or told the
 * arena is full. Ten times a second every gladiator gets a {@code WORLD} packet with only what it may know. The
 * battle starts as soon as the second gladiator arrives. Joins, hits and rounds are logged over serial at 115200
 * baud:
 *
 * <pre>
 * JOIN left AGGRESSIVE node 48211377
 * ROUND 2: FIGHT
 * HIT right AMBUSH strikes left for 20 (unseen), HP 60
 * ROUND 2: right AMBUSH wins (1 - 1)
 * </pre>
 */
public final class ArenaFlow {
    private static final Clock CLOCK = MCU.getClock();
    private static final int WORLD_MILLIS = 100;
    private static final int PACKETS_PER_PASS = 8;

    private ArenaFlow() {
    }

    /** Keeps the arena until MENU is tapped. */
    public static void run(TftTouchShield tft, GameSocket link) {
        Serial.println("Role: arena");
        Coliseum coliseum = new Coliseum();
        coliseum.build();
        Match match = new Match(coliseum);
        ArenaView view = new ArenaView(coliseum);
        view.drawAll(tft);
        int lastWorld = 0;
        int landed = 0;
        int phase = Match.WAITING;
        boolean touching = true;
        while (true) {
            if (tft.readTouch()) {
                if (!touching && ArenaView.menuTapped(tft.touchX(), tft.touchY())) {
                    return;
                }
                touching = true;
            } else {
                touching = false;
            }
            int now = CLOCK.millis();
            for (int i = 0; i < PACKETS_PER_PASS; i++) {
                int length = link.receive();
                if (length <= 0) {
                    break;
                }
                if (GladiatorProtocol.valid(link.incoming(), length)) {
                    serve(match, link, now);
                }
            }
            match.tick(now);
            if (match.landed() != landed) {
                landed = match.landed();
                logHit(match);
            }
            if (match.phase() != phase) {
                phase = match.phase();
                logPhase(match);
            }
            if (now - lastWorld >= WORLD_MILLIS) {
                lastWorld = now;
                for (int slot = 0; slot < 2; slot++) {
                    if (match.side(slot).present()) {
                        match.writeWorld(link.outgoing(), slot, now);
                        Side side = match.side(slot);
                        link.send(side.address(), side.port(), GladiatorProtocol.PACKET_SIZE);
                    }
                }
                view.update(tft, match, now);
            }
            Delay.millis(2);
        }
    }

    private static void serve(Match match, GameSocket link, int now) {
        byte[] packet = link.incoming();
        byte[] reply = link.outgoing();
        int type = GladiatorProtocol.type(packet);
        int node = GladiatorProtocol.nodeId(packet);
        if (type == GladiatorProtocol.JOIN) {
            boolean known = match.slotOf(node) >= 0;
            int kind = GladiatorProtocol.unsigned(packet, GladiatorProtocol.BEHAVIOUR);
            int slot = match.join(node, kind == Brain.AMBUSH ? Brain.AMBUSH : Brain.AGGRESSIVE, link.sender(),
                    link.senderPort(), now);
            if (slot < 0) {
                GladiatorProtocol.begin(reply, GladiatorProtocol.FULL, 0, node);
            } else {
                if (!known) {
                    Serial.print("JOIN " + side(slot) + " ");
                    Serial.print(Figure.name(match.side(slot).behaviour()));
                    Serial.print(" node ");
                    Serial.println(node);
                }
                ColiseumMap.write(reply, slot, node, match.coliseum());
            }
            link.send(link.sender(), link.senderPort(), GladiatorProtocol.PACKET_SIZE);
        } else if (type == GladiatorProtocol.STATE) {
            int slot = match.slotOf(node);
            if (slot >= 0) {
                match.state(slot, packet, now);
            }
        }
    }

    private static void logHit(Match match) {
        int attacker = match.lastAttacker();
        int victim = 1 - attacker;
        if (match.lastByLion()) {
            Serial.print("LION mauls " + side(victim));
            Serial.println(" for " + match.lastDamage() + ", HP " + match.side(victim).health());
            return;
        }
        Serial.print("HIT " + side(attacker) + " ");
        Serial.print(Figure.name(match.side(attacker).behaviour()));
        Serial.print(" strikes " + side(victim));
        Serial.print(" for " + match.lastDamage());
        Serial.print(match.side(victim).surprised() ? " (unseen)" : "");
        Serial.println(", HP " + match.side(victim).health());
    }

    private static void logPhase(Match match) {
        if (match.phase() == Match.FIGHT) {
            Serial.println("ROUND " + match.round() + ": FIGHT");
        } else if (match.phase() == Match.OVER) {
            Serial.print("ROUND " + match.round() + ": " + side(match.winner()) + " ");
            Serial.print(Figure.name(match.side(match.winner()).behaviour()));
            Serial.println(" wins (" + match.side(0).wins() + " - " + match.side(1).wins() + ")");
        } else if (match.phase() == Match.WAITING) {
            Serial.println("Waiting for gladiators");
        }
    }

    private static String side(int slot) {
        return slot == 0 ? "left" : "right";
    }
}
