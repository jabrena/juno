package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

/**
 * Monophonic RCX arrangement of the Ocarina of Time opening title used by the three-channel orchestra.
 *
 * <p>The timing follows Rachel Bell's piano arrangement: rolled opening chords, the right-hand melody, phrase
 * breaths, the closing left-hand arpeggios, and the written repeat. Simultaneous piano notes are ordered so the RCX's
 * single-voice speaker can play them without allocating objects while the program runs.
 */
public final class ZeldaOcarinaTheme {
    private static final int REPEAT_COUNT = 2;
    private static final int DURATION_MILLIS = 77_186;

    private static final int[] FREQUENCIES_HZ = {
        87, 175, 220, 294, 330, 65, 196, 247, 294, 330, 87, 131,
        175, 220, 294, 330, 65, 98, 165, 247, 294, 587, 659, 1175,
        1109, 1047, 988, 880, 784, 587, 659, 1175, 1109, 1047, 988, 1047,
        988, 587, 659, 1175, 1109, 1047, 988, 880, 784, 587, 659, 1175,
        1109, 1047, 988, 1319, 988, 880, 659, 587, 784, 880, 659, 988,
        784, 880, 659, 587, 784, 1047, 784, 698, 784, 87, 131, 175,
        220, 294, 330, 65, 98, 165, 247, 294, 330, 87, 131, 175,
        220, 294, 220
    };

    private static final int[] TONE_MILLIS = {
        274, 77, 60, 86, 2791, 283, 77, 85, 86, 3356, 462, 394,
        283, 291, 248, 1378, 462, 394, 282, 292, 248, 821, 831, 1421,
        103, 94, 2714, 317, 796, 436, 394, 1481, 103, 94, 1379, 231,
        2192, 394, 393, 1515, 95, 94, 2679, 292, 787, 428, 411, 1481,
        94, 94, 1422, 222, 3128, 813, 2483, 805, 2474, 831, 2431, 848,
        2474, 814, 2457, 831, 2449, 821, 2475, 796, 2363, 462, 394, 282,
        291, 249, 1379, 463, 393, 283, 291, 248, 1378, 462, 394, 283,
        291, 248, 1361
    };

    private static final int[] STEP_MILLIS = {
        274, 77, 60, 86, 3758, 283, 77, 85, 86, 3356, 462, 394,
        283, 291, 257, 1601, 462, 394, 282, 292, 274, 821, 831, 1421,
        103, 94, 2714, 317, 1096, 436, 394, 1481, 103, 94, 1379, 231,
        2466, 394, 402, 1515, 95, 94, 2679, 292, 1070, 428, 411, 1481,
        94, 94, 1422, 222, 3505, 813, 2483, 805, 2474, 831, 2431, 848,
        2474, 814, 2465, 831, 2449, 821, 2475, 796, 2449, 462, 394, 282,
        291, 257, 1601, 463, 393, 283, 291, 257, 1601, 462, 394, 283,
        291, 248, 1361
    };

    private ZeldaOcarinaTheme() {
    }

    public static int repeatCount() {
        return REPEAT_COUNT;
    }

    public static int noteCount() {
        return FREQUENCIES_HZ.length;
    }

    static int durationMillis() {
        return DURATION_MILLIS;
    }

    public static int frequencyHz(int note) {
        return FREQUENCIES_HZ[note];
    }

    public static int toneMillis(int note) {
        return TONE_MILLIS[note];
    }

    public static int stepMillis(int note) {
        return STEP_MILLIS[note];
    }
}
