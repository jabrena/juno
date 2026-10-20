package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.games.gladiator.arena.Arms;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Braziers;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.ColiseumMap;
import io.github.jabrena.juno.games.gladiator.arena.Eyes;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.arena.Javelins;
import io.github.jabrena.juno.games.gladiator.arena.Lion;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.arena.Side;
import io.github.jabrena.juno.games.gladiator.gladiator.Ambush;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.games.gladiator.screen.FighterProfile;
import io.github.jabrena.juno.games.gladiator.screen.FighterSetup;
import io.github.jabrena.juno.games.gladiator.screen.ModeMenu;
import io.github.jabrena.juno.games.gladiator.screen.Opening;
import io.github.jabrena.juno.games.gladiator.screen.RoleMenu;
import io.github.jabrena.juno.games.gladiator.screen.SoloDuel;
import io.github.jabrena.juno.games.gladiator.screen.SoloPresentation;
import io.github.jabrena.juno.games.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.games.gladiator.view.Figure;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Rules of the Gladiator arena: the map packet, what the eyes see, how the arena judges strikes and rounds, what the
 * first-person view draws, and a whole duel between the two CPU behaviours played through the arena's own code and
 * packets, without the network.
 */
class GladiatorTest {
    private static final int[] SOURCE = {192, 168, 1, 20, GladiatorProtocol.PORT};
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    /** The class the arena tests fight as: a THRAEX has no shield or helmet to change a blow from in front. */
    private static final int FIGHTER = GladiatorClass.THRAEX;

    private Coliseum coliseum;
    private ViewList list;

    @BeforeEach
    void buildTheColiseum() {
        grid();
        list = new ViewList();
        Random.seed(7);
    }

    /**
     * The coliseum with a fixed five-by-five block of pillars {@link Coliseum#SPACING} apart instead of its random
     * ones, so the tests of sight, strikes and steering know where every pillar stands.
     */
    private void grid() {
        coliseum = new Coliseum();
        coliseum.define(1600, 1012, 300, Coliseum.PILLAR_RADIUS, 420);
        for (int row = -2; row <= 2; row++) {
            for (int column = -2; column <= 2; column++) {
                coliseum.addPillar(column * Coliseum.SPACING, row * Coliseum.SPACING);
            }
        }
    }

    @Test
    void theTitleShowsOneOfTwoQuotes() {
        assertThat(Opening.QUOTES).isEqualTo(2);
        assertThat(Opening.quoteLine(0, 0) + " " + Opening.quoteLine(0, 1)).isEqualTo("TIMOR MORTIS MORTE PEIOR");
        assertThat(Opening.quoteLine(1, 0) + " " + Opening.quoteLine(1, 1)).isEqualTo("FORTES FORTUNA ADIUVAT");
    }

    @Test
    void everyBoutStandsFivePillarsAtRandomWhereTheyLeaveRoomToFight() {
        java.util.Set<Long> layouts = new java.util.HashSet<>();
        for (int seed = 1; seed <= 50; seed++) {
            Random.seed(seed);
            Coliseum arena = new Coliseum();
            arena.build();
            assertThat(arena.pillars()).as("pillars with seed %d", seed).isEqualTo(Coliseum.PILLAR_COUNT);
            long layout = 0;
            for (int p = 0; p < arena.pillars(); p++) {
                int x = arena.pillarX(p);
                int y = arena.pillarY(p);
                assertThat(Math.abs(x)).isLessThanOrEqualTo(Coliseum.PLACE_X);
                assertThat(Math.abs(y)).isLessThanOrEqualTo(Coliseum.PLACE_Y);
                for (int q = 0; q < p; q++) {
                    assertThat(Math.hypot(x - arena.pillarX(q), y - arena.pillarY(q)))
                            .as("pillars %d and %d apart", p, q).isGreaterThanOrEqualTo(Coliseum.SPACING);
                }
                layout = layout * 31 + x * 7919L + y;
            }
            layouts.add(layout);
            assertThat(arena.walkable(Coliseum.spawnX(LEFT), 0)).as("left gate").isTrue();
            assertThat(arena.walkable(Coliseum.spawnX(RIGHT), 0)).as("right gate").isTrue();
            assertThat(arena.walkable(Lion.GATE_X, Lion.gateY(arena))).as("lion's gate").isTrue();
            assertThat(arena.walkable(0, arena.radiusY())).as("against the wall").isFalse();
        }
        assertThat(layouts).as("a different arena each time").hasSizeGreaterThan(45);
    }

    @Test
    void theMapTravelsOnceAndArrivesWhole() {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        ColiseumMap.write(packet, RIGHT, 0xCAFEBABE, coliseum);
        Coliseum received = new Coliseum();
        received.addPillar(5, 5);

        assertThat(GladiatorProtocol.valid(packet, packet.length)).isTrue();
        assertThat(GladiatorProtocol.type(packet)).isEqualTo(GladiatorProtocol.MAP);
        assertThat(GladiatorProtocol.slot(packet)).isEqualTo(RIGHT);
        assertThat(GladiatorProtocol.nodeId(packet)).isEqualTo(0xCAFEBABE);
        assertThat(ColiseumMap.read(packet, received)).isTrue();
        assertThat(received.radiusX()).isEqualTo(1600);
        assertThat(received.radiusY()).isEqualTo(1012);
        assertThat(received.pillars()).isEqualTo(25);
        for (int p = 0; p < coliseum.pillars(); p++) {
            assertThat(received.pillarX(p)).as("pillar %d x, negatives surviving", p).isEqualTo(coliseum.pillarX(p));
            assertThat(received.pillarY(p)).as("pillar %d y, negatives surviving", p).isEqualTo(coliseum.pillarY(p));
        }
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
        assertThat(Eyes.sees(coliseum, -470, 0, 0f, -380, 60)).as("in front, clear").isTrue();
        assertThat(Eyes.sees(coliseum, -470, 0, (float) Math.PI, -380, 60)).as("behind its back").isFalse();
        assertThat(Eyes.sees(coliseum, -470, 0, 0f, -470, 200)).as("off to the side").isFalse();
        assertThat(Eyes.sees(coliseum, -470, 0, 0f, 470, 0)).as("across the arena, behind the middle row").isFalse();
        assertThat(Eyes.sees(coliseum, -470, 62, 0f, 470, 62)).as("down the lane between two rows").isTrue();
    }

