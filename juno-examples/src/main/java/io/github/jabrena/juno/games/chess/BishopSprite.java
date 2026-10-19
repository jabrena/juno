package io.github.jabrena.juno.games.chess;


final class BishopSprite {
    private BishopSprite() {
    }

    static int bishopShape(int row) {
        if (row == 1) {
            return 0x00600;
        }
        if (row == 2) {
            return 0x00F00;
        }
        if (row == 3) {
            return 0x00600;
        }
        if (row == 4) {
            return 0x00F00;
        }
        if (row == 5) {
            return 0x01F80;
        }
        if (row == 6) {
            return 0x03FC0;
        }
        if (row == 7) {
            return 0x03FC0;
        }
        if (row == 8) {
            return 0x03FC0;
        }
        if (row == 9) {
            return 0x01F80;
        }
        if (row == 10) {
            return 0x00F00;
        }
        if (row == 11) {
            return 0x01F80;
        }
        if (row == 12) {
            return 0x00F00;
        }
        if (row == 13) {
            return 0x00F00;
        }
        if (row == 14) {
            return 0x01F80;
        }
        if (row == 15) {
            return 0x07FE0;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x1FFF8;
        }
        return 0;
    }

    static int bishopDetail(int row) {
        if (row == 5) {
            return 0x01000;
        }
        if (row == 6) {
            return 0x00800;
        }
        if (row == 7) {
            return 0x00400;
        }
        if (row == 11) {
            return 0x01F80;
        }
        return 0;
    }
}
