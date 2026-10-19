package io.github.jabrena.juno.games.blackjack;


final class DiamondSprite {
    private DiamondSprite() {
    }

    static int diamondRow(int row) {
        if (row == 0 || row == 8) {
            return 0x010;
        }
        if (row == 1 || row == 7) {
            return 0x038;
        }
        if (row == 2 || row == 6) {
            return 0x07C;
        }
        if (row == 3 || row == 5) {
            return 0x0FE;
        }
        return 0x1FF;
    }
}
