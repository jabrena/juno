package io.github.jabrena.juno.games.doom;

/** Rules for finishing one map while playing an episode from the WAD. */
final class Campaign {
    private Campaign() {
    }

    /** An exit was reached, or every monster is dead on a boss map that has no exit line. */
    static boolean mapWon(short[] monsters) {
        if (Player.atExit()) {
            return true;
        }
        if (World.exitLine >= 0 || !World.fromWad || World.monsters == 0) {
            return false;
        }
        for (int i = 0; i < World.monsters; i++) {
            if (monsters[i * Monsters.STRIDE + Monsters.STATE] != Monsters.DEAD) {
                return false;
            }
        }
        return true;
    }

    /** Whether the current map is the normal ending of a WAD episode. */
    static boolean episodeFinished() {
        return World.fromWad && World.map >= World.LAST_MAP;
    }

    /** A DOOM intermission percentage, safe for maps with no entries and capped at 100%. */
    static int percent(int found, int total) {
        return total == 0 ? 0 : Math.min(100, 100 * found / total);
    }

    /** Number of countable map items collected in the current attempt. */
    static int itemsFound(byte[] taken, int items) {
        int found = 0;
        for (int item = 0; item < items; item++) {
            if (taken[item] != 0) {
                found = found + 1;
            }
        }
        return found;
    }
}
