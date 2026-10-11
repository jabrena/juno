package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * A gladiator's board: it plays one gladiator and shows the battle through its eyes.
 *
 * <p>It broadcasts {@code JOIN} until the arena answers with the map, the only time the map travels. Then, 25 times
 * a second, the CPU behaviour or the HUMAN at the touch screen moves the gladiator, and ten times a second its
 * position, facing and strike counter go to the arena in a {@code STATE} packet (at once after a swing). The arena's
 * {@code WORLD} packets ({@link Report}) bring the round, both healths and, only while this gladiator's eyes see it,
 * where the opponent stands. A new round sends it back to its gate. Silence from the arena for three seconds starts
 * the search for it again.
 *
 * <p>The left and right thirds of the view turn a HUMAN gladiator, the upper middle walks and the lower middle swings
 * the gladius. CPU/HUMAN in the status bar switches between the two at any time; MENU returns to the role menu.
 */
final class FighterFlow {
    private static final int FRAME_MILLIS = 40;
    private static final int JOIN_MILLIS = 1000;
    private static final int STATE_MILLIS = 100;
    private static final int LOST_MILLIS = 3000;
    private static final int SWING_MILLIS = 700;
    private static final int SWING_FRAMES = 6;
    private static final int HURT_FRAMES = 4;
    private static final int THRUST_FRAMES = 6;
    private static final int PACKETS_PER_FRAME = 6;

    private static final int SEARCHING_ARENA = 0;
    private static final int ARENA_FULL = 1;
    private static final int IN_ARENA = 2;

    private static int nodeId;
    private static int screen;
    private static int slot;
    private static int heardAt;
    private static boolean human;
    private static int strikes;
    private static int swungAt;
    private static int swing;
    private static int hurt;
    private static int thrust;
    private static boolean touching;
    /** Whether the view shows the arena, rather than the wait for an opponent. */
    private static boolean viewing;

    private FighterFlow() {
    }

