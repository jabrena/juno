package io.github.jabrena.juno.games.empirestrikesback;

/**
 * One game's progress: score, shields, the JEDI letters, the wave and round being flown, and the
 * round's clock (frames, distance travelled and the countdown to the next spawn).
 */
final class Session {
    static final int START_SHIELDS = 6;
    static final int MAX_SHIELDS = 9;

    // Scoring.
    static final int FIREBALL_POINTS = 33;
    static final int PROBE_POINTS = 500;
    static final int ATST_POINTS = 1000;
    static final int ATAT_POINTS = 5000;
    static final int ASTEROID_POINTS = 100;
    static final int TIE_POINTS = 1000;
    static final int ALL_ATATS_BONUS = 20000;
    static final int JEDI_BONUS = 50000;

    static int score;
    static int best;
    static int shields;
    static int wave;
    static Round round = Round.PROBES;
    static int frame;
    static boolean dead;
    /** JEDI letters earned so far (0-4). */
    static int jedi;
    static boolean hitThisRound;
    static int travel;
    static int spawnCountdown;

    private Session() {
    }

    static void newGame() {
        score = 0;
        shields = START_SHIELDS;
        wave = 1;
        jedi = 0;
    }

    /** A completed wave restores a shield. */
    static void nextWave() {
        shields = Math.min(shields + 1, MAX_SHIELDS);
        wave = wave + 1;
    }

    static void endGame() {
        if (score > best) {
            best = score;
        }
    }

    /** One hit on your craft: a shield is lost, or the game when none is left. */
    static void shieldHit() {
        hitThisRound = true;
        if (shields == 0) {
            dead = true;
            return;
        }
        shields = shields - 1;
        Hud.flashHit();
    }

    /** How far the world moves towards the camera each frame. */
    static int flightSpeed() {
        if (round == Round.ASTEROIDS) {
            return 30 + 2 * Math.min(wave, 8);
        }
        return 18 + Math.min(wave, 8);
    }

    /** The letters of JEDI earned so far, the rest as dashes. */
    static String jediWord() {
        if (jedi >= 4) {
            return "J E D I";
        }
        if (jedi == 3) {
            return "J E D -";
        }
        if (jedi == 2) {
            return "J E - -";
        }
        if (jedi == 1) {
            return "J - - -";
        }
        return "- - - -";
    }
}
