package io.github.jabrena.juno.games.gladiator.gladiator;

import io.github.jabrena.juno.games.gladiator.arena.Arms;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Eyes;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.arena.Javelins;

/**
 * What a CPU gladiator's board tells its {@link Brain} besides the opponent: the javelins it holds or sees on the sand,
 * whether its own net is still in hand, whether it is caught in the opponent's, and where the lion is; and what it
 * decides to do with javelin and net each frame.
 */
public final class Senses {
    /** A javelin is thrown at an opponent in sight between these distances, once it is aimed this closely. */
    public static final int THROW_MIN = 180;
    public static final int THROW_MAX = 800;
    public static final float THROW_AIM = 0.08f;
    public static final int THROW_MILLIS = 1500;

    private final boolean[] lying = new boolean[Javelins.COUNT];
    private final float[] lyingX = new float[Javelins.COUNT];
    private final float[] lyingY = new float[Javelins.COUNT];
    private boolean holding;
    private boolean rooted;
    private boolean netReady;
    private boolean lionOut;
    private float lionX;
    private float lionY;
    private boolean throwJavelin;
    private boolean castNet;
    private int thrownAt;

    /** Forgets everything for a new round, with its net in hand when {@code netInHand}. */
    public void reset(boolean netInHand, int now) {
        holding = false;
        rooted = false;
        netReady = netInHand;
        lionOut = false;
        throwJavelin = false;
        castNet = false;
        thrownAt = now - THROW_MILLIS;
        for (int j = 0; j < Javelins.COUNT; j++) {
            lying[j] = false;
        }
    }

    /** What the board says of its own arms: a javelin in hand, caught in a net, its net still in hand. */
    public void arm(boolean holdingJavelin, boolean caught, boolean netInHand) {
        holding = holdingJavelin;
        rooted = caught;
        netReady = netInHand;
    }

    /** Javelin {@code j} lies on the sand at ({@code x}, {@code y}) when {@code onSand}. */
    public void javelin(int j, boolean onSand, float x, float y) {
        lying[j] = onSand;
        lyingX[j] = x;
        lyingY[j] = y;
    }

    /** The lion is out at ({@code x}, {@code y}) when {@code out}. */
    public void lion(boolean out, float x, float y) {
        lionOut = out;
        lionX = x;
        lionY = y;
    }

    public boolean holding() {
        return holding;
    }

    public boolean rooted() {
        return rooted;
    }

    public boolean netReady() {
        return netReady;
    }

    public boolean lionOut() {
        return lionOut;
    }

    public float lionX() {
        return lionX;
    }

    public float lionY() {
        return lionY;
    }

    /** Whether it threw its javelin this frame. */
    public boolean throwJavelin() {
        return throwJavelin;
    }

    /** Whether it cast its net this frame. */
    public boolean castNet() {
        return castNet;
    }

    /** Heads for the nearest javelin on the sand within {@code range}, when it has none; returns whether it does. */
    public boolean fetch(Brain brain, Body body, float range, float speed) {
        if (holding) {
            return false;
        }
        int nearest = -1;
        float best = range;
        for (int j = 0; j < Javelins.COUNT; j++) {
            float distance = body.distanceTo(lyingX[j], lyingY[j]);
            if (lying[j] && distance < best) {
                best = distance;
                nearest = j;
            }
        }
        if (nearest < 0) {
            return false;
        }
        brain.goTo(body, lyingX[nearest], lyingY[nearest], speed);
        return true;
    }

    /** Throws the javelin it holds at an opponent in sight, in range and straight ahead. */
    public void aimJavelin(Brain brain, Body body, int now) {
        throwJavelin = false;
        if (!holding || !brain.sees() || now - thrownAt < THROW_MILLIS) {
            return;
        }
        if (aimed(brain, body, THROW_MIN, THROW_MAX, THROW_AIM)) {
            throwJavelin = true;
            thrownAt = now;
        }
    }

    /** A RETIARIUS casts its net at an opponent in sight, near and in front. */
    public void aimNet(Brain brain, Body body) {
        castNet = false;
        if (!netReady || !brain.sees() || brain.kind() != GladiatorClass.RETIARIUS) {
            return;
        }
        if (aimed(brain, body, Arms.NET_MIN, Arms.NET_MAX, Arms.NET_AIM)) {
            castNet = true;
            netReady = false;
        }
    }

    /** Whether the opponent last seen is between {@code near} and {@code far}, within {@code aim} and in clear sight. */
    private static boolean aimed(Brain brain, Body body, int near, int far, float aim) {
        float distance = body.distanceTo(brain.seenX(), brain.seenY());
        float off = Math.abs(Eyes.wrap((float) Math.atan2(brain.seenY() - body.y(), brain.seenX() - body.x())
                - body.angle()));
        return distance >= near && distance <= far && off < aim
                && body.coliseum().clearSight(body.x(), body.y(), brain.seenX(), brain.seenY());
    }
}