    /** Fights as a {@code behaviour} gladiator until MENU is tapped. */
    static void run(int behaviour, byte[] incoming, byte[] outgoing, int[] source) {
        int[] arena = new int[Udp.ENDPOINT_SIZE];
        short[] lines = ViewList.lines;
        byte[] changes = ViewList.changes;
        Body body = new Body();
        Brain brain = new Brain(behaviour);
        nodeId = Clock.micros();
        if (nodeId == 0) {
            nodeId = 1;
        }
        // In pieces: a String built at runtime holds at most 31 characters on the boards.
        Serial.print("Role: ");
        Serial.print(Figure.name(behaviour));
        Serial.print(" gladiator, node ");
        Serial.println(nodeId);
        human = false;
        touching = true;
        searchArena();

        int lastJoin = Clock.millis() - JOIN_MILLIS;
        int lastState = 0;
        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                next = Clock.millis();
            }
            int now = Clock.millis();
            receive(incoming, source, arena, body, brain, now);
            if (screen != IN_ARENA) {
                if (now - lastJoin >= JOIN_MILLIS) {
                    GladiatorProtocol.begin(outgoing, GladiatorProtocol.JOIN, 0, nodeId);
                    outgoing[GladiatorProtocol.BEHAVIOUR] = (byte) behaviour;
                    Udp.broadcast(Gladiator.PORT, outgoing, GladiatorProtocol.PACKET_SIZE);
                    lastJoin = now;
                }
                if (menuTapped()) {
                    return;
                }
                continue;
            }
            if (now - heardAt > LOST_MILLIS) {
                Serial.println("Arena lost");
                searchArena();
                continue;
            }
            int action = touch(body);
            if (action < 0) {
                return;
            }
            boolean swung = Report.phase == Match.FIGHT && fight(body, brain, action == 1, now);
            swing = Math.max(0, swing - 1);
            hurt = Math.max(0, hurt - 1);
            thrust = Math.max(0, thrust - 1);
            if (swung || now - lastState >= STATE_MILLIS) {
                sendState(outgoing, arena, body, brain, behaviour);
                lastState = now;
            }
            show(lines, changes, body, brain);
            Hud.own(behaviour, Report.health, Report.wins);
            Hud.opponent(Report.opponentPresent, Report.opponentKind, Report.opponentHealth, Report.opponentWins);
            Hud.pilot(human);
        }
    }

    /** This frame's move, by the CPU or the HUMAN's {@code swingTapped}; returns whether the gladius was swung. */
    private static boolean fight(Body body, Brain brain, boolean swingTapped, int now) {
        body.opponentInSight = Report.visible;
        body.opponentX = Report.opponentX;
        body.opponentY = Report.opponentY;
        if (human) {
            return swingTapped && swingGladius(now);
        }
        brain.see(Report.visible, Report.opponentX, Report.opponentY, now);
        brain.drive(body, now);
        return brain.strike && swingGladius(now);
    }

    private static void show(short[] lines, byte[] changes, Body body, Brain brain) {
        if (Report.phase == Match.WAITING) {
            if (viewing) {
                viewing = false;
                ViewList.clearView();
                Hud.centered("WAITING FOR AN OPPONENT", 92, 1, TftTouchShield.YELLOW, ViewList.BACKGROUND);
            }
            Hud.status(0, "WAITING", TftTouchShield.GRAY);
            return;
        }
        if (!viewing) {
            viewing = true;
            ViewList.clearView();
        }
        View.build(lines, body, Report.visible, Report.opponentX, Report.opponentY, Report.opponentAngle,
                Report.opponentKind, thrust > 0, swing, hurt);
        ViewList.present(lines, changes);
        Hud.fighterStatus(slot, human, brain.mode, hurt > 0);
    }

    private static void searchArena() {
        screen = SEARCHING_ARENA;
        Hud.message("FINDING ARENA", "Waiting for a board in the ARENA role", TftTouchShield.YELLOW);
    }

    private static void receive(byte[] incoming, int[] source, int[] arena, Body body, Brain brain, int now) {
        for (int i = 0; i < PACKETS_PER_FRAME; i++) {
            int length = Udp.receive(incoming, GladiatorProtocol.PACKET_SIZE, source);
            if (length <= 0) {
                return;
            }
            if (!GladiatorProtocol.valid(incoming, length) || GladiatorProtocol.nodeId(incoming) != nodeId) {
                continue;
            }
            int type = GladiatorProtocol.type(incoming);
            if (type == GladiatorProtocol.MAP && screen != IN_ARENA && GladiatorProtocol.readMap(incoming)) {
                for (int k = 0; k < Udp.ENDPOINT_SIZE; k++) {
                    arena[k] = source[k];
                }
                slot = GladiatorProtocol.slot(incoming);
                screen = IN_ARENA;
                heardAt = now;
                Report.clear();
                Serial.print("Map received: " + Coliseum.pillars + " pillars, ");
                Serial.println(slot == 0 ? "left gate" : "right gate");
                Hud.bar();
                viewing = true;
            } else if (type == GladiatorProtocol.FULL && screen == SEARCHING_ARENA) {
                screen = ARENA_FULL;
                Hud.message("ARENA FULL", "Two gladiators are fighting; waiting", TftTouchShield.ORANGE);
            } else if (type == GladiatorProtocol.WORLD && screen == IN_ARENA) {
                heardAt = now;
                react(Report.read(incoming), body, brain, now);
            }
        }
    }

    /** A new round puts the gladiator back at its gate; a blow and the opponent's swing show in the view. */
    private static void react(int events, Body body, Brain brain, int now) {
        if ((events & Report.NEW_ROUND) != 0) {
            body.spawn(slot);
            brain.reset(slot, now);
            swing = 0;
            hurt = 0;
            thrust = 0;
        }
        if ((events & Report.STRUCK) != 0) {
            hurt = Report.surprised ? 2 * HURT_FRAMES : HURT_FRAMES;
            brain.hurt(now);
            Serial.println((Report.surprised ? "Struck unseen! HP " : "Struck! HP ") + Report.health);
        }
        if ((events & Report.OPPONENT_SWUNG) != 0) {
            thrust = THRUST_FRAMES;
        }
    }

    private static boolean swingGladius(int now) {
        if (now - swungAt < SWING_MILLIS) {
            return false;
        }
        swungAt = now;
        strikes = (strikes + 1) & 0xFF;
        swing = SWING_FRAMES;
        return true;
    }

    private static void sendState(byte[] packet, int[] arena, Body body, Brain brain, int behaviour) {
        GladiatorProtocol.begin(packet, GladiatorProtocol.STATE, slot, nodeId);
        packet[GladiatorProtocol.STATE_ROUND] = (byte) Report.round;
        packet[GladiatorProtocol.STATE_BEHAVIOUR] = (byte) behaviour;
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.X, Math.round(body.x));
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.Y, Math.round(body.y));
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(body.angle));
        packet[GladiatorProtocol.STRIKES] = (byte) strikes;
        packet[GladiatorProtocol.MODE] = (byte) (Report.phase == Match.FIGHT ? brain.mode : Brain.WAITING);
        packet[GladiatorProtocol.HUMAN] = (byte) (human ? 1 : 0);
        Udp.send(arena, arena[Udp.PORT], packet, GladiatorProtocol.PACKET_SIZE);
    }

    /**
     * This frame's touch: -1 when MENU was tapped, 1 when a HUMAN swings, 0 otherwise. A HUMAN gladiator turns and
     * walks here.
     */
    private static int touch(Body body) {
        if (!TftTouchShield.readTouch()) {
            touching = false;
            return 0;
        }
        int x = TftTouchShield.touchX();
        int y = TftTouchShield.touchY();
        boolean tap = !touching;
        touching = true;
        if (Hud.onMenu(x, y)) {
            return tap ? -1 : 0;
        }
        if (Hud.onPilot(x, y)) {
            human = tap != human;
            return 0;
        }
        if (!human || Report.phase != Match.FIGHT || y >= ViewList.VIEW_BOTTOM) {
            return 0;
        }
        if (x < ViewList.WIDTH / 3) {
            body.turn(Brain.TURN);
        } else if (x >= 2 * ViewList.WIDTH / 3) {
            body.turn(-Brain.TURN);
        } else if (y < View.CY) {
            body.walk(Brain.WALK);
        } else {
            return 1;
        }
        return 0;
    }

    private static boolean menuTapped() {
        if (!TftTouchShield.readTouch()) {
            touching = false;
            return false;
        }
        boolean tap = !touching;
        touching = true;
        return tap && TftTouchShield.touchY() >= ViewList.VIEW_BOTTOM;
    }
}
