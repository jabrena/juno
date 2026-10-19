package io.github.jabrena.juno.games.chess;


final class KingSprite {
    private KingSprite() {
    }

    static int kingShape(int row) {
        if (row == 0) {
            return 0x00600;
        }
        if (row == 1) {
            return 0x00F00;
        }
        if (row == 2) {
            return 0x00600;
        }
        if (row == 3) {
            return 0x1C638;
        }
        if (row == 4) {
            return 0x3EF7C;
        }
        if (row == 5) {
            return 0x7FFFE;
        }
        if (row == 6) {
            return 0x7FFFE;
        }
        if (row == 7) {
            return 0x7FFFE;
        }
        if (row == 8) {
            return 0x3FFFC;
        }
        if (row == 9) {
            return 0x3FFFC;
        }
        if (row == 10) {
            return 0x1FFF8;
        }
        if (row == 11) {
            return 0x1FFF8;
        }
        if (row == 12) {
            return 0x0FFF0;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        if (row == 14) {
            return 0x0FFF0;
        }
        if (row == 15) {
            return 0x1FFF8;
        }
        if (row == 16) {
            return 0x1FFF8;
        }
        if (row == 17) {
            return 0x3FFFC;
        }
        if (row == 18) {
            return 0x3FFFC;
        }
        return 0;
    }

    static int kingDetail(int row) {
        if (row == 5) {
            return 0x00400;
        }
        if (row == 6) {
            return 0x00400;
        }
        if (row == 7) {
            return 0x00400;
        }
        if (row == 8) {
            return 0x00400;
        }
        if (row == 9) {
            return 0x00400;
        }
        if (row == 10) {
            return 0x00400;
        }
        if (row == 13) {
            return 0x0FFF0;
        }
        return 0;
    }
}
