package io.github.jabrena.juno.usecases.miner2.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BitBoardTest {
    @Test
    void coversEveryBountyLengthTheChainAllows() {
        assertThat(BitBoard.FIRST).isEqualTo(2);
        assertThat(BitBoard.LAST).isEqualTo(62);
        assertThat(BitBoard.SIZE).isEqualTo(61);
        assertThat(BitBoard.onBoard(1)).isFalse();
        assertThat(BitBoard.onBoard(63)).isFalse();
    }

    @Test
    void paintsEachMinedLengthInItsMinersColor() {
        BitBoard board = new BitBoard();
        board.heard(0xAB, 3, 0);
        board.heard(0xCD, 5, 0);

        board.markMined(5, 0xAB);
        board.markMined(5, 0xCD);

        assertThat(board.isMined(5)).isTrue();
        assertThat(board.cellColor(5)).as("the first block keeps the cell").isEqualTo(3);
        assertThat(board.isMined(6)).isFalse();
        assertThat(board.count()).isEqualTo(1);
    }

    @Test
    void aCellOfAnUnheardMinerIsColoredOnceAPeerSaysWhich() {
        BitBoard board = new BitBoard();

        assertThat(board.merge(1, 1L << 7 | 1L << 9 | 1L << 63 | 1L, 0)).isTrue();
        board.markMined(7, 0xAB);

        assertThat(board.count()).as("lengths off the board are ignored").isEqualTo(2);
        assertThat(board.cellColor(7)).isEqualTo(BitBoard.NO_COLOR);
        board.learnCell(7, 6);
        board.learnCell(7, 2);
        board.learnCell(8, 2);
        assertThat(board.cellColor(7)).as("the first color told stays").isEqualTo(6);
        assertThat(board.cellColor(8)).as("only mined cells are colored").isEqualTo(BitBoard.NO_COLOR);
    }

    @Test
    void theFreeColorIsTheLowestNoOtherLiveNodeSays() {
        BitBoard board = new BitBoard();
        board.heard(1, 0, 0);
        board.heard(2, 1, 0);
        board.heard(3, 2, 0);

        assertThat(board.freeColor(3, 10)).as("a node's own color does not count").isEqualTo(2);
        assertThat(board.freeColor(9, 10)).isEqualTo(3);
        assertThat(board.freeColor(9, BitBoard.COLOR_MILLIS)).as("silent nodes free their colors").isZero();
        assertThat(BitBoard.defaultColor(0x12345678)).isBetween(0, BitBoard.COLORS - 1);
    }

    @Test
    void aLaterRoundReplacesTheBoardAndAnEarlierOneIsIgnored() {
        BitBoard board = new BitBoard();
        board.markMined(4, 1);

        assertThat(board.merge(0, 1L << 5, 0)).isFalse();
        assertThat(board.isMined(5)).as("round 0 is a node without a board").isFalse();

        board.merge(3, 1L << 6, 42);
        assertThat(board.round()).isEqualTo(3);
        assertThat(board.roundStart()).isEqualTo(42);
        assertThat(board.isMined(4)).isFalse();
        assertThat(board.isMined(6)).isTrue();

        board.merge(2, 1L << 8, 50);
        assertThat(board.isMined(8)).isFalse();
    }

    @Test
    void isFullOnceEveryLengthIsMined() {
        BitBoard board = new BitBoard();
        for (int bit = BitBoard.FIRST; bit < BitBoard.LAST; bit++) {
            board.markMined(bit, 1);
        }
        assertThat(board.full()).isFalse();

        board.markMined(BitBoard.LAST, 1);

        assertThat(board.full()).isTrue();
        board.startRound(2, 70);
        assertThat(board.count()).isZero();
    }

    @Test
    void aClaimHoldsOneLengthPerNodeUntilItIsNotHeardAgain() {
        BitBoard board = new BitBoard();

        board.claim(0xAB, 5, 1000);
        board.claim(0xAB, 6, 2000);

        assertThat(board.claimant(5, 2000)).as("a node claims one length at a time").isEqualTo(BitBoard.UNKNOWN);
        assertThat(board.claimant(6, 2000)).isEqualTo(0xAB);
        assertThat(board.claimant(6, 2000 + BitBoard.CLAIM_MILLIS)).isEqualTo(BitBoard.UNKNOWN);

        board.claim(0xAB, 0, 3000);
        assertThat(board.claimant(6, 3000)).as("bit 0 withdraws the claim").isEqualTo(BitBoard.UNKNOWN);
    }
}
