package io.github.jabrena.juno.usecases.zelda.theme;

/** Allocation-free access to the Cardputer's monophonic Ocarina of Time soundtrack arrangements. */
public final class ZeldaSoundtrackThemes {
    public static final int ZELDA_TITLE = 0;
    public static final int LOST_WOODS = 1;
    public static final int KAKARIKO_VILLAGE = 2;
    public static final int GREAT_FAIRY_FOUNTAIN = 3;
    public static final int TEMPLE_OF_TIME = 4;
    public static final int GANONDORF_BATTLE = 5;
    public static final int COUNT = 6;

    private static final String[] LABELS = {
        "1. ZELDA TITLE THEME",
        "2. LOST WOODS",
        "3. KAKARIKO VILLAGE",
        "4. GREAT FAIRY FOUNTAIN",
        "5. TEMPLE OF TIME",
        "6. GANONDORF BATTLE"
    };

    private static final String[] TITLES = {
        "ZELDA TITLE THEME",
        "LOST WOODS",
        "KAKARIKO VILLAGE",
        "GREAT FAIRY FOUNTAIN",
        "TEMPLE OF TIME",
        "GANONDORF BATTLE"
    };

    private ZeldaSoundtrackThemes() {
    }

    public static String label(int theme) {
        return LABELS[theme];
    }

    public static String title(int theme) {
        return TITLES[theme];
    }

    public static int repeatCount(int theme) {
        return switch (theme) {
            case ZELDA_TITLE -> ZeldaOcarinaTheme.repeatCount();
            case LOST_WOODS -> ZeldaLostWoodsTheme.repeatCount();
            case KAKARIKO_VILLAGE -> ZeldaKakarikoVillageTheme.repeatCount();
            case GREAT_FAIRY_FOUNTAIN -> ZeldaGreatFairyFountainTheme.repeatCount();
            case TEMPLE_OF_TIME -> ZeldaTempleOfTimeTheme.repeatCount();
            case GANONDORF_BATTLE -> ZeldaGanondorfBattleTheme.repeatCount();
            default -> 0;
        };
    }

    public static int noteCount(int theme) {
        return switch (theme) {
            case ZELDA_TITLE -> ZeldaOcarinaTheme.noteCount();
            case LOST_WOODS -> ZeldaLostWoodsTheme.noteCount();
            case KAKARIKO_VILLAGE -> ZeldaKakarikoVillageTheme.noteCount();
            case GREAT_FAIRY_FOUNTAIN -> ZeldaGreatFairyFountainTheme.noteCount();
            case TEMPLE_OF_TIME -> ZeldaTempleOfTimeTheme.noteCount();
            case GANONDORF_BATTLE -> ZeldaGanondorfBattleTheme.noteCount();
            default -> 0;
        };
    }

    public static int frequencyHz(int theme, int note) {
        return switch (theme) {
            case ZELDA_TITLE -> ZeldaOcarinaTheme.frequencyHz(note);
            case LOST_WOODS -> ZeldaLostWoodsTheme.frequencyHz(note);
            case KAKARIKO_VILLAGE -> ZeldaKakarikoVillageTheme.frequencyHz(note);
            case GREAT_FAIRY_FOUNTAIN -> ZeldaGreatFairyFountainTheme.frequencyHz(note);
            case TEMPLE_OF_TIME -> ZeldaTempleOfTimeTheme.frequencyHz(note);
            case GANONDORF_BATTLE -> ZeldaGanondorfBattleTheme.frequencyHz(note);
            default -> 0;
        };
    }

    public static int toneMillis(int theme, int note) {
        return switch (theme) {
            case ZELDA_TITLE -> ZeldaOcarinaTheme.toneMillis(note);
            case LOST_WOODS -> ZeldaLostWoodsTheme.toneMillis(note);
            case KAKARIKO_VILLAGE -> ZeldaKakarikoVillageTheme.toneMillis(note);
            case GREAT_FAIRY_FOUNTAIN -> ZeldaGreatFairyFountainTheme.toneMillis(note);
            case TEMPLE_OF_TIME -> ZeldaTempleOfTimeTheme.toneMillis(note);
            case GANONDORF_BATTLE -> ZeldaGanondorfBattleTheme.toneMillis(note);
            default -> 0;
        };
    }

    public static int stepMillis(int theme, int note) {
        return switch (theme) {
            case ZELDA_TITLE -> ZeldaOcarinaTheme.stepMillis(note);
            case LOST_WOODS -> ZeldaLostWoodsTheme.stepMillis(note);
            case KAKARIKO_VILLAGE -> ZeldaKakarikoVillageTheme.stepMillis(note);
            case GREAT_FAIRY_FOUNTAIN -> ZeldaGreatFairyFountainTheme.stepMillis(note);
            case TEMPLE_OF_TIME -> ZeldaTempleOfTimeTheme.stepMillis(note);
            case GANONDORF_BATTLE -> ZeldaGanondorfBattleTheme.stepMillis(note);
            default -> 0;
        };
    }
}
