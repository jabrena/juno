package io.github.jabrena.juno.games.texasholdem;

final class SuitSprites {
    private SuitSprites() {
    }

    static int suitRow(int suit, int row) {
        if (suit == 0) return SpadeSprite.row(row);
        if (suit == 1) return HeartSprite.row(row);
        if (suit == 2) return DiamondSprite.row(row);
        return ClubSprite.row(row);
    }
}
