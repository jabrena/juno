package io.github.jabrena.juno.games.gladiator.gladiator;

/**
 * Where an {@link Ambush} gladiator hides: the pillar or cloud of smoke it chose, the spot it stands on, where it
 * expects the threat from, since when it has been lurking, and how many hiding places it gave up on unseen.
 */
public final class Lair {
    /** No hiding place chosen. */
    public static final int NONE = -1;

    private int pillar = NONE;
    private float hideX;
    private float hideY;
    private float threatX;
    private float threatY;
    private int lurkSince;
    private int restless;

    /** No hiding place yet for a new round, with the threat expected from ({@code x}, {@code y}). */
    public void reset(float x, float y, int now) {
        pillar = NONE;
        threatX = x;
        threatY = y;
        lurkSince = now;
        restless = 0;
    }

    public int pillar() {
        return pillar;
    }

    public float hideX() {
        return hideX;
    }

    public float hideY() {
        return hideY;
    }

    public float threatX() {
        return threatX;
    }

    public float threatY() {
        return threatY;
    }

    public int lurkSince() {
        return lurkSince;
    }

    public int restless() {
        return restless;
    }

    /** Hides by {@code place}, a pillar or cloud of smoke, or {@link #NONE}. */
    public void choose(int place) {
        pillar = place;
    }

    /** The spot to stand on to hide. */
    public void spot(float x, float y) {
        hideX = x;
        hideY = y;
    }

    /** Where it expects the threat from. */
    public void threat(float x, float y) {
        threatX = x;
        threatY = y;
    }

    /** Starts lurking at {@code now}. */
    public void lurk(int now) {
        lurkSince = now;
    }

    /** Gives up on a hiding place nobody came to. */
    public void giveUp() {
        restless = restless + 1;
    }

    /** It saw the opponent: the waiting paid off. */
    public void calm() {
        restless = 0;
    }
}
