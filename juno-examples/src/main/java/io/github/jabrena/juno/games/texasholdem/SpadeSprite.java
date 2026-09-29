package io.github.jabrena.juno.games.texasholdem;

final class SpadeSprite {
    private SpadeSprite() {
    }

    static int row(int row) {
        if (row == 0 || row == 7) return 0x010;
        if (row == 1 || row == 8) return 0x038;
        if (row == 2) return 0x07C;
        if (row == 3) return 0x0FE;
        if (row == 4 || row == 5) return 0x1FF;
        return 0x0D6;
    }
}
