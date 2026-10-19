package io.github.jabrena.juno.games.chess;


final class KnightSprite {
    private KnightSprite() {
    }

    static int knightShape(int row) {
        if (row == 1) {
            return 0x00D00;
        }
        if (row == 2) {
            return 0x01F80;
        }
        if (row == 3) {
            return 0x03FC0;
        }
        if (row == 4) {
            return 0x07FE0;
        }
        if (row == 5) {
            return 0x07FF0;
        }
        if (row == 6) {
            return 0x07FF8;
        }
        if (row == 7) {
            return 0x07FFC;
        }
        if (row == 8) {
            return 0x07FFC;
        }
        if (row == 9) {
            return 0x07F78;
        }
        if (row == 10) {
            return 0x0FF80;
        }
        if (row == 11) {
            return 0x0FFC0;
        }
        if (row == 12) {
            return 0x0FFE0;
        }
        if (row == 13) {
            return 0x0FFE0;
        }
        if (row == 14) {
            return 0x0FFE0;
        }
        if (row == 15) {
            return 0x0FFF0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    static int knightDetail(int row) {
        if (row == 4) {
            return 0x00100;
        }
        if (row == 8) {
            return 0x00004;
        }
        return 0;
    }
}