    @Test
    void theBattleStartsWhenTheSecondGladiatorArrives() {
        Match match = new Match(coliseum);
        assertThat(match.join(11, Brain.AGGRESSIVE, SOURCE, 900)).isEqualTo(LEFT);
        assertThat(match.phase()).isEqualTo(Match.WAITING);
        assertThat(match.join(11, Brain.AGGRESSIVE, SOURCE, 1000)).as("a JOIN again keeps the slot").isEqualTo(LEFT);
        assertThat(match.join(22, Brain.AMBUSH, SOURCE, 1000)).isEqualTo(RIGHT);
        assertThat(match.phase()).isEqualTo(Match.COUNTDOWN);
        assertThat(match.join(33, Brain.AMBUSH, SOURCE, 1000)).as("a third finds it full").isEqualTo(-1);

        match.tick(1000 + Match.COUNTDOWN_MILLIS);
        assertThat(match.phase()).isEqualTo(Match.FIGHT);
    }

    @Test
    void aStrikeTheVictimSeesComingDoesNormalDamage() {
        Match match = fight();
        stand(match, LEFT, -100, 62, 0f, 0, 10_000);
        stand(match, RIGHT, -40, 62, (float) Math.PI, 0, 10_000);

        stand(match, LEFT, -100, 62, 0f, 1, 10_100);

        assertThat(match.side(RIGHT).health()).isEqualTo(Match.HEALTH - Arms.damage(FIGHTER));
        assertThat(match.side(RIGHT).surprised()).isFalse();
    }

    @Test
    void aStrikeFromBehindDoesDoubleDamage() {
        Match match = fight();
        stand(match, LEFT, -100, 62, 0f, 0, 10_000);
        stand(match, RIGHT, -40, 62, 0f, 0, 10_000);

        stand(match, LEFT, -100, 62, 0f, 1, 10_100);

        assertThat(match.side(RIGHT).health()).isEqualTo(Match.HEALTH - 2 * Arms.damage(FIGHTER));
        assertThat(match.side(RIGHT).surprised()).isTrue();
    }

    @Test
    void aStrikeOutOfReachOrSwungTooSoonMisses() {
        Match match = fight();
        stand(match, LEFT, -200, 62, 0f, 0, 10_000);
        stand(match, RIGHT, -40, 62, (float) Math.PI, 0, 10_000);
        stand(match, LEFT, -200, 62, 0f, 1, 10_100);
        assertThat(match.side(RIGHT).health()).as("out of reach").isEqualTo(Match.HEALTH);

        stand(match, LEFT, -100, 62, 0f, 2, 10_700);
        stand(match, LEFT, -100, 62, 0f, 3, 10_800);
        assertThat(match.side(RIGHT).health()).as("one blow per swing time")
                .isEqualTo(Match.HEALTH - Arms.damage(FIGHTER));
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

        assertThat(match.side(LEFT).present()).isTrue();
        assertThat(match.side(RIGHT).present()).isFalse();
        assertThat(match.phase()).isEqualTo(Match.WAITING);
    }

    @Test
    void anOpponentBehindAPillarIsNotDrawnButInTheOpenItIs() {
        Body body = new Body(coliseum);
        body.place(-470, 0, 0f);
        View view = new View(list, coliseum, reportWith(0));

        view.build(body, true, -200, 0, (float) Math.PI, Brain.AMBUSH, false, 0, 0);
        assertThat(figureLines()).as("hidden by the left pillar of the middle row").isZero();

        view.build(body, true, -380, 0, (float) Math.PI, Brain.AMBUSH, false, 0, 0);
        assertThat(figureLines()).as("in the open").isGreaterThan(10);
        assertThat(list.size()).isLessThan(ViewList.MAX_LINES);
    }

    @Test
    void theViewNeverRunsOutOfLines() {
        Body body = new Body(coliseum);
        View view = new View(list, coliseum, reportWith(0));
        for (int x = -1000; x <= 1000; x += 100) {
            for (int y = -400; y <= 400; y += 80) {
                if (!coliseum.walkable(x, y)) {
                    continue;
                }
                for (int turn = 0; turn < 8; turn++) {
                    body.place(x, y, turn * (float) Math.PI / 4);
                    view.build(body, false, 0, 0, 0f, Brain.AGGRESSIVE, false, 0, 0);
                    assertThat(list.size()).as("lines from (%d, %d) turned %d", x, y, turn)
                            .isLessThan(ViewList.MAX_LINES);
                }
            }
        }
    }

    @Test
    void theAmbushHidesBehindAPillarAwayFromTheThreat() {
        Brain brain = new Brain(Brain.AMBUSH);
        Body body = new Body(coliseum);
        body.spawn(RIGHT);
        brain.reset(RIGHT, 0);
        for (int frame = 0; frame < 800 && brain.mode() != Brain.LURKING; frame++) {
            brain.see(false, 0, 0, frame * 40);
            brain.drive(body, frame * 40);
        }

        assertThat(brain.mode()).isEqualTo(Brain.LURKING);
        assertThat(brain.lair().pillar()).isNotNegative();
        assertThat(body.x()).as("on the pillar's side away from the left gate")
                .isGreaterThan(coliseum.pillarX(brain.lair().pillar()));
        assertThat(coliseum.clearSight(Coliseum.spawnX(LEFT), 0, body.x(), body.y())).isFalse();
    }

    @Test
    void gladiatorsThatSeeEachOtherPastAPillarEdgeWalkRoundItAndMeet() {
        // The line between them clears the pillar at (-352, 0) by 8, so their eyes meet, but their bodies do not pass.
        Body[] bodies = {new Body(coliseum), new Body(coliseum)};
        Brain[] brains = {new Brain(Brain.AGGRESSIVE), new Brain(Brain.AMBUSH)};
        bodies[LEFT].place(-440, 52, 0f);
        bodies[RIGHT].place(-264, 52, (float) Math.PI);
        brains[LEFT].reset(LEFT, 0);
        brains[RIGHT].reset(RIGHT, 0);
        assertThat(coliseum.clearSight(-440, 52, -264, 52)).isTrue();

        boolean struck = false;
        for (int frame = 0; frame < 250 && !struck; frame++) {
            int now = frame * 40;
            for (int slot = 0; slot < 2; slot++) {
                Body body = bodies[slot];
                Body other = bodies[1 - slot];
                boolean sees = Eyes.sees(coliseum, body.x(), body.y(), body.angle(), other.x(), other.y());
                body.seeOpponent(sees, other.x(), other.y());
                brains[slot].see(sees, other.x(), other.y(), now);
                brains[slot].drive(body, now);
                struck = struck || brains[slot].strike();
            }
        }

        assertThat(struck).as("one of them got within reach and swung, ten seconds at most").isTrue();
    }

