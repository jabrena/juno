package io.github.jabrena.juno.games.empirestrikesback;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.ATAT_HEAD_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.ATAT_HITS;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.ATST_HEAD_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_FLAG;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_HP;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SR;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SY;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TIMER;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_VZ;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.GROUND;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATAT;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATST;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_FIREBALL;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The walkers: AT-AT walkers stride towards the rebel base. Their armor stops your lasers except at
 * the head, which takes three hits; the smaller AT-STs fall to one. Both fire back. Bringing down
 * every AT-AT pays a bonus. The walkers are drawn here too, for the round and the opening.
 */
final class WalkersRound {
    private static final int WALKER = 0xC618;

    private static int walkersToSpawn;
    private static int atatsTotal;
    static int atatsDown;

    private WalkersRound() {
    }

    static void start() {
        atatsTotal = Math.min(2 + Session.wave, 5);
        walkersToSpawn = atatsTotal + Math.min(2 + Session.wave, 6);
        atatsDown = 0;
    }

    static void announce() {
        Hud.showCentered("THE WALKERS", 104, 2, TftTouchShield.YELLOW);
        Hud.showCentered("Hit the AT-AT heads", 136, 1, TftTouchShield.WHITE);
    }

    static boolean isOver(int[] ents) {
        int threats = Entities.count(ents, T_FIREBALL) + Entities.count(ents, T_DEBRIS);
        return walkersToSpawn == 0 && Entities.count(ents, T_ATAT) + Entities.count(ents, T_ATST) + threats == 0;
    }

    static int atatsLeft() {
        return Math.max(0, atatsTotal - atatsDown);
    }

    static boolean allAtatsDown() {
        return atatsDown == atatsTotal;
    }

    /** Called when the spawn countdown runs out. */
    static void spawn(int[] ents) {
        Session.spawnCountdown = 10;
        int walking = Entities.count(ents, T_ATAT) + Entities.count(ents, T_ATST);
        if (walkersToSpawn > 0 && walking < 3) {
            spawnWalker(ents);
            Session.spawnCountdown = Math.max(40, Random.nextInt(60, 110) - 4 * Session.wave);
        }
    }

    private static void spawnWalker(int[] ents) {
        int slot = Entities.freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        // AT-ATs first, then AT-STs mixed in.
        boolean atat = atatsTotal - atatsDown - Entities.count(ents, T_ATAT) > 0
                && (walkersToSpawn > Math.min(Session.wave, 4) || Random.nextInt(2) == 0);
        ents[b + E_TYPE] = atat ? T_ATAT : T_ATST;
        int side = Random.nextInt(2) == 0 ? -1 : 1;
        ents[b + E_X] = Camera.camX + side * Random.nextInt(150, 520);
        if (atat) {
            // AT-ATs stride across your path, towards the middle.
            ents[b + E_FLAG] = -side;
            ents[b + E_VX] = -side * 3;
        }
        ents[b + E_Y] = GROUND;
        ents[b + E_Z] = Camera.FAR;
        // Walkers advance slowly; the snowspeeder closes in on them.
        ents[b + E_VZ] = Session.flightSpeed() - (atat ? 6 : 10);
        ents[b + E_HP] = atat ? ATAT_HITS : 1;
        ents[b + E_TIMER] = Random.nextInt(10, 40);
        walkersToSpawn = walkersToSpawn - 1;
    }

