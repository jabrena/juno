package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules of the Gladiator arena: the map packet, what the eyes see, how the arena judges strikes and rounds, what the
 * first-person view draws, and a whole duel between the two CPU behaviours played through the arena's own code and
 * packets, without the network.
 */
class GladiatorTest {
    private static final int[] SOURCE = {192, 168, 1, 20, Gladiator.PORT};
    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    @BeforeEach
    void buildTheColiseum() {
        Coliseum.build();
        Random.seed(7);
    }

    @Test
    void theColiseumHasTheFifteenPillarsOfThePicture() {
        assertThat(Coliseum.pillars).isEqualTo(15);
        assertThat(Coliseum.walkable(Coliseum.spawnX(LEFT), 0)).as("left gate").isTrue();
        assertThat(Coliseum.walkable(Coliseum.spawnX(RIGHT), 0)).as("right gate").isTrue();
        assertThat(Coliseum.walkable(0, 0)).as("the middle pillar").isFalse();
        assertThat(Coliseum.walkable(0, 260)).as("against the wall").isFalse();
    }

    @Test
    void theMapTravelsOnceAndArrivesWhole() {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.writeMap(packet, RIGHT, 0xCAFEBABE);
        short[] pillarX = Coliseum.pillarX.clone();
        short[] pillarY = Coliseum.pillarY.clone();
        Coliseum.radiusX = 0;
        Coliseum.pillars = 0;

        assertThat(GladiatorProtocol.valid(packet, packet.length)).isTrue();
        assertThat(GladiatorProtocol.type(packet)).isEqualTo(GladiatorProtocol.MAP);
        assertThat(GladiatorProtocol.slot(packet)).isEqualTo(RIGHT);
        assertThat(GladiatorProtocol.nodeId(packet)).isEqualTo(0xCAFEBABE);
        assertThat(GladiatorProtocol.readMap(packet)).isTrue();
        assertThat(Coliseum.radiusX).isEqualTo(615);
        assertThat(Coliseum.radiusY).isEqualTo(275);
        assertThat(Coliseum.pillars).isEqualTo(15);
        assertThat(Coliseum.pillarX).containsExactly(pillarX);
        assertThat(Coliseum.pillarY).as("negative coordinates survive").containsExactly(pillarY);
    }

    @Test
    void packetsOfOtherGamesAndOtherLengthsAreRejected() {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(packet, GladiatorProtocol.JOIN, 0, 1);
        assertThat(GladiatorProtocol.valid(packet, packet.length - 1)).isFalse();
        packet[5] = 3;
        assertThat(GladiatorProtocol.valid(packet, packet.length)).as("the Miner's chain service").isFalse();
    }

    @Test
    void anglesTravelInSixteenBits() {
        for (float angle : new float[] {0f, 1f, -1f, 3.1f, -3.1f}) {
            float back = Eyes.wrap(GladiatorProtocol.fromWire(GladiatorProtocol.toWire(angle)));
            assertThat(back).isCloseTo(angle, org.assertj.core.data.Offset.offset(0.001f));
        }
    }

    @Test
    void eyesSeeOnlyWhatIsInFrontAndNotBehindAPillar() {
        assertThat(Eyes.sees(-470, 0, 0f, -380, 60)).as("in front, clear").isTrue();
        assertThat(Eyes.sees(-470, 0, (float) Math.PI, -380, 60)).as("behind its back").isFalse();
        assertThat(Eyes.sees(-470, 0, 0f, -470, 200)).as("off to the side").isFalse();
        assertThat(Eyes.sees(-470, 0, 0f, 470, 0)).as("across the arena, behind the middle row").isFalse();
        assertThat(Eyes.sees(-470, 62, 0f, 470, 62)).as("down the lane between two rows").isTrue();
    }