    @Test
    void twoGladiatorsCanPassSideBySideBetweenNeighbouringPillars() {
        int fitsTwo = 2 * 2 * Coliseum.BODY;
        int checked = 0;
        for (int i = 0; i < coliseum.pillars(); i++) {
            for (int j = i + 1; j < coliseum.pillars(); j++) {
                double distance = Math.hypot(coliseum.pillarX(i) - coliseum.pillarX(j),
                        coliseum.pillarY(i) - coliseum.pillarY(j));
                if (distance < 2.5 * Coliseum.SPACING) {
                    checked++;
                    assertThat(distance - 2 * Coliseum.PILLAR_RADIUS)
                            .as("gap between pillars %d and %d", i, j)
                            .isGreaterThanOrEqualTo(fitsTwo);
                }
            }
        }
        assertThat(checked).as("pairs of neighbouring pillars").isGreaterThan(coliseum.pillars());
    }

    @Test
    void aBodyPushingIntoAPillarSlidesRoundIt() {
        Body body = new Body(coliseum);
        // Heading almost straight at the pillar at (-320, 0), a little off its centre.
        body.place(-420, 6, 0f);
        float start = body.y();
        for (int frame = 0; frame < 40; frame++) {
            body.walk(Brain.WALK);
        }
        assertThat(Math.abs(body.y() - start)).as("slid along the pillar's side").isGreaterThan(20f);
    }

    @Test
    void anAggressiveAndAnAmbushGladiatorFightUntilOneFalls() {
        Duel duel = new Duel();
        int winner = duel.play(10 * 60 * 1000);

        assertThat(winner).as("someone wins within ten minutes").isIn(LEFT, RIGHT);
        assertThat(duel.match.landed()).isGreaterThanOrEqualTo(Match.HEALTH / (2 * Match.DAMAGE));
        assertThat(duel.match.side(1 - winner).health()).isZero();
    }

    @Test
    void figuresAndHandsAreWholeSegments() {
        assertThat(Figure.SEGMENTS.length % 4).isZero();
        assertThat(Figure.START[Figure.START.length - 1]).isEqualTo((short) Figure.SEGMENTS.length);
        assertThat(Figure.HANDS_START[Figure.HANDS_START.length - 1]).isEqualTo((short) Figure.HANDS.length);
    }

    @Test
    void theRoleMenuButtonsPickTheRoles() {
        assertThat(Gladiator.roleAt(160, 78)).as("ARENA").isZero();
        assertThat(Gladiator.roleAt(160, 124)).as("PLAYER 1").isEqualTo(Brain.AGGRESSIVE);
        assertThat(Gladiator.roleAt(160, 170)).as("PLAYER 2").isEqualTo(Brain.AMBUSH);
        assertThat(Gladiator.roleAt(160, 215)).as("RETURN").isEqualTo(RoleMenu.RETURN);
        assertThat(Gladiator.roleAt(160, 101)).as("between two buttons").isEqualTo(-1);
        assertThat(Gladiator.roleAt(5, 78)).as("left of the buttons").isEqualTo(-1);
        assertThat(Gladiator.roleAt(40, 215)).as("beside RETURN").isEqualTo(-1);
    }

    @Test
    void theOpeningCyclesFourClassesAndOnlyItsButtonStartsTheGame() {
        assertThat(Opening.INTRO_MILLIS).isEqualTo(5000);
        assertThat(Opening.GALLERY_MILLIS).isEqualTo(10_000);
        assertThat(Opening.classAt(0)).isEqualTo(GladiatorClass.MURMILLO);
        assertThat(Opening.classAt(2499)).isEqualTo(GladiatorClass.MURMILLO);
        assertThat(Opening.classAt(2500)).isEqualTo(GladiatorClass.THRAEX);
        assertThat(Opening.classAt(5000)).isEqualTo(GladiatorClass.RETIARIUS);
        assertThat(Opening.classAt(7500)).isEqualTo(GladiatorClass.SECUTOR);
        assertThat(Opening.classAt(10_000)).isEqualTo(GladiatorClass.MURMILLO);
        assertThat(Opening.className(GladiatorClass.MURMILLO)).isEqualTo("MURMILLO");
        assertThat(Opening.className(GladiatorClass.THRAEX)).isEqualTo("THRAEX");
        assertThat(Opening.className(GladiatorClass.RETIARIUS)).isEqualTo("RETIARIUS");
        assertThat(Opening.className(GladiatorClass.SECUTOR)).isEqualTo("SECUTOR");

        assertThat(Opening.onPlay(20, 206)).isTrue();
        assertThat(Opening.onPlay(299, 233)).isTrue();
        assertThat(Opening.onPlay(19, 220)).isFalse();
        assertThat(Opening.onPlay(300, 220)).isFalse();
        assertThat(Opening.onPlay(160, 234)).isFalse();
    }

    @Test
    void theModeMenuKeepsSoloExclusiveToTheUnoQ() {
        assertThat(ModeMenu.modeAt(160, 100, true)).isEqualTo(ModeMenu.SOLO);
        assertThat(ModeMenu.modeAt(160, 100, false)).as("solo on an R4").isEqualTo(-1);
        assertThat(ModeMenu.modeAt(160, 170, true)).isEqualTo(ModeMenu.MULTIPLAYER);
        assertThat(ModeMenu.modeAt(160, 170, false)).isEqualTo(ModeMenu.MULTIPLAYER);
        assertThat(ModeMenu.modeAt(5, 100, true)).isEqualTo(-1);
        assertThat(ModeMenu.modeAt(160, 140, true)).as("between buttons").isEqualTo(-1);
    }

    @Test
    void fighterSetupOffersFourNamesAndClassesThenTwoStyles() {
        assertThat(FighterSetup.choiceAt(FighterSetup.NAME, 20, 80)).isEqualTo(FighterProfile.SPARTACUS);
        assertThat(FighterSetup.choiceAt(FighterSetup.NAME, 180, 80)).isEqualTo(FighterProfile.CRIXUS);
        assertThat(FighterSetup.choiceAt(FighterSetup.NAME, 20, 160)).isEqualTo(FighterProfile.FLAMMA);
        assertThat(FighterSetup.choiceAt(FighterSetup.NAME, 180, 160)).isEqualTo(FighterProfile.PRISCUS);
        assertThat(FighterSetup.choiceAt(FighterSetup.CLASS, 20, 80)).isEqualTo(GladiatorClass.MURMILLO);
        assertThat(FighterSetup.choiceAt(FighterSetup.CLASS, 180, 160)).isEqualTo(GladiatorClass.SECUTOR);

        FighterProfile flamma = new FighterProfile(FighterProfile.FLAMMA, GladiatorClass.RETIARIUS);
        assertThat(flamma.displayName()).isEqualTo("FLAMMA");
        assertThat(flamma.color()).isEqualTo(TftTouchShield.CYAN);
        assertThat(flamma.behaviour()).isEqualTo(Brain.AMBUSH);
    }

