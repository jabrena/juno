package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;

/**
 * The gladiator's screen apart from the view: full-screen messages while it looks for the arena, and the status bar
 * under the view with both gladiators' health and wins, what is happening, the CPU/HUMAN switch and MENU. Every part
 * of the bar is redrawn only when what it shows changed, which each bar remembers.
 */
public final class Hud {
    public static final int PANEL = 0x18C3;
    public static final int TOP = ViewList.VIEW_BOTTOM;
    public static final int BUTTON_Y = TOP + 19;
    public static final int BUTTON_HEIGHT = 18;
    public static final int PILOT_LEFT = 112;
    public static final int PILOT_WIDTH = 46;
    public static final int MENU_LEFT = 162;
    public static final int MENU_WIDTH = 46;
    private static final int BAR_WIDTH = 100;

    private final ViewList list;
    private int ownKey = -1;
    private int opponentKey = -1;
    private int statusKey = -1;
    private int pilotKey = -1;
    private int fpsKey = -1;

    public Hud(ViewList list) {
        this.list = list;
    }

    /** A message across the whole screen, with a smaller line under it; the next {@link #bar} starts afresh. */
    public static void message(String text, String detail, int color) {
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        centered(text, 92, 2, color, ViewList.BACKGROUND);
        centered(detail, 122, 1, TftTouchShield.GRAY, ViewList.BACKGROUND);
        button((ViewList.WIDTH - MENU_WIDTH) / 2, MENU_WIDTH, "MENU", TftTouchShield.WHITE);
    }

    /** The empty status bar, its buttons and the cleared view above it. */
    public void bar() {
        list.clearView();
        TftTouchShield.fillRect(0, TOP, ViewList.WIDTH, ViewList.HEIGHT - TOP, PANEL);
        TftTouchShield.drawHorizontalLine(0, TOP, ViewList.WIDTH, View.WALL);
        button(MENU_LEFT, MENU_WIDTH, "MENU", TftTouchShield.WHITE);
        ownKey = -1;
        opponentKey = -1;
        statusKey = -1;
        pilotKey = -1;
        fpsKey = -1;
    }

    /** This gladiator's side of the bar. */
    public void own(int behaviour, int health, int wins) {
        int key = behaviour << 16 | health << 8 | wins;
        if (key != ownKey) {
            ownKey = key;
            side(4, Figure.name(behaviour), Figure.color(behaviour), health, wins);
        }
    }

    public void ownProfile(int identity, String name, int color, int health, int wins) {
        int key = (identity + 16) << 16 | health << 8 | wins;
        if (key != ownKey) {
            ownKey = key;
            side(4, name, color, health, wins);
        }
    }

    /** The opponent's side of the bar, or that there is none yet. */
    public void opponent(boolean present, int behaviour, int health, int wins) {
        int key = present ? 1 << 24 | behaviour << 16 | health << 8 | wins : 0;
        if (key == opponentKey) {
            return;
        }
        opponentKey = key;
        if (present) {
            side(ViewList.WIDTH - 4 - BAR_WIDTH, Figure.name(behaviour), Figure.color(behaviour), health, wins);
        } else {
            TftTouchShield.fillRect(ViewList.WIDTH - 4 - BAR_WIDTH, TOP + 2, BAR_WIDTH, ViewList.HEIGHT - TOP - 2,
                    PANEL);
            text(ViewList.WIDTH - 4 - BAR_WIDTH, TOP + 5, "NO OPPONENT", TftTouchShield.GRAY, PANEL);
        }
    }

    public void opponentProfile(int identity, String name, int color, int health, int wins) {
        int key = 1 << 28 | identity << 16 | health << 8 | wins;
        if (key != opponentKey) {
            opponentKey = key;
            side(ViewList.WIDTH - 4 - BAR_WIDTH, name, color, health, wins);
        }
    }

    /** The line in the middle of the bar; {@code key} changes whenever {@code text} would. */
    public void status(int key, String text, int color) {
        if (key == statusKey) {
            return;
        }
        statusKey = key;
        TftTouchShield.fillRect(PILOT_LEFT - 4, TOP + 2, MENU_LEFT + MENU_WIDTH - PILOT_LEFT + 8, 14, PANEL);
        int x = (PILOT_LEFT + MENU_LEFT + MENU_WIDTH - text.length() * TftTouchShield.CHAR_WIDTH) / 2;
        text(x, TOP + 5, text, color, PANEL);
    }

