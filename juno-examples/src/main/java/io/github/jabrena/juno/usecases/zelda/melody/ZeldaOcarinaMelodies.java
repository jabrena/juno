package io.github.jabrena.juno.usecases.zelda.melody;

/** Allocation-free access to the thirteen Ocarina of Time melody arrangements. */
public final class ZeldaOcarinaMelodies {
    private ZeldaOcarinaMelodies() {
    }

    public static int noteCount(int song) {
        return switch (song) {
            case 1 -> ZeldaOcarinaZeldasLullaby.noteCount();
            case 2 -> ZeldaOcarinaEponasSong.noteCount();
            case 3 -> ZeldaOcarinaSariasSong.noteCount();
            case 4 -> ZeldaOcarinaSunsSong.noteCount();
            case 5 -> ZeldaOcarinaSongOfTime.noteCount();
            case 6 -> ZeldaOcarinaSongOfStorms.noteCount();
            case 7 -> ZeldaOcarinaMinuetOfForest.noteCount();
            case 8 -> ZeldaOcarinaBoleroOfFire.noteCount();
            case 9 -> ZeldaOcarinaSerenadeOfWater.noteCount();
            case 10 -> ZeldaOcarinaRequiemOfSpirit.noteCount();
            case 11 -> ZeldaOcarinaNocturneOfShadow.noteCount();
            case 12 -> ZeldaOcarinaPreludeOfLight.noteCount();
            default -> ZeldaOcarinaScarecrowsSong.noteCount();
        };
    }

    public static int ocarinaNoteCount(int song) {
        return switch (song) {
            case 1 -> ZeldaOcarinaZeldasLullaby.ocarinaNoteCount();
            case 2 -> ZeldaOcarinaEponasSong.ocarinaNoteCount();
            case 3 -> ZeldaOcarinaSariasSong.ocarinaNoteCount();
            case 4 -> ZeldaOcarinaSunsSong.ocarinaNoteCount();
            case 5 -> ZeldaOcarinaSongOfTime.ocarinaNoteCount();
            case 6 -> ZeldaOcarinaSongOfStorms.ocarinaNoteCount();
            case 7 -> ZeldaOcarinaMinuetOfForest.ocarinaNoteCount();
            case 8 -> ZeldaOcarinaBoleroOfFire.ocarinaNoteCount();
            case 9 -> ZeldaOcarinaSerenadeOfWater.ocarinaNoteCount();
            case 10 -> ZeldaOcarinaRequiemOfSpirit.ocarinaNoteCount();
            case 11 -> ZeldaOcarinaNocturneOfShadow.ocarinaNoteCount();
            case 12 -> ZeldaOcarinaPreludeOfLight.ocarinaNoteCount();
            default -> ZeldaOcarinaScarecrowsSong.ocarinaNoteCount();
        };
    }

    public static int frequencyHz(int song, int note) {
        return switch (song) {
            case 1 -> ZeldaOcarinaZeldasLullaby.frequencyHz(note);
            case 2 -> ZeldaOcarinaEponasSong.frequencyHz(note);
            case 3 -> ZeldaOcarinaSariasSong.frequencyHz(note);
            case 4 -> ZeldaOcarinaSunsSong.frequencyHz(note);
            case 5 -> ZeldaOcarinaSongOfTime.frequencyHz(note);
            case 6 -> ZeldaOcarinaSongOfStorms.frequencyHz(note);
            case 7 -> ZeldaOcarinaMinuetOfForest.frequencyHz(note);
            case 8 -> ZeldaOcarinaBoleroOfFire.frequencyHz(note);
            case 9 -> ZeldaOcarinaSerenadeOfWater.frequencyHz(note);
            case 10 -> ZeldaOcarinaRequiemOfSpirit.frequencyHz(note);
            case 11 -> ZeldaOcarinaNocturneOfShadow.frequencyHz(note);
            case 12 -> ZeldaOcarinaPreludeOfLight.frequencyHz(note);
            default -> ZeldaOcarinaScarecrowsSong.frequencyHz(note);
        };
    }

    public static int toneMillis(int song, int note) {
        return switch (song) {
            case 1 -> ZeldaOcarinaZeldasLullaby.toneMillis(note);
            case 2 -> ZeldaOcarinaEponasSong.toneMillis(note);
            case 3 -> ZeldaOcarinaSariasSong.toneMillis(note);
            case 4 -> ZeldaOcarinaSunsSong.toneMillis(note);
            case 5 -> ZeldaOcarinaSongOfTime.toneMillis(note);
            case 6 -> ZeldaOcarinaSongOfStorms.toneMillis(note);
            case 7 -> ZeldaOcarinaMinuetOfForest.toneMillis(note);
            case 8 -> ZeldaOcarinaBoleroOfFire.toneMillis(note);
            case 9 -> ZeldaOcarinaSerenadeOfWater.toneMillis(note);
            case 10 -> ZeldaOcarinaRequiemOfSpirit.toneMillis(note);
            case 11 -> ZeldaOcarinaNocturneOfShadow.toneMillis(note);
            case 12 -> ZeldaOcarinaPreludeOfLight.toneMillis(note);
            default -> ZeldaOcarinaScarecrowsSong.toneMillis(note);
        };
    }

    public static int stepMillis(int song, int note) {
        return switch (song) {
            case 1 -> ZeldaOcarinaZeldasLullaby.stepMillis(note);
            case 2 -> ZeldaOcarinaEponasSong.stepMillis(note);
            case 3 -> ZeldaOcarinaSariasSong.stepMillis(note);
            case 4 -> ZeldaOcarinaSunsSong.stepMillis(note);
            case 5 -> ZeldaOcarinaSongOfTime.stepMillis(note);
            case 6 -> ZeldaOcarinaSongOfStorms.stepMillis(note);
            case 7 -> ZeldaOcarinaMinuetOfForest.stepMillis(note);
            case 8 -> ZeldaOcarinaBoleroOfFire.stepMillis(note);
            case 9 -> ZeldaOcarinaSerenadeOfWater.stepMillis(note);
            case 10 -> ZeldaOcarinaRequiemOfSpirit.stepMillis(note);
            case 11 -> ZeldaOcarinaNocturneOfShadow.stepMillis(note);
            case 12 -> ZeldaOcarinaPreludeOfLight.stepMillis(note);
            default -> ZeldaOcarinaScarecrowsSong.stepMillis(note);
        };
    }
}
