package io.github.jabrena.juno.usecases.zelda;

import io.github.jabrena.juno.usecases.zelda.melody.ZeldaOcarinaMelodies;
import io.github.jabrena.juno.usecases.zelda.theme.ZeldaOcarinaTheme;

/** The Ocarina of Time title theme, twelve songs, and the optional Scarecrow's Song. */
final class ZeldaOcarinaSongs {
    static final int COUNT = 14;
    static final int FRAME_WIDTH = 4;

    static final String[] TITLES = {
        "Ocarina of Time Theme",
        "Zelda's Lullaby", "Epona's Song", "Saria's Song", "Sun's Song", "Song of Time", "Song of Storms",
        "Minuet of Forest", "Bolero of Fire", "Serenade of Water", "Requiem of Spirit", "Nocturne of Shadow",
        "Prelude of Light", "Scarecrow's Song (Custom)"
    };

    private ZeldaOcarinaSongs() {
    }

    static int nextIndex(int current) {
        return (current + 1) % COUNT;
    }

    static String title(int song) {
        return TITLES[song];
    }

    static void split(int song, int frame, char[] output) {
        String text = TITLES[song];
        int option = song + 1;
        int prefixLength = option < 10 ? 2 : 3;
        for (int position = 0; position < FRAME_WIDTH; position++) {
            int characterIndex = frame + position;
            char character;
            if (characterIndex < prefixLength) {
                character = prefixCharacter(option, characterIndex);
            } else {
                character = text.charAt(characterIndex - prefixLength);
            }
            output[position] = character >= 'a' && character <= 'z' ? (char) (character - 'a' + 'A') : character;
        }
    }

    static int frameCount(int song) {
        int prefixLength = song < 9 ? 2 : 3;
        return prefixLength + TITLES[song].length() - FRAME_WIDTH + 1;
    }

    private static char prefixCharacter(int option, int position) {
        if (option < 10) {
            return position == 0 ? (char) ('0' + option) : ' ';
        }
        if (position == 0) {
            return (char) ('0' + option / 10);
        }
        return position == 1 ? (char) ('0' + option % 10) : ' ';
    }

    static int noteCount(int song) {
        return song == 0 ? ZeldaOcarinaTheme.noteCount() : ZeldaOcarinaMelodies.noteCount(song);
    }

    static int repeatCount(int song) {
        return song == 0 ? ZeldaOcarinaTheme.repeatCount() : 1;
    }

    static int ocarinaNoteCount(int song) {
        return song == 0 ? 0 : ZeldaOcarinaMelodies.ocarinaNoteCount(song);
    }

    static int frequencyHz(int song, int note) {
        return song == 0 ? ZeldaOcarinaTheme.frequencyHz(note) : ZeldaOcarinaMelodies.frequencyHz(song, note);
    }

    static int toneMillis(int song, int note) {
        return song == 0 ? ZeldaOcarinaTheme.toneMillis(note) : ZeldaOcarinaMelodies.toneMillis(song, note);
    }

    static int stepMillis(int song, int note) {
        return song == 0 ? ZeldaOcarinaTheme.stepMillis(note) : ZeldaOcarinaMelodies.stepMillis(song, note);
    }
}
