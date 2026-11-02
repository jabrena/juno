package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.Eyes;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.arena.Side;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * The ARENA's screen: the coliseum seen from above, as in the aerial picture, with the sand, the red pillars, the
 * wall and the tiers of seats round it. Each gladiator is a dot with a line where it faces and the edges of what its
 * eyes can see; a dotted line joins it to the opponent it sees. Under the map, a panel per gate shows who fights
 * there, its health and wins and what it is doing.
 *
 * <p>The map is painted once. A gladiator that moves is erased by painting its old marks again, pixel by pixel, in
 * whatever the map has under each one, so nothing else needs to be redrawn.
 */
public final class ArenaView {
    public static final int WIDTH = 320;
    public static final int TITLE_HEIGHT = 20;
    public static final int MENU_LEFT = 268;
    private static final int CX = 160;
    private static final int CY = 97;
    /** How far out the wall's outer rim reaches, as a multiple of the oval's radii. */
    private static final float RIM_REACH = 1.17f;
    private static final int MAP_TOP = 22;
    private static final int MAP_BOTTOM = 172;
    private static final int STATUS_Y = 175;
    private static final int PANEL_Y = 188;
    private static final int PANEL_WIDTH = 154;
    private static final int PANEL_HEIGHT = 50;
    private static final int FACING = 12;
    private static final int SIGHT = 26;
    private static final int DOT = 4;

    public static final int BACKGROUND = 0x0841;
    public static final int PANEL = 0x18C3;
    public static final int SAND = 0xDDF0;
    public static final int WALL = 0x93CB;
    public static final int SEATS = 0x5AAA;
    public static final int SEATS_DARK = 0x4228;
    public static final int RIM = 0x7BEF;
    public static final int PILLAR = 0xE984;

    private final Coliseum coliseum;
    // What each gladiator's marks were drawn from, to erase them.
    private final boolean[] drawn = new boolean[2];
    private final short[] drawnX = new short[2];
    private final short[] drawnY = new short[2];
    private final short[] drawnAngle = new short[2];
    private final boolean[] drawnSees = new boolean[2];
    private final int[] panelKey = new int[2];
    /** Pixels per unit: fits the whole coliseum, out to its rim, in the map's width and height. */
    private float scale = 0.12f;
    private int statusKey = -1;

    public ArenaView(Coliseum coliseum) {
        this.coliseum = coliseum;
    }

