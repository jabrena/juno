package io.github.jabrena.juno.usecases.miner.blocks;

import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.miner.commons.BitBoard;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner.commons.TabBar;
import io.github.jabrena.juno.usecases.miner.services.NodeService;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * The blocks board, stacked like Tetris: one cell per bounty bit length, the shortest at the bottom left, filling rows
 * upward. A mined cell is a solid block in the color of the node that mined it (gray until some node says which); a
 * cell a node is mining is outlined in that node's color; a free cell is dark. Above the grid, the round, how many
 * cells are mined, and the length this node mines, beside a swatch of its own color. Only cells that changed are
 * redrawn.
 */
public final class BlocksView implements View {
    private static final Clock CLOCK = MCU.getClock();
    static final int COLUMNS = 8;
    static final int ROWS = (BitBoard.SIZE + COLUMNS - 1) / COLUMNS;
    static final int PITCH_X = 30;
    static final int PITCH_Y = 25;
    static final int GRID_Y = TabBar.TOP - 2 - ROWS * PITCH_Y;

    static final int FREE = 0;
    static final int CLAIMED = 1;
    static final int MINED = 2;

    /** Where gray sits in {@link #COLORS}: the color of a cell whose miner's color is not known. */
    static final int GRAY = BitBoard.COLORS;
    /**
     * The {@link BitBoard#COLORS} node colors, which the nodes keep unique between them; then gray. Hues sit far apart
     * on the color wheel, and since a node takes the lowest free color, the first nodes get the most distinct ones.
     */
    private static final int[] COLORS = {
        TftTouchShield.GREEN, TftTouchShield.MAGENTA, TftTouchShield.YELLOW, TftTouchShield.CYAN,
        TftTouchShield.RED, TftTouchShield.WHITE, 0x3B3F, TftTouchShield.ORANGE, TftTouchShield.GRAY
    };
    private static final int SWATCH = 14;

    private final Navigator navigator;
    private final Miner2State model;
    private final Miner2Header header;
    private final NodeService node;
    /** The {@link #cellKey} each cell was last drawn with, a byte apiece: the R4's arena is nearly full. */
    private final byte[] shown = new byte[BitBoard.SIZE];
    private int shownStatus;

    public BlocksView(Navigator navigator, Miner2State model, Miner2Header header, NodeService node) {
        this.navigator = navigator;
        this.model = model;
        this.header = header;
        this.node = node;
    }

    @Override
    public void enter() {
        header.enter();
        TabBar.draw(Miner2Views.BLOCKS);
        for (int i = 0; i < BitBoard.SIZE; i++) {
            shown[i] = -1;
        }
        shownStatus = -1;
    }

    @Override
    public void render() {
        header.render();
        BitBoard board = model.board();
        int status = statusKey(board.round(), board.count(), board.ownBit(), board.asking());
        if (status != shownStatus) {
            shownStatus = status;
            showStatus(board);
        }
        int now = CLOCK.millis();
        for (int bit = BitBoard.FIRST; bit <= BitBoard.LAST; bit++) {
            int key = cellKey(board, bit, now);
            if (key != shown[bit - BitBoard.FIRST]) {
                shown[bit - BitBoard.FIRST] = (byte) key;
                drawCell(bit, key / 16, COLORS[key % 16]);
            }
        }
    }

    @Override
    public void touch(int x, int y) {
        if (Miner2Header.isWifi(x, y)) {
            node.toggleWifi();
        } else {
            TabBar.follow(navigator, x, y);
        }
    }

    /** What {@code bit}'s cell shows: its state times 16 plus its color's index in {@link #COLORS}. */
    static int cellKey(BitBoard board, int bit, int now) {
        if (board.isMined(bit)) {
            return MINED * 16 + colorIndex(board.cellColor(bit));
        }
        int claimant = board.claimant(bit, now);
        if (claimant != BitBoard.UNKNOWN) {
            return CLAIMED * 16 + colorIndex(board.colorOf(claimant));
        }
        return FREE * 16;
    }

    /** The index in {@link #COLORS} of a node color: itself, or {@link #GRAY} for {@link BitBoard#NO_COLOR}. */
    static int colorIndex(int color) {
        return color >= 0 && color < BitBoard.COLORS ? color : GRAY;
    }

    /** The RGB565 color a node color is drawn in. */
    static int color(int color) {
        return COLORS[colorIndex(color)];
    }

    /** One number that changes whenever what {@link #showStatus} would draw changes. */
    static int statusKey(int round, int count, int ownBit, boolean asking) {
        return ((round * 64 + count) * 64 + ownBit) * 2 + (asking ? 1 : 0);
    }

    /** The left edge of {@code bit}'s cell: lengths run left to right along a row. */
    static int cellX(int bit) {
        return (bit - BitBoard.FIRST) % COLUMNS * PITCH_X;
    }

    /** The top edge of {@code bit}'s cell: rows stack upward from the bottom, as in Tetris. */
    static int cellY(int bit) {
        return GRID_Y + (ROWS - 1 - (bit - BitBoard.FIRST) / COLUMNS) * PITCH_Y;
    }

    private void showStatus(BitBoard board) {
        int own = board.ownBit();
        GameScreen.row(0, 2, own == 0 ? TftTouchShield.GRAY : board.asking() ? TftTouchShield.YELLOW
                : TftTouchShield.GREEN);
        TftTouchShield.print("R" + board.round());
        TftTouchShield.print(" " + board.count() + "/" + BitBoard.SIZE);
        if (own == 0) {
            TftTouchShield.print(" wait");
        } else {
            TftTouchShield.print(board.asking() ? " ask " : " bit ");
            TftTouchShield.print(own);
        }
        TftTouchShield.fillRect(240 - SWATCH - 4, 58, SWATCH, SWATCH, color(model.board().ownColor()));
    }

    private static void drawCell(int bit, int state, int color) {
        int x = cellX(bit) + 1;
        int y = cellY(bit) + 1;
        int width = PITCH_X - 2;
        int height = PITCH_Y - 2;
        int text;
        int background;
        if (state == MINED) {
            TftTouchShield.fillRect(x, y, width, height, color);
            // Tetris bevel: lit from the top left.
            TftTouchShield.drawHorizontalLine(x, y, width, TftTouchShield.WHITE);
            TftTouchShield.drawVerticalLine(x, y, height, TftTouchShield.WHITE);
            TftTouchShield.drawHorizontalLine(x, y + height - 1, width, TftTouchShield.BLACK);
            TftTouchShield.drawVerticalLine(x + width - 1, y, height, TftTouchShield.BLACK);
            text = TftTouchShield.BLACK;
            background = color;
        } else if (state == CLAIMED) {
            TftTouchShield.fillRect(x, y, width, height, GameScreen.BACKGROUND);
            TftTouchShield.drawRect(x, y, width, height, color);
            TftTouchShield.drawRect(x + 1, y + 1, width - 2, height - 2, color);
            text = color;
            background = GameScreen.BACKGROUND;
        } else {
            TftTouchShield.fillRect(x, y, width, height, GameScreen.PANEL);
            text = TftTouchShield.GRAY;
            background = GameScreen.PANEL;
        }
        int digits = bit < 10 ? 1 : 2;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(text, background);
        TftTouchShield.setCursor(x + (width - digits * TftTouchShield.CHAR_WIDTH + 1) / 2,
                y + (height - TftTouchShield.CHAR_HEIGHT + 1) / 2);
        TftTouchShield.print(bit);
    }
}
