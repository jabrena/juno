package io.github.jabrena.juno.games.starwars;

/**
 * One game's progress: score, shields, the wave and phase being flown, and the phase's clock (frames,
 * distance travelled and the countdown to the next spawn).
 */
final class Session {
    static final int START_SHIELDS = 6;
    static final int MAX_SHIELDS = 9;

    // Scoring, in the arcade's style.
    static final int FIREBALL_POINTS = 33;
    static final int TURRET_POINTS = 100;
    static final int TOWER_POINTS = 200;
    static final int TIE_POINTS = 1000;
    static final int PORT_POINTS = 25000;
    static final int ALL_TOWERS_BONUS = 50000;
    static final int SHIELD_BONUS = 5000;
    static final int FORCE_BONUS = 50000;

    static int score;
    static int best;
    static int shields;
    static int wave;
    static Phase phase = Phase.SPACE;
    static int frame;
    static boolean dead;
    static int travel;
    static int spawnCountdown;

    private Session() {
    }

    static void newGame() {
        score = 0;
        shields = START_SHIELDS;
        wave = 1;
    }

    static void endGame() {
        if (score > best) {
            best = score;
        }
    }

    /** One hit on the X-wing: a shield is lost, or the game when none is left. */
    static void shieldHit() {
        if (shields == 0) {
            dead = true;
            return;
        }
        shields = shields - 1;
        Hud.flashHit();
    }

    /** How far the world moves towards the camera each frame. */
    static int flightSpeed() {
        // An explicit default (the trench) spares javac's MatchException branch, which Juno cannot lower.
        return switch (phase) {
            case SPACE -> 0;
            case SURFACE -> 26 + 2 * Math.min(wave, 8);
            default -> 34 + 3 * Math.min(wave, 8);
        };
    }
}
