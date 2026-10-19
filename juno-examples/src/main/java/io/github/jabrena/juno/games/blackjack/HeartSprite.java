package io.github.jabrena.juno.games.blackjack;


final class HeartSprite {
    private HeartSprite() {
    }

    static int heartRow(int row) {
        if (row == 0) {
            return 0x0C6;
        }
        if (row == 1) {
            return 0x1EF;
        }
        if (row == 2 || row == 3) {
            return 0x1FF;
        }
        if (row == 4) {
            return 0x0FE;
        }
        if (row == 5) {
            return 0x07C;
        }
        if (row == 6) {
            return 0x038;
        }
        if (row == 7) {
            return 0x010;
        }
        return 0;
    }
}