    @Test
    void soloRunsBothSelectedStylesAsLocalCpuGladiators() {
        int start = 600_000;
        SoloDuel duel = new SoloDuel(Brain.AGGRESSIVE, Brain.AGGRESSIVE, start);
        Match match = duel.match();
        assertThat(match.side(0).present()).isTrue();
        assertThat(match.side(1).present()).isTrue();
        assertThat(match.phase()).isEqualTo(Match.COUNTDOWN);
        assertThat(duel.cpuBrain().behaviour()).isEqualTo(Brain.AGGRESSIVE);
        assertThat(duel.playerBrain().behaviour()).isEqualTo(Brain.AGGRESSIVE);

        int gateX = match.side(0).x();
        duel.step(start + Match.COUNTDOWN_MILLIS);
        assertThat(match.phase()).isEqualTo(Match.FIGHT);
        duel.step(start + Match.COUNTDOWN_MILLIS + 40);
        assertThat(match.side(0).x()).isNotEqualTo(gateX);
        assertThat(match.side(0).human()).isFalse();
        assertThat(match.side(1).human()).isFalse();

        byte[] world = new byte[GladiatorProtocol.PACKET_SIZE];
        duel.writeWorld(world, start + Match.COUNTDOWN_MILLIS + 40);
        Report report = new Report(duel.coliseum());
        report.read(world);
        assertThat(report.health()).isEqualTo(Match.HEALTH);
        assertThat(report.opponentPresent()).isTrue();
        assertThat(report.opponentHealth()).isEqualTo(Match.HEALTH);
        assertThat(report.opponentKind()).isEqualTo(Brain.AGGRESSIVE);
    }

    @Test
    void soloGladiatorsOfEveryStyleFindEachOtherWithinAMinute() {
        int[][] pairs = {{Brain.AGGRESSIVE, Brain.AGGRESSIVE}, {Brain.AGGRESSIVE, Brain.AMBUSH},
                {Brain.AMBUSH, Brain.AMBUSH}};
        for (int[] pair : pairs) {
            SoloDuel duel = new SoloDuel(pair[0], pair[1], 1000);
            int firstSeen = -1;
            for (int frame = 0; frame < 1500 && firstSeen < 0; frame++) {
                duel.step(1000 + frame * 40);
                if (duel.match().sees(0) || duel.match().sees(1)) {
                    firstSeen = frame * 40;
                }
            }
            assertThat(firstSeen).as("styles %d and %d meet", pair[0], pair[1]).isBetween(0, 60_000);
        }
    }

    @Test
    void aggressiveSprintsLongerThanAmbushBursts() {
        Body aggressive = new Body(coliseum);
        aggressive.place(-400, 0, 0f);
        Brain sprinter = new Brain(Brain.AGGRESSIVE);
        for (int frame = 0; frame < 50; frame++) {
            sprinter.see(true, 400, 0, frame * 40);
            sprinter.drive(aggressive, frame * 40);
        }
        assertThat(sprinter.stamina()).as("50 running frames cost half a point each").isCloseTo(75f, within(0.01f));

        Body ambusher = new Body(coliseum);
        ambusher.place(-400, 0, 0f);
        Brain burst = new Brain(Brain.AMBUSH);
        for (int frame = 0; frame < 10; frame++) {
            burst.see(true, -200, 0, frame * 40);
            burst.drive(ambusher, frame * 40);
        }
        assertThat(burst.stamina()).as("a charge spends four points a frame").isLessThan(70f);
    }

    @Test
    void anExhaustedGladiatorSlowsToAWalkUntilItRecovers() {
        Body body = new Body(coliseum);
        body.place(-400, 0, 0f);
        Brain brain = new Brain(Brain.AMBUSH);
        brain.tire();
        brain.see(true, -300, 0, 0);
        brain.drive(body, 0);
        assertThat(brain.walk()).as("an empty gladiator cannot run").isLessThanOrEqualTo(Brain.WALK);

        for (int frame = 0; frame < 40; frame++) {
            brain.see(false, 0, 0, frame * 40);
            brain.drive(body, frame * 40);
        }
        assertThat(brain.stamina()).as("a walking or hidden gladiator recovers one point a frame, the first frame included")
                .isCloseTo(41f, within(0.01f));
    }

    @Test
    void smokeHidesWhatStandsBehindItButNotWhatIsAtArmsLength() {
        int east = 3;
        float bx = Braziers.X[east];
        assertThat(coliseum.clearSight(bx - 300, 0, bx + 300, 0)).as("no smoke yet").isTrue();

        coliseum.braziers().setMask(1 << east);
        assertThat(coliseum.clearSight(bx - 300, 0, bx + 300, 0)).as("through the cloud").isFalse();
        assertThat(Eyes.sees(coliseum, bx - 300, 0, 0f, bx + 300, 0)).isFalse();
        assertThat(coliseum.clearSight(bx - 50, 0, bx + 50, 0)).as("at arm's length inside it").isTrue();
        assertThat(coliseum.clearSight(bx - 300, 400, bx + 300, 400)).as("past the cloud").isTrue();
    }

    @Test
    void theBraziersTakeTurnsSoThereIsAlwaysSmokeSomewhere() {
        int seen = 0;
        for (int millis = 0; millis < Braziers.PERIOD_MILLIS; millis += 250) {
            int up = Braziers.maskAt(millis);
            assertThat(Integer.bitCount(up)).as("clouds up at %d ms", millis).isBetween(1, 2);
            seen = seen | up;
        }
        assertThat(seen).as("every brazier smokes once a period").isEqualTo((1 << Braziers.COUNT) - 1);
    }

    @Test
    void anAmbushHidesInSmokeRatherThanBehindAFartherPillar() {
        coliseum.braziers().setMask(1 << 3);
        Brain brain = new Brain(Brain.AMBUSH);
        Body body = new Body(coliseum);
        body.place(Braziers.X[3] + 340, 0, 0f);
        brain.reset(RIGHT, 0);

        Ambush.hide(brain, body, -1, 0);
        assertThat(brain.lair().pillar()).isEqualTo(Ambush.SMOKE_SPOT + 3);
        assertThat(brain.mode()).isEqualTo(Brain.HIDING);
    }

