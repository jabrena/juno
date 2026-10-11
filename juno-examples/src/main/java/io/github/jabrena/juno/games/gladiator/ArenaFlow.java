package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.tft.TftTouchShield;

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
final class ArenaFlow {
    private static final int WORLD_MILLIS = 100;
    private static final int PACKETS_PER_PASS = 8;

    private ArenaFlow() {
    }

    /** Keeps the arena until MENU is tapped. */
    static void run(byte[] incoming, byte[] outgoing, int[] source) {
        Serial.println("Role: arena");
        Coliseum.build();
        Match match = new Match();
        ArenaView.drawAll();
        int lastWorld = 0;
        int landed = 0;
        int phase = Match.WAITING;
        boolean touching = true;
        while (true) {
            if (TftTouchShield.readTouch()) {
                if (!touching && ArenaView.menuTapped(TftTouchShield.touchX(), TftTouchShield.touchY())) {
                    return;
                }
                touching = true;
            } else {
                touching = false;
            }
            int now = Clock.millis();
            for (int i = 0; i < PACKETS_PER_PASS; i++) {
                int length = Udp.receive(incoming, GladiatorProtocol.PACKET_SIZE, source);
                if (length <= 0) {
                    break;
                }
                if (GladiatorProtocol.valid(incoming, length)) {
                    serve(match, incoming, source, outgoing, now);
                }
            }
            match.tick(now);
            if (match.landed != landed) {
                landed = match.landed;
                logHit(match);
            }
            if (match.phase != phase) {
                phase = match.phase;
                logPhase(match);
            }
            if (now - lastWorld >= WORLD_MILLIS) {
                lastWorld = now;
                for (int slot = 0; slot < 2; slot++) {
                    if (match.present[slot]) {
                        match.writeWorld(outgoing, slot, now);
                        int[] endpoint = match.endpoint(slot);
                        Udp.send(endpoint, endpoint[Udp.PORT], outgoing, GladiatorProtocol.PACKET_SIZE);
                    }
                }
                ArenaView.update(match, now);
            }
            Delay.millis(2);
        }
    }

    private static void serve(Match match, byte[] packet, int[] source, byte[] reply, int now) {
        int type = GladiatorProtocol.type(packet);
        int node = GladiatorProtocol.nodeId(packet);
        if (type == GladiatorProtocol.JOIN) {
            boolean known = match.slotOf(node) >= 0;
            int kind = GladiatorProtocol.unsigned(packet, GladiatorProtocol.BEHAVIOUR);
            int slot = match.join(node, kind == Brain.AMBUSH ? Brain.AMBUSH : Brain.AGGRESSIVE, source, now);
            if (slot < 0) {
                GladiatorProtocol.begin(reply, GladiatorProtocol.FULL, 0, node);
            } else {
                if (!known) {
                    Serial.print("JOIN " + side(slot) + " ");
                    Serial.print(Figure.name(match.behaviour[slot]));
                    Serial.print(" node ");
                    Serial.println(node);
                }
                GladiatorProtocol.writeMap(reply, slot, node);
            }
            Udp.send(source, source[Udp.PORT], reply, GladiatorProtocol.PACKET_SIZE);
        } else if (type == GladiatorProtocol.STATE) {
            int slot = match.slotOf(node);
            if (slot >= 0) {
                match.state(slot, packet, now);
            }
        }
    }

    private static void logHit(Match match) {
        int attacker = match.lastAttacker;
        int victim = 1 - attacker;
        Serial.print("HIT " + side(attacker) + " ");
        Serial.print(Figure.name(match.behaviour[attacker]));
        Serial.print(" strikes " + side(victim));
        Serial.print(" for " + match.lastDamage);
        Serial.print(match.surprised[victim] ? " (unseen)" : "");
        Serial.println(", HP " + match.health[victim]);
    }

    private static void logPhase(Match match) {
        if (match.phase == Match.FIGHT) {
            Serial.println("ROUND " + match.round + ": FIGHT");
        } else if (match.phase == Match.OVER) {
            Serial.print("ROUND " + match.round + ": " + side(match.winner) + " ");
            Serial.print(Figure.name(match.behaviour[match.winner]));
            Serial.println(" wins (" + match.wins[0] + " - " + match.wins[1] + ")");
        } else if (match.phase == Match.WAITING) {
            Serial.println("Waiting for gladiators");
        }
    }

    private static String side(int slot) {
        return slot == 0 ? "left" : "right";
    }
}