    @Test
    void theBattleStartsWhenTheSecondGladiatorArrives() {
        Match match = new Match();
        assertThat(match.join(11, Brain.AGGRESSIVE, SOURCE, 900)).isEqualTo(LEFT);
        assertThat(match.phase).isEqualTo(Match.WAITING);
        assertThat(match.join(11, Brain.AGGRESSIVE, SOURCE, 1000)).as("a JOIN again keeps the slot").isEqualTo(LEFT);
        assertThat(match.join(22, Brain.AMBUSH, SOURCE, 1000)).isEqualTo(RIGHT);
        assertThat(match.phase).isEqualTo(Match.COUNTDOWN);
        assertThat(match.join(33, Brain.AMBUSH, SOURCE, 1000)).as("a third finds it full").isEqualTo(-1);

        match.tick(1000 + Match.COUNTDOWN_MILLIS);
        assertThat(match.phase).isEqualTo(Match.FIGHT);
    }

    @Test
    void aStrikeTheVictimSeesComingDoesNormalDamage() {
        Match match = fight();
        stand(match, LEFT, -100, 62, 0f, 0, 10_000);
        stand(match, RIGHT, -40, 62, (float) Math.PI, 0, 10_000);

        stand(match, LEFT, -100, 62, 0f, 1, 10_100);

        assertThat(match.health[RIGHT]).isEqualTo(Match.HEALTH - Match.DAMAGE);
        assertThat(match.surprised[RIGHT]).isFalse();
    }

    @Test
    void aStrikeFromBehindDoesDoubleDamage() {
        Match match = fight();
        stand(match, LEFT, -100, 62, 0f, 0, 10_000);
        stand(match, RIGHT, -40, 62, 0f, 0, 10_000);

        stand(match, LEFT, -100, 62, 0f, 1, 10_100);

        assertThat(match.health[RIGHT]).isEqualTo(Match.HEALTH - 2 * Match.DAMAGE);
        assertThat(match.surprised[RIGHT]).isTrue();
    }

    @Test
    void aStrikeOutOfReachOrSwungTooSoonMisses() {
        Match match = fight();
        stand(match, LEFT, -200, 62, 0f, 0, 10_000);
        stand(match, RIGHT, -40, 62, (float) Math.PI, 0, 10_000);
        stand(match, LEFT, -200, 62, 0f, 1, 10_100);
        assertThat(match.health[RIGHT]).as("out of reach").isEqualTo(Match.HEALTH);

        stand(match, LEFT, -100, 62, 0f, 2, 10_700);
        stand(match, LEFT, -100, 62, 0f, 3, 10_800);
        assertThat(match.health[RIGHT]).as("one blow per swing time").isEqualTo(Match.HEALTH - Match.DAMAGE);
    }

    @Test
    void theOpponentIsReportedOnlyWhileTheEyesSeeIt() {
        Match match = fight();
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        stand(match, LEFT, -470, 0, 0f, 0, 10_000);
        stand(match, RIGHT, 470, 0, (float) Math.PI, 0, 10_000);

        match.writeWorld(packet, LEFT, 10_000);
        assertThat(packet[GladiatorProtocol.OPPONENT]).isEqualTo((byte) 1);
        assertThat(packet[GladiatorProtocol.VISIBLE]).as("behind the middle row").isZero();
        assertThat(GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_X)).isZero();

