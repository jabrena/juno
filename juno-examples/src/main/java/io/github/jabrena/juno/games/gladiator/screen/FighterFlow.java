package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;
import io.github.jabrena.juno.games.gladiator.arena.Arms;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.ColiseumMap;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.arena.Javelins;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.games.gladiator.gladiator.Senses;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.games.gladiator.view.Figure;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;

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
public final class FighterFlow {
    private static final Clock CLOCK = MCU.getClock();
    private static final int FRAME_MILLIS = 40;
    private static final int JOIN_MILLIS = 1000;
    private static final int STATE_MILLIS = 100;
    private static final int LOST_MILLIS = 3000;
    private static final int SWING_FRAMES = 6;
    private static final int HURT_FRAMES = 4;
    private static final int THRUST_FRAMES = 6;
    private static final int PACKETS_PER_FRAME = 6;

    private static final int SEARCHING_ARENA = 0;
    private static final int ARENA_FULL = 1;
    private static final int IN_ARENA = 2;

    private final Coliseum coliseum = new Coliseum();
    private final Report report = new Report(coliseum);
    private final Body body = new Body(coliseum);
    private final View view;
    private final Hud hud;
    private final int[] arena = new int[Udp.ENDPOINT_SIZE];
    private final int behaviour;
    /** The class the network game gives each fighting style: a relentless SECUTOR, or a RETIARIUS with its net. */
    private final int kind;
    private final Brain brain;
    private int nodeId;
    private int screen;
    private int slot;
    private int heardAt;
    private boolean human;
    private int strikes;
    private int throwsMade;
    private int netsCast;
    private int swungAt;
    private int swing;
    private int hurt;
    private int thrust;
    private boolean touching;
    /** Whether the view shows the arena, rather than the wait for an opponent. */
    private boolean viewing;

    /** A {@code style} gladiator drawn into {@code list}. */
    public FighterFlow(ViewList list, int style) {
        view = new View(list, coliseum, report);
        hud = new Hud(list);
        behaviour = style;
        kind = style == Brain.AMBUSH ? GladiatorClass.RETIARIUS : GladiatorClass.SECUTOR;
        brain = new Brain(style, kind);
    }