    /**
     * The middle line during a round, from what the {@code report} says, for the gladiator in {@code slot} doing
     * {@code mode}; {@code hurt} after a blow.
     */
    public void fighterStatus(Report report, int slot, boolean human, int mode, boolean hurt) {
        if (report.phase() == Match.COUNTDOWN) {
            status(100 + report.countdown(), "FIGHT IN " + report.countdown(), TftTouchShield.YELLOW);
        } else if (report.phase() == Match.OVER) {
            boolean won = report.winner() == slot;
            status(won ? 200 : 201, won ? "VICTORY!" : "DEFEATED", won ? TftTouchShield.GREEN : TftTouchShield.RED);
        } else if (report.rooted()) {
            status(600, "NETTED!", TftTouchShield.CYAN);
        } else if (hurt && report.surprised()) {
            status(300, "SURPRISED!", TftTouchShield.RED);
        } else if (human) {
            status(400 + (report.visible() ? 1 : 0), report.visible() ? "IN SIGHT" : "FIGHT!", TftTouchShield.WHITE);
        } else {
            status(500 + mode, modeName(mode), report.visible() ? TftTouchShield.WHITE : TftTouchShield.GRAY);
        }
    }

    public static String modeName(int mode) {
        if (mode == Brain.SEARCHING) {
            return "SEARCHING";
        }
        if (mode == Brain.HUNTING) {
            return "HUNTING";
        }
        if (mode == Brain.HIDING) {
            return "HIDING";
        }
        if (mode == Brain.LURKING) {
            return "LURKING";
        }
        if (mode == Brain.CHARGING) {
            return "CHARGING";
        }
        if (mode == Brain.FLEEING) {
            return "FLEEING LION";
        }
        return "WAITING";
    }

    /** The frames drawn in the last second, left of MENU where a network gladiator has its CPU/HUMAN switch. */
    public void fps(int fps) {
        if (fps == fpsKey) {
            return;
        }
        fpsKey = fps;
        TftTouchShield.fillRect(PILOT_LEFT, BUTTON_Y, PILOT_WIDTH, BUTTON_HEIGHT, PANEL);
        text(PILOT_LEFT + 2, BUTTON_Y + 5, fps + " FPS", fps >= 20 ? TftTouchShield.GREEN : TftTouchShield.ORANGE,
                PANEL);
    }

    public void pilot(boolean human) {
        int key = human ? 1 : 0;
        if (key != pilotKey) {
            pilotKey = key;
            button(PILOT_LEFT, PILOT_WIDTH, human ? "HUMAN" : "CPU", human ? TftTouchShield.YELLOW : TftTouchShield.GREEN);
        }
    }

    public static boolean onPilot(int x, int y) {
        return y >= BUTTON_Y && x >= PILOT_LEFT && x < PILOT_LEFT + PILOT_WIDTH;
    }

    public static boolean onMenu(int x, int y) {
        return y >= BUTTON_Y && x >= MENU_LEFT && x < MENU_LEFT + MENU_WIDTH;
    }

    private static void side(int x, String name, int color, int health, int wins) {
        TftTouchShield.fillRect(x, TOP + 2, BAR_WIDTH, ViewList.HEIGHT - TOP - 2, PANEL);
        text(x, TOP + 5, name, color, PANEL);
        TftTouchShield.drawRect(x, TOP + 17, BAR_WIDTH, 8, TftTouchShield.GRAY);
        int filled = (BAR_WIDTH - 2) * health / Match.HEALTH;
        int bar = health > 50 ? TftTouchShield.GREEN : health > 20 ? TftTouchShield.YELLOW : TftTouchShield.RED;
        TftTouchShield.fillRect(x + 1, TOP + 18, filled, 6, bar);
        text(x, TOP + 29, "HP " + health + "  WINS " + wins, TftTouchShield.WHITE, PANEL);
    }

    private static void button(int x, int width, String label, int color) {
        TftTouchShield.fillRect(x, BUTTON_Y, width, BUTTON_HEIGHT, ViewList.BACKGROUND);
        TftTouchShield.drawRect(x, BUTTON_Y, width, BUTTON_HEIGHT, color);
        text(x + (width - label.length() * TftTouchShield.CHAR_WIDTH) / 2, BUTTON_Y + 5, label, color,
                ViewList.BACKGROUND);
    }

    private static void text(int x, int y, String text, int color, int background) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, background);
        TftTouchShield.setCursor(x, y);
        TftTouchShield.print(text);
    }

    public static void centered(String text, int y, int size, int color, int background) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, background);
        TftTouchShield.setCursor((ViewList.WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2, y);
        TftTouchShield.print(text);
    }
}
