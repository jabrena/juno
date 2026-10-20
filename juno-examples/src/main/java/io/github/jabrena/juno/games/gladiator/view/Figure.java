package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;

/**
 * Vector line art: the opponent as a billboard, a run of segments {@code x0, z0, x1, z1} in world units (x across
 * the figure toward the viewer's right, z up from the feet), and the gladius and shield in the viewer's own hands, in
 * screen pixels. Original line art.
 */
public final class Figure {
    public static final int BODY = 0;
    public static final int VISOR = 1;
    public static final int SHIELD = 2;
    public static final int SWORD = 3;
    public static final int THRUST = 4;

    public static final short[] SEGMENTS = {
        // Body: galea with its brim and crest, shoulders and torso, the skirt, legs and feet.
        -9, 152, 9, 152, 9, 152, 10, 170, -10, 170, -9, 152, -13, 170, 13, 170, -12, 172, 0, 186, 0, 186, 12, 172,
        -24, 146, 24, 146, 24, 146, 15, 100, 15, 100, -15, 100, -15, 100, -24, 146,
        -15, 100, -19, 78, 15, 100, 19, 78, -19, 78, 19, 78,
        -9, 78, -12, 0, 9, 78, 12, 0, -12, 0, -20, 0, 12, 0, 20, 0,
        // Visor, seen only from the front.
        -6, 162, 6, 162, 0, 162, 0, 155,
        // Scutum on the left arm, with its boss.
        22, 50, 44, 50, 44, 50, 44, 130, 44, 130, 22, 130, 22, 130, 22, 50, 28, 90, 38, 90,
        // Gladius held up in the right hand.
        -24, 144, -34, 110, -34, 110, -36, 168, -40, 112, -28, 108,
        // Gladius thrust forward.
        -24, 144, -30, 124, -30, 124, 4, 118, -30, 131, -30, 117,
    };
    public static final short[] START = {0, 68, 76, 96, 108, 120};

    /** The viewer's own gladius, at rest and mid-swing, and the shield's edge, in screen pixels. */
    public static final short[] HANDS = {
        // At rest: blade, tip, guard and grip.
        238, 170, 224, 108, 250, 172, 232, 106, 224, 108, 226, 96, 232, 106, 226, 96,
        226, 172, 262, 176, 240, 176, 246, 199, 250, 177, 254, 199,
        // Swinging across the view.
        250, 170, 176, 128, 258, 160, 180, 122, 176, 128, 170, 122, 180, 122, 170, 122,
        244, 180, 266, 154, 254, 172, 276, 199, 262, 166, 286, 199,
        // The shield's rim on the left.
        0, 138, 70, 126, 70, 126, 78, 199, 0, 146, 63, 135, 63, 135, 70, 199,
    };
    public static final short[] HANDS_START = {0, 28, 56, 72};

    public static final int AGGRESSIVE_COLOR = TftTouchShield.ORANGE;
    public static final int AMBUSH_COLOR = TftTouchShield.CYAN;
    public static final int BLADE = 0xB5B5;
    public static final int SHIELD_COLOR = 0xE8E8;

    private Figure() {
    }

    public static int color(int behaviour) {
        return behaviour == Brain.AMBUSH ? AMBUSH_COLOR : AGGRESSIVE_COLOR;
    }

    public static String name(int behaviour) {
        return behaviour == Brain.AMBUSH ? "AMBUSH" : "AGGRESSIVE";
    }
}
