package io.github.jabrena.juno.games.texasholdem;

final class HeartSprite {
    private HeartSprite() {
    }

    static int row(int row) {
        if (row == 0) return 0x0C6;
        if (row == 1) return 0x1EF;
        if (row == 2 || row == 3) return 0x1FF;
        if (row == 4) return 0x0FE;
        if (row == 5) return 0x07C;
        if (row == 6) return 0x038;
        return row == 7 ? 0x010 : 0;
    }
}