    @Test
    void aJavelinIsPickedUpThrownAndStrikesTheOpponent() {
        Match match = fight();
        Javelins javelins = match.javelins();
        int x = Javelins.START_X[0];
        int y = Javelins.START_Y[0];
        stand(match, LEFT, x, y, 0f, 0, 10_000);
        stand(match, RIGHT, x + 400, y, (float) Math.PI, 0, 10_000);
        int held = javelins.held(LEFT);
        assertThat(held).as("walked over it").isEqualTo(0);

        throwFrom(match, LEFT, x, y, 0f, 1, 10_100);
        assertThat(javelins.state(held)).isEqualTo(Javelins.FLYING);
        for (int now = 10_100; now <= 11_500; now += 40) {
            match.tick(now);
        }

        assertThat(match.side(RIGHT).health()).as("seen coming: single damage")
                .isEqualTo(Match.HEALTH - Javelins.DAMAGE);
        assertThat(javelins.state(held)).as("it falls where it struck").isEqualTo(Javelins.GROUND);
        assertThat(javelins.held(LEFT)).isNegative();
    }

    @Test
    void aJavelinThatHitsAPillarFallsAtItsFoot() {
        Match match = fight();
        Javelins javelins = match.javelins();
        stand(match, LEFT, Javelins.START_X[0], Javelins.START_Y[0], 0f, 0, 10_000);
        stand(match, RIGHT, 1000, 0, (float) Math.PI, 0, 10_000);
        throwFrom(match, LEFT, -1000, 0, 0f, 1, 10_100);
        for (int now = 10_100; now <= 11_500; now += 40) {
            match.tick(now);
        }

        float pillarEdge = -2 * Coliseum.SPACING - Coliseum.PILLAR_RADIUS;
        assertThat(javelins.state(0)).isEqualTo(Javelins.GROUND);
        assertThat(javelins.x(0)).isBetween(pillarEdge - Javelins.PICKUP, pillarEdge);
        assertThat(match.side(RIGHT).health()).isEqualTo(Match.HEALTH);
    }

    @Test
    void theWorldTellsWhereTheJavelinsAreButNotWhereTheOpponentCarriesOne() {
        Match match = fight();
        stand(match, LEFT, Javelins.START_X[0], Javelins.START_Y[0], 0f, 0, 10_000);
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];

        match.writeWorld(packet, LEFT, 10_000);
        Report report = new Report(coliseum);
        report.read(packet);
        assertThat(report.holding()).isTrue();
        assertThat(report.javelin(1)).isEqualTo(GladiatorProtocol.JAVELIN_GROUND);
        assertThat(report.javelinX(1)).isEqualTo(Javelins.START_X[1]);

