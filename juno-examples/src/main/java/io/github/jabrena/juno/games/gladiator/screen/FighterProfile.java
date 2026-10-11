package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;

/**
 * A player's historical name and gladiator class. The class decides the CPU's fighting style: a RETIARIUS lays its
 * net from cover as an AMBUSH gladiator, and every other class presses the attack as an AGGRESSIVE one.
 */
public record FighterProfile(int name, int kind) {
    public static final int SPARTACUS = 0;
    public static final int CRIXUS = 1;
    public static final int FLAMMA = 2;
    public static final int PRISCUS = 3;
    public static final int NAMES = 4;

    /** The fighting style its class fights with. */
    public int behaviour() {
        return styleOf(kind);
    }

    /** The fighting style a class fights with. */
    public static int styleOf(int kind) {
        return kind == GladiatorClass.RETIARIUS ? Brain.AMBUSH : Brain.AGGRESSIVE;
    }

    public String displayName() {
        return name(name);
    }

    public int color() {
        return Opening.classColor(kind);
    }

    /** Creates a CPU opponent with a different historical name and a random class. */
    public static FighterProfile randomOpponent(FighterProfile player) {
        int opponentName = Random.nextInt(NAMES - 1);
        if (opponentName >= player.name()) {
            opponentName = opponentName + 1;
        }
        return new FighterProfile(opponentName, Random.nextInt(GladiatorClass.CLASSES));
    }

    public static String name(int name) {
        if (name == CRIXUS) {
            return "CRIXUS";
        }
        if (name == FLAMMA) {
            return "FLAMMA";
        }
        if (name == PRISCUS) {
            return "PRISCUS";
        }
        return "SPARTACUS";
    }

    public static String history(int name) {
        if (name == CRIXUS) {
            return "GALLIC CHAMPION";
        }
        if (name == FLAMMA) {
            return "SYRIAN VETERAN";
        }
        if (name == PRISCUS) {
            return "FAMOUS DUELIST";
        }
        return "THRACIAN REBEL";
    }
}