    /** Paints the title, the whole map and the empty panels. */
    public void drawAll(TftTouchShield tft) {
        scale = Math.min(WIDTH / 2f / (coliseum.radiusX() * RIM_REACH),
                (MAP_BOTTOM - MAP_TOP) / 2f / (coliseum.radiusY() * RIM_REACH));
        tft.fillScreen(BACKGROUND);
        tft.fillRect(0, 0, WIDTH, TITLE_HEIGHT, PANEL);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.YELLOW, PANEL);
        tft.setCursor(6, 3);
        tft.print("GLADIATOR ARENA");
        tft.drawRect(MENU_LEFT, 2, WIDTH - MENU_LEFT - 2, TITLE_HEIGHT - 4, TftTouchShield.WHITE);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, PANEL);
        tft.setCursor(MENU_LEFT + 13, 7);
        tft.print("MENU");
        for (int y = MAP_TOP; y < MAP_BOTTOM; y++) {
            int runStart = 0;
            int runColor = floorAt(0, y);
            for (int x = 1; x <= WIDTH; x++) {
                int color = x < WIDTH ? floorAt(x, y) : -1;
                if (color != runColor) {
                    tft.fillRect(runStart, y, x - runStart, 1, runColor);
                    runStart = x;
                    runColor = color;
                }
            }
        }
        drawn[0] = false;
        drawn[1] = false;
        panelKey[0] = -1;
        panelKey[1] = -1;
        statusKey = -1;
    }

    /** Moves both gladiators' marks to where the match has them now and refreshes the panels. */
    public void update(TftTouchShield tft, Match match, int now) {
        // Erase both before forgetting either: a line of sight ends at the other gladiator's old dot.
        for (int slot = 0; slot < 2; slot++) {
            if (drawn[slot]) {
                marks(tft, slot, drawnX[slot], drawnY[slot], drawnAngle[slot], drawnSees[slot], 0, true);
            }
        }
        drawn[0] = false;
        drawn[1] = false;
        boolean fighting = match.phase() != Match.WAITING;
        for (int slot = 0; slot < 2; slot++) {
            Side side = match.side(slot);
            if (side.present()) {
                drawnX[slot] = (short) screenX(side.x());
                drawnY[slot] = (short) screenY(side.y());
                drawnAngle[slot] = (short) side.angle();
                drawnSees[slot] = fighting && match.sees(slot);
                drawn[slot] = true;
            }
        }
        for (int slot = 0; slot < 2; slot++) {
            if (drawn[slot]) {
                marks(tft, slot, drawnX[slot], drawnY[slot], drawnAngle[slot], drawnSees[slot],
                        Figure.color(match.side(slot).behaviour()), false);
                panel(tft, match, slot);
            } else {
                emptyPanel(tft, slot);
            }
        }
        status(tft, match, now);
    }

    public static boolean menuTapped(int x, int y) {
        return y < TITLE_HEIGHT && x >= MENU_LEFT;
    }

    /** The map's color under a pixel: stands, wall, sand or pillar. */
    public int floorAt(int px, int py) {
        float wx = (px - CX) / scale;
        float wy = (CY - py) / scale;
        float ex = wx / coliseum.radiusX();
        float ey = wy / coliseum.radiusY();
        float e = ex * ex + ey * ey;
        if (e > 1.36f) {
            return BACKGROUND;
        }
        if (e > 1.3f) {
            return RIM;
        }
        if (e > 1.06f) {
            return ((int) ((e - 1.06f) / 0.06f) & 1) == 0 ? SEATS : SEATS_DARK;
        }
        if (e > 1f) {
            return WALL;
        }
        if (coliseum.pillarAt(wx, wy, 0) >= 0) {
            return PILLAR;
        }
        return SAND;
    }

    private int screenX(int x) {
        return Math.round(CX + x * scale);
    }

    private int screenY(int y) {
        return Math.round(CY - y * scale);
    }

    /** A gladiator's dot, facing line and field of view, and its line of sight; or, erasing, the map under them. */
    private void marks(TftTouchShield tft, int slot, int x, int y, int wireAngle, boolean sees, int color, boolean erase) {
        float angle = GladiatorProtocol.fromWire(wireAngle);
        int other = 1 - slot;
        if (sees && drawn[other]) {
            line(tft, x, y, drawnX[other], drawnY[other], TftTouchShield.YELLOW, erase, 3);
        }
        for (int side = -1; side <= 1; side += 2) {
            float edge = angle + side * Eyes.HALF_VIEW;
            line(tft, x, y, x + Math.round((float) Math.cos(edge) * SIGHT), y - Math.round((float) Math.sin(edge) * SIGHT),
                    TftTouchShield.GRAY, erase, 2);
        }
        line(tft, x, y, x + Math.round((float) Math.cos(angle) * FACING), y - Math.round((float) Math.sin(angle) * FACING),
                TftTouchShield.WHITE, erase, 1);
        for (int dy = -DOT; dy <= DOT; dy++) {
            for (int dx = -DOT; dx <= DOT; dx++) {
                if (dx * dx + dy * dy <= DOT * DOT) {
                    plot(tft, x + dx, y + dy, color, erase);
                }
            }
        }
    }

    /** A line of every {@code step}-th pixel, so a dotted one shows the map through it. */
    private void line(TftTouchShield tft, int x0, int y0, int x1, int y1, int color, boolean erase, int step) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        int skip = 0;
        while (true) {
            if (skip == 0) {
                plot(tft, x, y, color, erase);
                skip = step;
            }
            skip = skip - 1;
            if (x == x1 && y == y1) {
                return;
            }
            int twice = 2 * error;
            if (twice >= dy) {
                error = error + dy;
                x = x + sx;
            }
            if (twice <= dx) {
                error = error + dx;
                y = y + sy;
            }
        }
    }

    private void plot(TftTouchShield tft, int x, int y, int color, boolean erase) {
        if (x < 0 || x >= WIDTH || y < MAP_TOP || y >= MAP_BOTTOM) {
            return;
        }
        tft.drawPixel(x, y, erase ? floorAt(x, y) : color);
    }

    private void panel(TftTouchShield tft, Match match, int slot) {
        Side side = match.side(slot);
        int key = side.behaviour() << 24 | side.health() << 16 | side.wins() << 8 | side.mode() << 1
                | (side.human() ? 1 : 0);
        if (key == panelKey[slot]) {
            return;
        }
        panelKey[slot] = key;
        int x = panelX(slot);
        int color = Figure.color(side.behaviour());
        tft.fillRect(x, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, PANEL);
        tft.fillRect(x, PANEL_Y, 4, PANEL_HEIGHT, color);
        tft.setTextSize(1);
        tft.setTextColor(color, PANEL);
        tft.setCursor(x + 10, PANEL_Y + 5);
        tft.print((slot == 0 ? "LEFT  " : "RIGHT ") + Figure.name(side.behaviour()));
        tft.drawRect(x + 10, PANEL_Y + 17, 100, 8, TftTouchShield.GRAY);
        int health = side.health();
        int bar = health > 50 ? TftTouchShield.GREEN : health > 20 ? TftTouchShield.YELLOW : TftTouchShield.RED;
        tft.fillRect(x + 11, PANEL_Y + 18, 98 * health / Match.HEALTH, 6, bar);
        tft.setTextColor(TftTouchShield.WHITE, PANEL);
        tft.setCursor(x + 116, PANEL_Y + 17);
        tft.print("W " + side.wins());
        tft.setTextColor(TftTouchShield.GRAY, PANEL);
        tft.setCursor(x + 10, PANEL_Y + 34);
        tft.print((side.human() ? "HUMAN " : "CPU ") + Hud.modeName(side.mode()));
    }

    private void emptyPanel(TftTouchShield tft, int slot) {
        if (panelKey[slot] == 0) {
            return;
        }
        panelKey[slot] = 0;
        int x = panelX(slot);
        tft.fillRect(x, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, PANEL);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, PANEL);
        tft.setCursor(x + 10, PANEL_Y + 5);
        tft.print(slot == 0 ? "LEFT GATE" : "RIGHT GATE");
        tft.setCursor(x + 10, PANEL_Y + 21);
        tft.print("waiting for a gladiator");
    }

    private int panelX(int slot) {
        return slot == 0 ? 4 : WIDTH - 4 - PANEL_WIDTH;
    }

    private void status(TftTouchShield tft, Match match, int now) {
        int gladiators = (match.side(0).present() ? 1 : 0) + (match.side(1).present() ? 1 : 0);
        int countdown = match.countdown(now);
        int key = match.phase() << 24 | (match.round() & 0xFF) << 16 | countdown << 8 | match.winner() << 2 | gladiators;
        if (key == statusKey) {
            return;
        }
        statusKey = key;
        String text;
        int color = TftTouchShield.WHITE;
        if (match.phase() == Match.WAITING) {
            text = "WAITING FOR GLADIATORS " + gladiators + "/2";
            color = TftTouchShield.YELLOW;
        } else if (match.phase() == Match.COUNTDOWN) {
            text = "ROUND " + match.round() + " STARTS IN " + countdown;
            color = TftTouchShield.YELLOW;
        } else if (match.phase() == Match.FIGHT) {
            text = "ROUND " + match.round() + ": FIGHT!";
        } else {
            int style = match.side(match.winner()).behaviour();
            text = Figure.name(style) + (match.winner() == 0 ? " (LEFT)" : " (RIGHT)") + " WINS";
            color = Figure.color(style);
        }
        tft.fillRect(0, STATUS_Y, WIDTH, 11, BACKGROUND);
        tft.setTextSize(1);
        tft.setTextColor(color, BACKGROUND);
        tft.setCursor((WIDTH - text.length() * TftTouchShield.CHAR_WIDTH) / 2, STATUS_Y + 2);
        tft.print(text);
    }
}