        match.writeWorld(packet, RIGHT, 10_000);
        report.read(packet);
        assertThat(report.javelin(0)).isEqualTo(GladiatorProtocol.JAVELIN_OPPONENT);
        assertThat(report.javelinX(0)).as("kept secret").isZero();
    }

    @Test
    void aCpuWithAJavelinThrowsItAtAnOpponentInRange() {
        Brain brain = new Brain(Brain.AGGRESSIVE);
        Body body = new Body(coliseum);
        body.place(-1000, 154, 0f);
        brain.reset(LEFT, 0);
        brain.senses().arm(true, false, false);
        boolean thrown = false;
        for (int frame = 0; frame < 20 && !thrown; frame++) {
            brain.see(true, -500, 154, 2000 + frame * 40);
            brain.drive(body, 2000 + frame * 40);
            thrown = brain.senses().throwJavelin();
        }
        assertThat(thrown).isTrue();
    }

    @Test
    void aCrowdedViewWithSmokeAndJavelinsStillDrawsTheWholeOpponent() {
        Match match = fight();
        coliseum.braziers().setMask((1 << Braziers.COUNT) - 1);
        match.javelins().place(0, -1300, 800);
        match.javelins().place(1, 0, 1200);
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        match.writeWorld(packet, LEFT, 10_000);
        Report report = new Report(coliseum);
        report.read(packet);
        View view = new View(list, coliseum, report);
        Body body = new Body(coliseum);
        for (int x = -2200; x <= 2200; x += 200) {
            for (int y = -1400; y <= 1400; y += 200) {
                if (!coliseum.walkable(x, y)) {
                    continue;
                }
                for (int turn = 0; turn < 8; turn++) {
                    body.place(x, y, turn * (float) Math.PI / 4);
                    float ox = x + (float) Math.cos(body.angle()) * 150f;
                    float oy = y + (float) Math.sin(body.angle()) * 150f;
                    if (!coliseum.walkable(ox, oy) || coliseum.blocking(x, y, ox, oy, Coliseum.BODY) >= 0) {
                        continue;
                    }
                    view.build(body, true, ox, oy, 0f, Brain.AMBUSH, false, 0, 0);
                    assertThat(figureLines()).as("opponent from (%d, %d) turned %d", x, y, turn).isGreaterThan(10);
                    assertThat(list.size()).isLessThanOrEqualTo(ViewList.MAX_LINES);
                }
            }
        }
    }

    @Test
    void eachClassFightsWithItsOwnArms() {
        assertThat(Arms.reach(GladiatorClass.RETIARIUS)).as("trident").isGreaterThan(Arms.reach(GladiatorClass.MURMILLO));
        assertThat(Arms.swingMillis(GladiatorClass.THRAEX)).as("sica").isLessThan(Arms.swingMillis(GladiatorClass.MURMILLO));
        assertThat(Arms.speed(GladiatorClass.MURMILLO)).as("heavy scutum").isLessThan(1f);
        assertThat(Arms.halfView(GladiatorClass.SECUTOR)).as("helmet").isLessThan(Eyes.HALF_VIEW);
        assertThat(Arms.netMillis(GladiatorClass.SECUTOR)).isLessThan(Arms.netMillis(GladiatorClass.THRAEX));

        float front = 0f;
        float side = (float) Math.PI / 2;
        float behind = (float) Math.PI;
        assertThat(Arms.blow(10, GladiatorClass.SECUTOR, true, GladiatorClass.MURMILLO, front)).as("on the scutum").isEqualTo(8);
        assertThat(Arms.blow(10, GladiatorClass.SECUTOR, true, GladiatorClass.MURMILLO, behind)).as("round it").isEqualTo(10);
        assertThat(Arms.blow(10, GladiatorClass.THRAEX, true, GladiatorClass.RETIARIUS, side)).as("sica in the side").isEqualTo(15);
        assertThat(Arms.blow(10, GladiatorClass.THRAEX, false, GladiatorClass.RETIARIUS, side)).as("its javelin").isEqualTo(10);
        assertThat(Arms.blow(20, GladiatorClass.RETIARIUS, false, GladiatorClass.SECUTOR, behind)).as("helmet").isEqualTo(18);
    }

    @Test
    void theRetiariusTridentReachesWhereAGladiusDoesNot() {
        Match match = fight();
        standAs(match, LEFT, GladiatorClass.RETIARIUS, -200, 62, 0f, 0, 0, 10_000);
        standAs(match, RIGHT, GladiatorClass.THRAEX, -100, 62, (float) Math.PI, 0, 0, 10_000);
        standAs(match, LEFT, GladiatorClass.RETIARIUS, -200, 62, 0f, 1, 0, 10_100);
        assertThat(match.side(RIGHT).health()).isEqualTo(Match.HEALTH - Arms.damage(GladiatorClass.RETIARIUS));

        standAs(match, RIGHT, GladiatorClass.THRAEX, -100, 62, (float) Math.PI, 1, 0, 10_200);
        assertThat(match.side(LEFT).health()).as("a sica 100 away is out of reach").isEqualTo(Match.HEALTH);
    }

    @Test
    void aNetRootsTheOpponentOnceARound() {
        Match match = fight();
        Side caster = match.side(LEFT);
        Side victim = match.side(RIGHT);
        standAs(match, LEFT, GladiatorClass.RETIARIUS, -400, 62, 0f, 0, 0, 10_000);
        standAs(match, RIGHT, GladiatorClass.THRAEX, -200, 62, (float) Math.PI, 0, 0, 10_000);
        assertThat(caster.netReady()).isTrue();
        assertThat(victim.netReady()).as("only a retiarius has one").isFalse();

        standAs(match, LEFT, GladiatorClass.RETIARIUS, -400, 62, 0f, 0, 1, 10_100);
        assertThat(caster.netReady()).isFalse();
        assertThat(victim.rooted(10_100)).isTrue();
        standAs(match, RIGHT, GladiatorClass.THRAEX, -150, 62, 0f, 0, 0, 10_500);
        assertThat(victim.x()).as("caught: it cannot walk").isEqualTo(-200);
        assertThat(victim.facing()).as("but it can turn").isCloseTo(0f, within(0.01f));
        standAs(match, RIGHT, GladiatorClass.THRAEX, -150, 62, (float) Math.PI, 1, 0, 10_600);
        assertThat(caster.health()).as("nor strike").isEqualTo(Match.HEALTH);

        standAs(match, RIGHT, GladiatorClass.THRAEX, -150, 62, 0f, 0, 0, 10_100 + Arms.NET_MILLIS + 50);
        assertThat(victim.x()).as("free again").isEqualTo(-150);
        standAs(match, LEFT, GladiatorClass.RETIARIUS, -400, 62, 0f, 0, 2, 12_000);
        assertThat(victim.rooted(12_000)).as("one net a round").isFalse();
    }

    @Test
    void aSecutorSlipsTheNetSoonerButSeesLess() {
        Match match = fight();
        standAs(match, LEFT, GladiatorClass.RETIARIUS, -400, 62, 0f, 0, 0, 10_000);
        standAs(match, RIGHT, GladiatorClass.SECUTOR, -200, 62, (float) Math.PI, 0, 0, 10_000);
        standAs(match, LEFT, GladiatorClass.RETIARIUS, -400, 62, 0f, 0, 1, 10_100);
        assertThat(match.side(RIGHT).rooted(10_100 + Arms.NET_MILLIS / 2 + 10)).isFalse();

        float wide = (Arms.SECUTOR_VIEW + Eyes.HALF_VIEW) / 2;
        float tx = -200 + (float) Math.cos(Math.PI - wide) * 150;
        float ty = 62 + (float) Math.sin(Math.PI - wide) * 150;
        assertThat(match.watches(RIGHT, tx, ty)).as("outside the helmet's slit").isFalse();
        assertThat(match.watches(LEFT, -400 + 150 * (float) Math.cos(wide), 62 + 150 * (float) Math.sin(wide)))
                .as("an open-faced retiarius sees it").isTrue();
    }

    @Test
    void aCpuRetiariusCastsItsNetAndANettedCpuStandsStill() {
        Brain caster = new Brain(Brain.AGGRESSIVE, GladiatorClass.RETIARIUS);
        Body body = new Body(coliseum);
        body.place(-400, 62, 0f);
        caster.reset(LEFT, 0);
        boolean cast = false;
        for (int frame = 0; frame < 10 && !cast; frame++) {
            caster.see(true, -200, 62, frame * 40);
            caster.drive(body, frame * 40);
            cast = caster.senses().castNet();
        }
        assertThat(cast).isTrue();
        assertThat(caster.senses().netReady()).isFalse();

        Brain netted = new Brain(Brain.AGGRESSIVE, GladiatorClass.THRAEX);
        Body still = new Body(coliseum);
        still.place(600, 1000, 0f);
        netted.reset(RIGHT, 0);
        netted.senses().arm(false, true, false);
        for (int frame = 0; frame < 20; frame++) {
            netted.see(true, 1200, 1000, frame * 40);
            netted.drive(still, frame * 40);
        }
        assertThat(still.x()).isEqualTo(600f);
        assertThat(still.y()).isEqualTo(1000f);
    }

    @Test
    void theLionComesOutOnTheFightsClockAndGoesHome() {
        Match match = fight();
        int start = Match.COUNTDOWN_MILLIS;
        boolean seenOut = false;
        boolean seenHome = false;
        for (int now = start; now < start + Lion.FIRST_MILLIS + Lion.HUNT_MILLIS + 25_000; now += 40) {
            if (now % 1000 == 0) {
                standAs(match, LEFT, FIGHTER, -1200, 0, 0f, 0, 0, now);
                standAs(match, RIGHT, FIGHTER, 1200, 0, (float) Math.PI, 0, 0, now);
            }
            match.tick(now);
            int fightMillis = now - start;
            if (fightMillis < Lion.FIRST_MILLIS) {
                assertThat(match.lion().active()).as("still caged at %d ms", fightMillis).isFalse();
            } else if (fightMillis < Lion.FIRST_MILLIS + Lion.HUNT_MILLIS) {
                assertThat(match.lion().active()).as("hunting at %d ms", fightMillis).isTrue();
                seenOut = true;
            } else if (seenOut && !match.lion().active()) {
                seenHome = true;
            }
        }
        assertThat(seenOut).isTrue();
        assertThat(seenHome).as("back in its cage").isTrue();
    }

    @Test
    void theLionBitesTheNearestGladiatorAndCanDecideTheBout() {
        Match match = fight();
        standAs(match, LEFT, FIGHTER, 0, 800, (float) Math.PI / 2, 0, 0, 10_000);
        standAs(match, RIGHT, FIGHTER, 1200, 0, (float) Math.PI, 0, 0, 10_000);
        match.lion().loose(0, 850, 10_000);
        match.lion().update(match, 40, Lion.FIRST_MILLIS + 100, 10_000);
        assertThat(match.side(LEFT).health()).as("seen coming").isEqualTo(Match.HEALTH - Lion.DAMAGE);
        assertThat(match.side(RIGHT).health()).isEqualTo(Match.HEALTH);
        assertThat(match.lastByLion()).isTrue();

        match.lion().update(match, 40, Lion.FIRST_MILLIS + 140, 10_040);
        assertThat(match.side(LEFT).health()).as("one bite each %d ms", Lion.BITE_MILLIS)
                .isEqualTo(Match.HEALTH - Lion.DAMAGE);

        // Down to 5 health: the next bite is the lion's kill.
        match.side(LEFT).takeBlow(Match.HEALTH - Lion.DAMAGE - 5, false);
        match.lion().update(match, 40, Lion.FIRST_MILLIS + 2000, 10_000 + Lion.BITE_MILLIS);
        assertThat(match.phase()).isEqualTo(Match.OVER);
        assertThat(match.winner()).as("the lion's kill is the other gladiator's win").isEqualTo(RIGHT);
    }

    @Test
    void everyGladiatorIsToldWhereTheLionIs() {
        Match match = fight();
        match.lion().loose(300, -900, 10_000);
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        match.writeWorld(packet, RIGHT, 10_000);
        Report report = new Report(coliseum);
        report.read(packet);
        assertThat(report.lion()).isTrue();
        assertThat(report.lionX()).isEqualTo(300f);
        assertThat(report.lionY()).isEqualTo(-900f);
    }

    @Test
    void aCpuRunsFromANearLion() {
        Brain brain = new Brain(Brain.AGGRESSIVE, GladiatorClass.THRAEX);
        Body body = new Body(coliseum);
        body.place(-1000, 154, 0f);
        brain.reset(LEFT, 0);
        brain.senses().lion(true, -900, 154);
        boolean fled = false;
        for (int frame = 0; frame < 60; frame++) {
            brain.drive(body, frame * 40);
            fled = fled || brain.mode() == Brain.FLEEING;
        }
        assertThat(fled).isTrue();
        assertThat(body.distanceTo(-900, 154)).as("farther from the lion").isGreaterThan(200f);
    }

    @Test
    void soloRandomlyBuildsADifferentCpuOpponent() {
        FighterProfile player = new FighterProfile(FighterProfile.FLAMMA, GladiatorClass.RETIARIUS);
        Random.seed(7);

        for (int selection = 0; selection < 20; selection++) {
            FighterProfile opponent = FighterProfile.randomOpponent(player);
            assertThat(opponent.name()).isBetween(0, FighterProfile.NAMES - 1).isNotEqualTo(player.name());
            assertThat(opponent.kind()).isBetween(GladiatorClass.MURMILLO, GladiatorClass.SECUTOR);
            assertThat(opponent.behaviour()).as("the class decides the style")
                    .isEqualTo(FighterProfile.styleOf(opponent.kind()));
        }
        assertThat(SoloPresentation.RESULT_MILLIS).isEqualTo(5000);
        assertThat(SoloPresentation.onPlay(20, 206)).isTrue();
        assertThat(SoloPresentation.onPlay(299, 233)).isTrue();
        assertThat(SoloPresentation.onPlay(19, 220)).isFalse();
        assertThat(SoloPresentation.onPlay(300, 220)).isFalse();
    }

    @Test
    void soloShowsTheArmsOfTheSelectedClassInFirstPerson() {
        Body body = new Body(coliseum);
        body.spawn(0);
        View view = new View(list, coliseum, reportWith(GladiatorProtocol.NET_READY));
        view.build(body, false, 0, 0, 0, Brain.AGGRESSIVE, false, 0, 0, GladiatorClass.RETIARIUS);
        assertThat(linesIn(TftTouchShield.CYAN)).as("retiarius net").isGreaterThanOrEqualTo(6);

        view = new View(list, coliseum, reportWith(0));
        view.build(body, false, 0, 0, 0, Brain.AGGRESSIVE, false, 0, 0, GladiatorClass.RETIARIUS);
        assertThat(linesIn(TftTouchShield.CYAN)).as("net already cast").isZero();

        view.build(body, false, 0, 0, 0, Brain.AGGRESSIVE, false, 0, 0, GladiatorClass.THRAEX);
        assertThat(linesIn(TftTouchShield.YELLOW)).as("Thraex parmula").isGreaterThanOrEqualTo(5);
    }

    @Test
    void everyClassBuildsAThreeDimensionalWireframeInsideTheLeftPanel() {
        ClassWireframe wireframe = new ClassWireframe(list);
        for (int kind = GladiatorClass.MURMILLO; kind < GladiatorClass.CLASSES; kind++) {
            wireframe.build(kind, 0f);
            assertThat(list.size()).as(Opening.className(kind)).isBetween(35, ViewList.MAX_LINES - 1);
            for (int line = 0; line < list.size(); line++) {
                assertThat(list.built(line, 0)).isBetween(0, 189);
                assertThat(list.built(line, 1)).isBetween(1, ViewList.VIEW_BOTTOM - 1);
                assertThat(list.built(line, 2)).isBetween(0, 189);
                assertThat(list.built(line, 3)).isBetween(1, ViewList.VIEW_BOTTOM - 1);
            }
        }
        wireframe.build(GladiatorClass.MURMILLO, 0f);
        int frontFacingX = list.built(0, 0);
        wireframe.build(GladiatorClass.MURMILLO, (float) (Math.PI / 2));
        assertThat(list.built(0, 0)).as("the projected model turns around its vertical axis")
                .isNotEqualTo(frontFacingX);
    }

    @Test
    void shieldsAreStandingRatherThanLandscapeRectangles() {
        assertStandingShield(GladiatorClass.MURMILLO, TftTouchShield.ORANGE);
        assertStandingShield(GladiatorClass.THRAEX, TftTouchShield.YELLOW);
    }

    private void assertStandingShield(int kind, int color) {
        new ClassWireframe(list).build(kind, 0f);
        int minX = ViewList.WIDTH;
        int minY = ViewList.HEIGHT;
        int maxX = 0;
        int maxY = 0;
        int shieldLines = 0;
        for (int line = 0; line < list.size(); line++) {
            if ((list.built(line, 4) & 0xFFFF) == color) {
                minX = Math.min(minX, Math.min(list.built(line, 0), list.built(line, 2)));
                minY = Math.min(minY, Math.min(list.built(line, 1), list.built(line, 3)));
                maxX = Math.max(maxX, Math.max(list.built(line, 0), list.built(line, 2)));
                maxY = Math.max(maxY, Math.max(list.built(line, 1), list.built(line, 3)));
                shieldLines++;
            }
        }
        assertThat(shieldLines).isPositive();
        assertThat(maxY - minY).as(Opening.className(kind) + " shield height").isGreaterThan(maxX - minX);
    }

    private int figureLines() {
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            if ((list.built(i, 4) & 0xFFFF) == Figure.AMBUSH_COLOR) {
                count++;
            }
        }
        return count;
    }

    private int linesIn(int color) {
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            if ((list.built(i, 4) & 0xFFFF) == color) {
                count++;
            }
        }
        return count;
    }

    /** A report read from a WORLD packet whose flags are {@code flags}, with nothing else set. */
    private Report reportWith(int flags) {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(packet, GladiatorProtocol.WORLD, 0, 1);
        packet[GladiatorProtocol.FLAGS] = (byte) flags;
        Report report = new Report(coliseum);
        report.read(packet);
        return report;
    }

    /** Two gladiators joined on the grid, the countdown over, and the javelins where the tests know them. */
    private Match fight() {
        Match match = new Match(coliseum);
        match.join(11, Brain.AGGRESSIVE, SOURCE, 0);
        match.join(22, Brain.AMBUSH, SOURCE, 0);
        match.tick(Match.COUNTDOWN_MILLIS);
        assertThat(match.phase()).isEqualTo(Match.FIGHT);
        for (int j = 0; j < Javelins.COUNT; j++) {
            match.javelins().place(j, Javelins.START_X[j], Javelins.START_Y[j]);
        }
        return match;
    }

    @Test
    void everyRoundStandsEachJavelinSomewhereNewInItsOwnHalf() {
        java.util.Set<Long> places = new java.util.HashSet<>();
        Javelins javelins = new Javelins();
        for (int seed = 1; seed <= 30; seed++) {
            Random.seed(seed);
            Coliseum arena = new Coliseum();
            arena.build();
            javelins.reset(arena);
            for (int j = 0; j < Javelins.COUNT; j++) {
                assertThat(javelins.state(j)).isEqualTo(Javelins.GROUND);
                assertThat(javelins.x(j) * (j == 0 ? -1 : 1)).as("javelin %d in its own half", j).isPositive();
                assertThat(arena.walkable(javelins.x(j), javelins.y(j))).isTrue();
                assertThat(arena.pillarAt(javelins.x(j), javelins.y(j), Coliseum.BODY)).isNegative();
            }
            places.add((long) javelins.x(0) * 100_003L + (long) javelins.y(1));
        }
        assertThat(places).as("somewhere new each round").hasSizeGreaterThan(25);
    }

    /** The gladiator in {@code slot} reports where it stands and how many strikes it swung. */
    private static void stand(Match match, int slot, int x, int y, float angle, int strikes, int now) {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(packet, GladiatorProtocol.STATE, slot, match.side(slot).node());
        packet[GladiatorProtocol.STATE_ROUND] = (byte) match.round();
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.X, x);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.Y, y);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(angle));
        packet[GladiatorProtocol.STRIKES] = (byte) strikes;
        packet[GladiatorProtocol.STATE_KIND] = (byte) FIGHTER;
        match.state(slot, packet, now);
    }

    /** A gladiator of {@code kind} reports where it stands, the strikes it swung and the nets it cast. */
    private static void standAs(Match match, int slot, int kind, int x, int y, float angle, int strikes, int nets,
                                int now) {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(packet, GladiatorProtocol.STATE, slot, match.side(slot).node());
        packet[GladiatorProtocol.STATE_ROUND] = (byte) match.round();
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.X, x);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.Y, y);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(angle));
        packet[GladiatorProtocol.STRIKES] = (byte) strikes;
        packet[GladiatorProtocol.STATE_KIND] = (byte) kind;
        packet[GladiatorProtocol.NETS] = (byte) nets;
        match.state(slot, packet, now);
    }

    /** The gladiator in {@code slot} reports where it stands and how many javelins it threw. */
    private static void throwFrom(Match match, int slot, int x, int y, float angle, int throwsMade, int now) {
        byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(packet, GladiatorProtocol.STATE, slot, match.side(slot).node());
        packet[GladiatorProtocol.STATE_ROUND] = (byte) match.round();
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.X, x);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.Y, y);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(angle));
        packet[GladiatorProtocol.THROWS] = (byte) throwsMade;
        packet[GladiatorProtocol.STATE_KIND] = (byte) FIGHTER;
        match.state(slot, packet, now);
    }

    /**
     * The arena and both gladiators' boards in one loop: each frame every gladiator thinks and moves on what its last
     * {@code WORLD} said; ten times a second they report to the arena and the arena reports back.
     */
    private final class Duel {
        final Match match = new Match(coliseum);
        private final Body[] bodies = {new Body(coliseum), new Body(coliseum)};
        private final Brain[] brains = {new Brain(Brain.AGGRESSIVE), new Brain(Brain.AMBUSH)};
        private final Report[] reports = {new Report(coliseum), new Report(coliseum)};
        private final int[] strikes = new int[2];
        private final int[] swungAt = {-10_000, -10_000};
        private final int[] rounds = {-1, -1};
        private final int[] hits = {-1, -1};
        private final byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];

        int play(int millis) {
            match.join(11, Brain.AGGRESSIVE, SOURCE, 0);
            match.join(22, Brain.AMBUSH, SOURCE, 0);
            for (int now = 0; now < millis; now += 40) {
                match.tick(now);
                if (match.phase() == Match.OVER) {
                    return match.winner();
                }
                if (now % 120 == 0) {
                    for (int slot = 0; slot < 2; slot++) {
                        match.writeWorld(packet, slot, now);
                        world(slot, now);
                    }
                }
                for (int slot = 0; slot < 2; slot++) {
                    boolean swung = false;
                    if (match.phase() == Match.FIGHT) {
                        Body body = bodies[slot];
                        Report report = reports[slot];
                        body.seeOpponent(report.visible(), report.opponentX(), report.opponentY());
                        brains[slot].see(report.visible(), report.opponentX(), report.opponentY(), now);
                        brains[slot].drive(body, now);
                        if (brains[slot].strike() && now - swungAt[slot] >= 700) {
                            swungAt[slot] = now;
                            strikes[slot] = (strikes[slot] + 1) & 0xFF;
                            swung = true;
                        }
                    }
                    if (swung || now % 120 == 0) {
                        stand(match, slot, Math.round(bodies[slot].x()), Math.round(bodies[slot].y()),
                                bodies[slot].angle(), strikes[slot], now);
                    }
                }
            }
            return Match.NOBODY;
        }

        private void world(int slot, int now) {
            Report report = reports[slot];
            report.read(packet);
            if (report.round() != rounds[slot]) {
                rounds[slot] = report.round();
                bodies[slot].spawn(slot);
                brains[slot].reset(slot, now);
            }
            if (hits[slot] >= 0 && report.hits() != hits[slot]) {
                brains[slot].hurt(now);
            }
            hits[slot] = report.hits();
        }
    }
}