    static void walkerFires(int[] ents, int b) {
        int z = ents[b + E_Z];
        if (z < 450 || z > 2500) {
            return;
        }
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            boolean atat = ents[b + E_TYPE] == T_ATAT;
            ents[b + E_TIMER] = Math.max(34, Random.nextInt(atat ? 70 : 90, 140) - 4 * Session.wave);
            int headX = atat ? ents[b + E_X] + (ents[b + E_FLAG] >= 0 ? 175 : -175) : ents[b + E_X];
            Entities.fireball(ents, headX, atat ? ATAT_HEAD_Y : ATST_HEAD_Y, z);
        }
    }

    /**
     * An AT-AT seen side-on as it strides across your path: a long box body on four legs that step
     * in turn, and a boxy head out in front, its only weak spot. {@code E_FLAG} is the direction it
     * walks (+1 or -1 along x).
     */
    static void drawAtat(short[] lines, int[] ents, int b) {
        int x = ents[b + E_X];
        int z = ents[b + E_Z];
        int dir = ents[b + E_FLAG] >= 0 ? 1 : -1;
        int bodyLow = GROUND + 150;
        int bodyHigh = GROUND + 230;
        Camera.box(lines, x - 110, bodyLow, z - 45, x + 110, bodyHigh, z + 45, WALKER);
        // Neck and head, in front of the body.
        int headX = x + dir * 175;
        Camera.line3(lines, x + dir * 110, bodyLow + 40, z, headX - dir * 30, ATAT_HEAD_Y, z, WALKER);
        int headColor = ents[b + E_HP] < ATAT_HITS && (Session.frame & 2) == 0 ? TftTouchShield.RED : SceneRenderer.WALKER_HEAD;
        Camera.box(lines, headX - 32, ATAT_HEAD_Y - 22, z - 24, headX + 32, ATAT_HEAD_Y + 22, z + 24, headColor);
        int step = (Session.frame + b) % 32;
        for (int leg = 0; leg < 4; leg++) {
            int legX = x + ((leg & 1) == 0 ? -80 : 80);
            int legZ = z + ((leg & 2) == 0 ? -40 : 40);
            // Diagonal pairs step together, half a cycle apart.
            int phase = (step + ((leg == 0 || leg == 3) ? 0 : 16)) % 32;
            int swing = dir * (phase < 16 ? phase * 3 - 24 : (32 - phase) * 3 - 24);
            int knee = GROUND + 75 + (phase < 16 ? 12 : 0);
            Camera.line3(lines, legX, bodyLow, legZ, legX + swing / 2, knee, legZ, WALKER);
            Camera.line3(lines, legX + swing / 2, knee, legZ, legX + swing, GROUND, legZ, WALKER);
            Camera.line3(lines, legX + swing - 12, GROUND, legZ, legX + swing + 12, GROUND, legZ, WALKER);
        }
        ents[b + E_SX] = Camera.projectX(headX, z);
        ents[b + E_SY] = Camera.projectY(ATAT_HEAD_Y, z);
        ents[b + E_SR] = Camera.scale(44, z) + 5;
    }

    /** An AT-ST: a small box head on two bird-like legs. One hit brings it down. */
    static void drawAtst(short[] lines, int[] ents, int b) {
        int x = ents[b + E_X];
        int z = ents[b + E_Z];
        Camera.box(lines, x - 26, ATST_HEAD_Y - 20, z - 26, x + 26, ATST_HEAD_Y + 20, z + 26, SceneRenderer.WALKER_HEAD);
        int step = (Session.frame + b) % 20;
        for (int leg = -1; leg <= 1; leg = leg + 2) {
            int phase = (step + (leg < 0 ? 0 : 10)) % 20;
            int swing = phase < 10 ? phase * 5 - 25 : (20 - phase) * 5 - 25;
            int kneeZ = z + 26;
            Camera.line3(lines, x + leg * 18, ATST_HEAD_Y - 20, z, x + leg * 22, GROUND + 60, kneeZ, WALKER);
            Camera.line3(lines, x + leg * 22, GROUND + 60, kneeZ, x + leg * 18, GROUND, z + swing, WALKER);
            Camera.line3(lines, x + leg * 18 - 12, GROUND, z + swing, x + leg * 18 + 12, GROUND, z + swing, WALKER);
        }
        ents[b + E_SX] = Camera.projectX(x, z);
        ents[b + E_SY] = Camera.projectY(ATST_HEAD_Y, z);
        ents[b + E_SR] = Camera.scale(40, z) + 5;
    }
}
