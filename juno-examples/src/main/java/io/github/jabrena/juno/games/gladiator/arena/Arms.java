package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.games.gladiator.screen.Opening;

/**
 * What each class of gladiator fights with, by its {@link Opening} kind. The RETIARIUS reaches farthest with its
 * trident, and once a round casts its net, which tangles the opponent so it can neither walk nor strike for a moment.
 * The MURMILLO is slow, but its big scutum takes a quarter off a blow from in front. The THRAEX swings fastest, and
 * its curved sica bites half again as deep into a victim's side. The SECUTOR's helmet takes a tenth off every blow
 * and slips a net sooner, but narrows its view, so it is surprised more often.
 *
 * <p>The numbers were tuned on CPU-against-CPU bouts so that no class wins much more than half of them.
 */
public final class Arms {
    /** Not a gladiator's class: the lion's claws. */
    public static final int CLAWS = -1;

    private static final int[] REACH = {80, 80, 110, 80};
    private static final int[] DAMAGE = {10, 10, 10, 10};
    private static final int[] SWING_MILLIS = {850, 550, 600, 800};
    private static final float[] SPEED = {0.85f, 1.05f, 1f, 1f};

    /** Half the SECUTOR's field of view, against everyone else's {@link Eyes#HALF_VIEW}. */
    public static final float SECUTOR_VIEW = 0.6f;
    public static final int NET_MILLIS = 1500;
    /** The net is cast at an opponent in sight between these distances, aimed this closely. */
    public static final int NET_MIN = 100;
    public static final int NET_MAX = 300;
    public static final float NET_AIM = 0.35f;
    /** What is left of a blow on a MURMILLO's scutum, and through a SECUTOR's helmet. */
    public static final float SHIELD = 0.75f;
    public static final float HELMET = 0.9f;
    /** A scutum covers this far either side of where its bearer faces. */
    public static final float SHIELD_ARC = 1.05f;
    /** The sica's bonus, for a blow coming from between these angles off where the victim faces: its side. */
    public static final float SIDE_MIN = 0.9f;
    public static final float SIDE_MAX = 2.2f;

    private Arms() {
    }

    public static int reach(int kind) {
        return REACH[index(kind)];
    }

    public static int damage(int kind) {
        return DAMAGE[index(kind)];
    }

    /** The time between two swings of this class's weapon. */
    public static int swingMillis(int kind) {
        return SWING_MILLIS[index(kind)];
    }

    /** How fast this class walks and runs, against the others. */
    public static float speed(int kind) {
        return SPEED[index(kind)];
    }

    public static float halfView(int kind) {
        return kind == GladiatorClass.SECUTOR ? SECUTOR_VIEW : Eyes.HALF_VIEW;
    }

    /** How long a net holds a gladiator of this class. */
    public static int netMillis(int kind) {
        return kind == GladiatorClass.SECUTOR ? NET_MILLIS / 2 : NET_MILLIS;
    }

    /**
     * The damage a blow of {@code base} does. It comes from {@code off} radians off where the victim faces: a THRAEX's
     * {@code blade} adds half again from the side, a MURMILLO's scutum takes a quarter off from in front, and a
     * SECUTOR's helmet a tenth. Every blow does at least one point.
     */
    public static int blow(int base, int attackerKind, boolean blade, int victimKind, float off) {
        float damage = base;
        float side = Math.abs(off);
        if (blade && attackerKind == GladiatorClass.THRAEX && side >= SIDE_MIN && side <= SIDE_MAX) {
            damage = damage * 1.5f;
        }
        if (victimKind == GladiatorClass.MURMILLO && side < SHIELD_ARC) {
            damage = damage * SHIELD;
        }
        if (victimKind == GladiatorClass.SECUTOR) {
            damage = damage * HELMET;
        }
        return Math.max(1, Math.round(damage));
    }

    private static int index(int kind) {
        return Math.clamp(kind, GladiatorClass.MURMILLO, GladiatorClass.SECUTOR);
    }
}