        stand(match, RIGHT, -380, 60, (float) Math.PI, 0, 10_100);
        match.writeWorld(packet, LEFT, 10_100);
        assertThat(packet[GladiatorProtocol.VISIBLE]).isEqualTo((byte) 1);
        assertThat(GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_X)).isEqualTo(-380);
    }

    @Test
    void aSilentGladiatorLeavesItsSlot() {
        Match match = fight();
        stand(match, LEFT, -470, 0, 0f, 0, 10_000);
        match.tick(10_000 + Match.TIMEOUT_MILLIS);

        assertThat(match.present[LEFT]).isTrue();
        assertThat(match.present[RIGHT]).isFalse();
        assertThat(match.phase).isEqualTo(Match.WAITING);
    }

    @Test
    void anOpponentBehindAPillarIsNotDrawnButInTheOpenItIs() {
        short[] lines = ViewList.lines;
        Body body = new Body();
        body.x = -470;
        body.y = 0;
        body.angle = 0f;

        View.build(lines, body, true, -200, 0, (float) Math.PI, Brain.AMBUSH, false, 0, 0);
        assertThat(figureLines(lines)).as("hidden by the left pillar of the middle row").isZero();

        View.build(lines, body, true, -380, 0, (float) Math.PI, Brain.AMBUSH, false, 0, 0);
        assertThat(figureLines(lines)).as("in the open").isGreaterThan(10);
        assertThat(ViewList.size()).isLessThan(ViewList.MAX_LINES);
    }

    @Test
    void theViewNeverRunsOutOfLines() {
        Body body = new Body();
        for (int x = -540; x <= 540; x += 60) {
            for (int y = -200; y <= 200; y += 50) {
                if (!Coliseum.walkable(x, y)) {
                    continue;
                }
                for (int turn = 0; turn < 8; turn++) {
                    body.x = x;
                    body.y = y;
                    body.angle = turn * (float) Math.PI / 4;
                    View.build(ViewList.lines, body, false, 0, 0, 0f, Brain.AGGRESSIVE, false, 0, 0);
                    assertThat(ViewList.size()).as("lines from (%d, %d) turned %d", x, y, turn)
                            .isLessThan(ViewList.MAX_LINES);
                }
            }
        }
    }

    @Test
    void theAmbushHidesBehindAPillarAwayFromTheThreat() {
        Brain brain = new Brain(Brain.AMBUSH);
        Body body = new Body();
        body.spawn(RIGHT);
        brain.reset(RIGHT, 0);
        for (int frame = 0; frame < 200 && brain.mode != Brain.LURKING; frame++) {
            brain.see(false, 0, 0, frame * 40);
            brain.drive(body, frame * 40);
        }

        assertThat(brain.mode).isEqualTo(Brain.LURKING);
        assertThat(brain.pillar).isNotNegative();
        assertThat(body.x).as("on the pillar's side away from the left gate")
                .isGreaterThan(Coliseum.pillarX[brain.pillar]);
        assertThat(Coliseum.clearSight(Coliseum.spawnX(LEFT), 0, body.x, body.y)).isFalse();
    }

    @Test
    void anAggressiveAndAnAmbushGladiatorFightUntilOneFalls() {
        Duel duel = new Duel();
        int winner = duel.play(10 * 60 * 1000);

        assertThat(winner).as("someone wins within ten minutes").isIn(LEFT, RIGHT);
        assertThat(duel.match.landed).isGreaterThanOrEqualTo(Match.HEALTH / (2 * Match.DAMAGE));
        assertThat(duel.match.health[1 - winner]).isZero();
    }

    @Test
    void figuresAndHandsAreWholeSegments() {
        assertThat(Figure.SEGMENTS.length % 4).isZero();
        assertThat(Figure.START[Figure.START.length - 1]).isEqualTo((short) Figure.SEGMENTS.length);
        assertThat(Figure.HANDS_START[Figure.HANDS_START.length - 1]).isEqualTo((short) Figure.HANDS.length);
    }

    @Test
    void theRoleMenuButtonsPickTheRoles() {
        assertThat(Gladiator.roleAt(160, 90)).as("ARENA").isZero();
        assertThat(Gladiator.roleAt(160, 145)).isEqualTo(Brain.AGGRESSIVE);
        assertThat(Gladiator.roleAt(160, 205)).isEqualTo(Brain.AMBUSH);
        assertThat(Gladiator.roleAt(160, 118)).as("between two buttons").isEqualTo(-1);
        assertThat(Gladiator.roleAt(5, 90)).as("left of the buttons").isEqualTo(-1);
    }

    private static int figureLines(short[] lines) {
        int count = 0;
        for (int i = 0; i < ViewList.size(); i++) {
            if ((ViewList.built(lines, i, 4) & 0xFFFF) == Figure.AMBUSH_COLOR) {
                count++;
            }
        }
        return count;
    }

    /** Two gladiators joined, the countdown over, at second 10. */
    private static Match fight() {
        Match match = new Match();
        match.join(11, Brain.AGGRESSIVE, SOURCE, 0);
        match.join(22, Brain.AMBUSH, SOURCE, 0);
        match.tick(Match.COUNTDOWN_MILLIS);
        assertThat(match.phase).isEqualTo(Match.FIGHT);
        return match;
    }

    /** The gladiator in {@code slot} reports where it stands and how many strikes it swung. */
    private static void stand(Match match, int slot, int x, int y, float angle, int strikes, int now) {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(packet, GladiatorProtocol.STATE, slot, match.node[slot]);
        packet[GladiatorProtocol.STATE_ROUND] = (byte) match.round;
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.X, x);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.Y, y);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(angle));
        packet[GladiatorProtocol.STRIKES] = (byte) strikes;
        match.state(slot, packet, now);
    }

    /**
     * The arena and both gladiators' boards in one loop: each frame every gladiator thinks and moves on what its last
     * {@code WORLD} said; ten times a second they report to the arena and the arena reports back.
     */
    private static final class Duel {
        final Match match = new Match();
        private final Body[] bodies = {new Body(), new Body()};
        private final Brain[] brains = {new Brain(Brain.AGGRESSIVE), new Brain(Brain.AMBUSH)};
        private final int[] strikes = new int[2];
        private final int[] swungAt = {-10_000, -10_000};
        private final int[] rounds = {-1, -1};
        private final int[] hits = {-1, -1};
        private final boolean[] visible = new boolean[2];
        private final float[] opponentX = new float[2];
        private final float[] opponentY = new float[2];
        private final byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];

        int play(int millis) {
            match.join(11, Brain.AGGRESSIVE, SOURCE, 0);
            match.join(22, Brain.AMBUSH, SOURCE, 0);
            for (int now = 0; now < millis; now += 40) {
                match.tick(now);
                if (match.phase == Match.OVER) {
                    return match.winner;
                }
                if (now % 120 == 0) {
                    for (int slot = 0; slot < 2; slot++) {
                        match.writeWorld(packet, slot, now);
                        world(slot, now);
                    }
                }
                for (int slot = 0; slot < 2; slot++) {
                    boolean swung = false;
                    if (match.phase == Match.FIGHT) {
                        Body body = bodies[slot];
                        body.opponentInSight = visible[slot];
                        body.opponentX = opponentX[slot];
                        body.opponentY = opponentY[slot];
                        brains[slot].see(visible[slot], opponentX[slot], opponentY[slot], now);
                        brains[slot].drive(body, now);
                        if (brains[slot].strike && now - swungAt[slot] >= 700) {
                            swungAt[slot] = now;
                            strikes[slot] = (strikes[slot] + 1) & 0xFF;
                            swung = true;
                        }
                    }
                    if (swung || now % 120 == 0) {
                        stand(match, slot, Math.round(bodies[slot].x), Math.round(bodies[slot].y),
                                bodies[slot].angle, strikes[slot], now);
                    }
                }
            }
            return Match.NOBODY;
        }

        private void world(int slot, int now) {
            int round = GladiatorProtocol.unsigned(packet, GladiatorProtocol.ROUND);
            if (round != rounds[slot]) {
                rounds[slot] = round;
                bodies[slot].spawn(slot);
                brains[slot].reset(slot, now);
            }
            int hit = GladiatorProtocol.unsigned(packet, GladiatorProtocol.HITS);
            if (hits[slot] >= 0 && hit != hits[slot]) {
                brains[slot].hurt(now);
            }
            hits[slot] = hit;
            visible[slot] = packet[GladiatorProtocol.VISIBLE] != 0;
            if (visible[slot]) {
                opponentX[slot] = GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_X);
                opponentY[slot] = GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_Y);
            }
        }
    }
}
