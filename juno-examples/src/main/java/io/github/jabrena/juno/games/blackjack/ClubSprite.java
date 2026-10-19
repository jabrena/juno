package io.github.jabrena.juno.games.blackjack;


final class ClubSprite {
    private ClubSprite() {
    }

    static int clubRow(int row) {
        if (row == 0 || row == 2 || row == 7) {
            return 0x038;
        }
        if (row == 1) {
            return 0x07C;
        }
        if (row == 3 || row == 5) {
            return 0x0D6;
        }
        if (row == 4) {
            return 0x1FF;
        }
        if (row == 6) {
            return 0x010;
        }
        return 0;
    }
}
