package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Random;

/** A player's historical name, gladiator class and CPU fighting style. */
final class FighterProfile {
    static final int SPARTACUS = 0;
    static final int CRIXUS = 1;
    static final int FLAMMA = 2;
    static final int PRISCUS = 3;
    static final int NAMES = 4;

    final int name;
    final int kind;
    final int behaviour;

    FighterProfile(int name, int kind, int behaviour) {
        this.name = name;
        this.kind = kind;
        this.behaviour = behaviour;
    }

    String displayName() {
        return name(name);
    }

    int color() {
        return Opening.classColor(kind);
    }

    /** Creates a CPU opponent with a different historical name and independently random class and style. */
    static FighterProfile randomOpponent(FighterProfile player) {
        int opponentName = Random.nextInt(NAMES - 1);
        if (opponentName >= player.name) {
            opponentName = opponentName + 1;
        }
        int opponentClass = Random.nextInt(Opening.CLASSES);
        int opponentBehaviour = Random.nextInt(2) == 0 ? Brain.AGGRESSIVE : Brain.AMBUSH;
        return new FighterProfile(opponentName, opponentClass, opponentBehaviour);
    }

    static String name(int name) {
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

    static String history(int name) {
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
