package io.github.jabrena.juno.games.startrek;

/** One game's score, best score, sector and frame, and whether the Enterprise or the starbase is lost. */
final class Session {
    static int score;
    static int best;
    static int sector;
    static int frame;
    static boolean dead;
    static boolean baseLost;

    private Session() {
    }

    static void newGame() {
        score = 0;
        sector = 1;
        Enterprise.newGame();
    }

    static void endGame() {
        if (score > best) {
            best = score;
        }
    }
}