    /** Fights until MENU is tapped. */
    public void run(byte[] incoming, byte[] outgoing, int[] source) {
        nodeId = CLOCK.micros();
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

        int lastJoin = CLOCK.millis() - JOIN_MILLIS;
        int lastState = 0;
        int next = CLOCK.millis();
        while (true) {
            while (CLOCK.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (CLOCK.millis() - next > 4 * FRAME_MILLIS) {
                next = CLOCK.millis();
            }
            int now = CLOCK.millis();
            receive(incoming, source, now);
            if (screen != IN_ARENA) {
                if (now - lastJoin >= JOIN_MILLIS) {
                    GladiatorProtocol.begin(outgoing, GladiatorProtocol.JOIN, 0, nodeId);
                    outgoing[GladiatorProtocol.BEHAVIOUR] = (byte) behaviour;
                    Udp.broadcast(GladiatorProtocol.PORT, outgoing, GladiatorProtocol.PACKET_SIZE);
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
            int action = touch();
            if (action < 0) {
                return;
            }
            boolean swung = report.phase() == Match.FIGHT && fight(action == 1, now);
            swing = Math.max(0, swing - 1);
            hurt = Math.max(0, hurt - 1);
            thrust = Math.max(0, thrust - 1);
            if (swung || now - lastState >= STATE_MILLIS) {
                sendState(outgoing);
                lastState = now;
            }
            show();
            hud.own(behaviour, report.health(), report.wins());
            hud.opponent(report.opponentPresent(), report.opponentKind(), report.opponentHealth(),
                    report.opponentWins());
            hud.pilot(human);
        }
    }

    /**
     * This frame's move, by the CPU or the HUMAN's {@code swingTapped}; returns whether the gladius was swung or a
     * javelin thrown, so the arena hears of it at once.
     */
    private boolean fight(boolean swingTapped, int now) {
        body.seeOpponent(report.visible(), report.opponentX(), report.opponentY());
        if (human) {
            return swingTapped && swingGladius(now);
        }
        Senses senses = brain.senses();
        senses.arm(report.holding(), report.rooted(), report.netReady());
        senses.lion(report.lion(), report.lionX(), report.lionY());
        for (int j = 0; j < Javelins.COUNT; j++) {
            senses.javelin(j, report.javelin(j) == GladiatorProtocol.JAVELIN_GROUND, report.javelinX(j), report.javelinY(j));
        }
        brain.see(report.visible(), report.opponentX(), report.opponentY(), now);
        brain.drive(body, now);
        boolean swung = brain.strike() && swingGladius(now);
        if (senses.castNet()) {
            netsCast = (netsCast + 1) & 0xFF;
            return true;
        }
        if (senses.throwJavelin()) {
            throwsMade = (throwsMade + 1) & 0xFF;
            return true;
        }
        return swung;
    }

    private void show() {
        if (report.phase() == Match.WAITING) {
            if (viewing) {
                viewing = false;
                view.list().clearView();
                Hud.centered("WAITING FOR AN OPPONENT", 92, 1, TftTouchShield.YELLOW, ViewList.BACKGROUND);
            }
            hud.status(0, "WAITING", TftTouchShield.GRAY);
            return;
        }
        if (!viewing) {
            viewing = true;
            view.list().clearView();
        }
        view.build(body, report.visible(), report.opponentX(), report.opponentY(), report.opponentAngle(),
                report.opponentKind(), thrust > 0, swing, hurt);
        view.list().present();
        hud.fighterStatus(report, slot, human, brain.mode(), hurt > 0);
    }

    private void searchArena() {
        screen = SEARCHING_ARENA;
        Hud.message("FINDING ARENA", "Waiting for a board in the ARENA role", TftTouchShield.YELLOW);
    }

    private void receive(byte[] incoming, int[] source, int now) {
        for (int i = 0; i < PACKETS_PER_FRAME; i++) {
            int length = Udp.receive(incoming, GladiatorProtocol.PACKET_SIZE, source);
            if (length <= 0) {
                return;
            }
            if (!GladiatorProtocol.valid(incoming, length) || GladiatorProtocol.nodeId(incoming) != nodeId) {
                continue;
            }
            int type = GladiatorProtocol.type(incoming);
            if (type == GladiatorProtocol.MAP && screen != IN_ARENA && ColiseumMap.read(incoming, coliseum)) {
                for (int k = 0; k < Udp.ENDPOINT_SIZE; k++) {
                    arena[k] = source[k];
                }
                slot = GladiatorProtocol.slot(incoming);
                screen = IN_ARENA;
                heardAt = now;
                report.clear();
                Serial.print("Map received: " + coliseum.pillars() + " pillars, ");
                Serial.println(slot == 0 ? "left gate" : "right gate");
                hud.bar();
                viewing = true;
            } else if (type == GladiatorProtocol.FULL && screen == SEARCHING_ARENA) {
                screen = ARENA_FULL;
                Hud.message("ARENA FULL", "Two gladiators are fighting; waiting", TftTouchShield.ORANGE);
            } else if (type == GladiatorProtocol.WORLD && screen == IN_ARENA) {
                heardAt = now;
                react(report.read(incoming), now);
            }
        }
    }

    /** A new round puts the gladiator back at its gate; a blow and the opponent's swing show in the view. */
    private void react(int events, int now) {
        if ((events & Report.NEW_ROUND) != 0) {
            body.spawn(slot);
            brain.reset(slot, now);
            swing = 0;
            hurt = 0;
            thrust = 0;
        }
        if ((events & Report.STRUCK) != 0) {
            hurt = report.surprised() ? 2 * HURT_FRAMES : HURT_FRAMES;
            brain.hurt(now);
            Serial.println((report.surprised() ? "Struck unseen! HP " : "Struck! HP ") + report.health());
        }
        if ((events & Report.OPPONENT_SWUNG) != 0) {
            thrust = THRUST_FRAMES;
        }
    }

    private boolean swingGladius(int now) {
        if (now - swungAt < Arms.swingMillis(kind)) {
            return false;
        }
        swungAt = now;
        strikes = (strikes + 1) & 0xFF;
        swing = SWING_FRAMES;
        return true;
    }

    private void sendState(byte[] packet) {
        GladiatorProtocol.begin(packet, GladiatorProtocol.STATE, slot, nodeId);
        packet[GladiatorProtocol.STATE_ROUND] = (byte) report.round();
        packet[GladiatorProtocol.STATE_BEHAVIOUR] = (byte) behaviour;
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.X, Math.round(body.x()));
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.Y, Math.round(body.y()));
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(body.angle()));
        packet[GladiatorProtocol.STRIKES] = (byte) strikes;
        packet[GladiatorProtocol.MODE] = (byte) (report.phase() == Match.FIGHT ? brain.mode() : Brain.WAITING);
        packet[GladiatorProtocol.HUMAN] = (byte) (human ? 1 : 0);
        packet[GladiatorProtocol.THROWS] = (byte) throwsMade;
        packet[GladiatorProtocol.STATE_KIND] = (byte) kind;
        packet[GladiatorProtocol.NETS] = (byte) netsCast;
        Udp.send(arena, arena[Udp.PORT], packet, GladiatorProtocol.PACKET_SIZE);
    }

    /**
     * This frame's touch: -1 when MENU was tapped, 1 when a HUMAN swings, 0 otherwise. A HUMAN gladiator turns and
     * walks here.
     */
    private int touch() {
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
        if (!human || report.phase() != Match.FIGHT || y >= ViewList.VIEW_BOTTOM) {
            return 0;
        }
        if (x < ViewList.WIDTH / 3) {
            body.turn(Brain.TURN);
        } else if (x >= 2 * ViewList.WIDTH / 3) {
            body.turn(-Brain.TURN);
        } else if (y < View.CY) {
            if (!report.rooted()) {
                body.walk(Brain.WALK * Arms.speed(kind));
            }
        } else {
            return 1;
        }
        return 0;
    }

    private boolean menuTapped() {
        if (!TftTouchShield.readTouch()) {
            touching = false;
            return false;
        }
        boolean tap = !touching;
        touching = true;
        return tap && TftTouchShield.touchY() >= ViewList.VIEW_BOTTOM;
    }
}
