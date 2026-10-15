package io.github.jabrena.juno.usecases.miner2.blocks;

import io.github.jabrena.juno.usecases.miner2.commons.BitBoard;
import io.github.jabrena.juno.usecases.miner2.commons.TabBar;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BlocksViewTest {
    @Test
    void theShortestLengthSitsAtTheBottomLeftAndRowsStackUpward() {
        assertThat(BlocksView.cellX(BitBoard.FIRST)).isZero();
        assertThat(BlocksView.cellY(BitBoard.FIRST) + BlocksView.PITCH_Y).isLessThanOrEqualTo(TabBar.TOP);
        assertThat(BlocksView.cellY(BitBoard.FIRST + BlocksView.COLUMNS)).isLessThan(BlocksView.cellY(BitBoard.FIRST));
        assertThat(BlocksView.cellY(BitBoard.LAST)).isEqualTo(BlocksView.GRID_Y);
    }

    @Test
    void everyCellFitsBetweenTheStatusRowAndTheTabsWithoutOverlapping() {
        Set<Integer> corners = new HashSet<>();
        for (int bit = BitBoard.FIRST; bit <= BitBoard.LAST; bit++) {
            assertThat(BlocksView.cellX(bit) + BlocksView.PITCH_X).isLessThanOrEqualTo(240);
            assertThat(BlocksView.cellY(bit)).isGreaterThanOrEqualTo(54 + 22);
            assertThat(corners.add(BlocksView.cellX(bit) * 1000 + BlocksView.cellY(bit))).isTrue();
        }
    }

    @Test
    void aCellShowsMinedOverClaimedOverFree() {
        BitBoard board = new BitBoard();
        int now = 1000;

        assertThat(BlocksView.cellKey(board, 5, now) / 16).isEqualTo(BlocksView.FREE);
        board.heard(0xAB, 3, now);
        board.heard(0xCD, 6, now);
        board.claim(0xAB, 5, now);
        assertThat(BlocksView.cellKey(board, 5, now)).isEqualTo(BlocksView.CLAIMED * 16 + 3);
        board.markMined(5, 0xCD);
        assertThat(BlocksView.cellKey(board, 5, now)).isEqualTo(BlocksView.MINED * 16 + 6);
        board.markMined(6, 0xEF);
        assertThat(BlocksView.cellKey(board, 6, now)).as("miner's color unknown").isEqualTo(BlocksView.MINED * 16
                + BlocksView.GRAY);
        assertThat(BlocksView.cellKey(board, 5, now)).as("fits the byte the view keeps").isLessThan(128);
    }

    @Test
    void everyNodeColorIsDrawnDifferentlyAndUnknownIsGray() {
        Set<Integer> colors = new HashSet<>();
        for (int color = 0; color < BitBoard.COLORS; color++) {
            colors.add(BlocksView.color(color));
        }
        assertThat(colors).hasSize(BitBoard.COLORS).doesNotContain(0x8410);
        assertThat(BlocksView.color(BitBoard.NO_COLOR)).isEqualTo(0x8410);
    }

    @Test
    void theStatusKeyChangesWithEverythingTheRowShows() {
        int key = BlocksView.statusKey(1, 12, 7, false);

        assertThat(BlocksView.statusKey(2, 12, 7, false)).isNotEqualTo(key);
        assertThat(BlocksView.statusKey(1, 13, 7, false)).isNotEqualTo(key);
        assertThat(BlocksView.statusKey(1, 12, 8, false)).isNotEqualTo(key);
        assertThat(BlocksView.statusKey(1, 12, 7, true)).isNotEqualTo(key);
    }
}
