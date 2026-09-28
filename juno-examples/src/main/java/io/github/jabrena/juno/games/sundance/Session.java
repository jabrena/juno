package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.api.Random;

/** Owns Sundance's score, round progression, packed sun records, and simulation rules. */
final class Session {
    static final int SUNS = 6;
    static final int S_STATE = 0;
    static final int S_FROM = 1;
    static final int S_TO = 2;
    static final int S_T = 3;
    static final int S_DUR = 4;
    static final int S_UP = 5;
    static final int S_COLOR = 6;
    static final int S_TIMER = 7;
    static final int S_STRIDE = 8;

    static final int FREE = 0;
    static final int FLYING = 1;
    static final int TRAPPED = 2;
    static final int BURST = 3;

    private static final int ROUND_FRAMES = 1500;
    private static final int RELEASE_FRAMES = 24;
    static final int TRAP_FRAMES = 8;
    static final int BURST_FRAMES = 16;
    private static final int START_LIVES = 3;
    private static final int MAX_LIVES = 5;
    private static final float COLLISION = 0.45f;
    private static final float GRID_GAP = 2.0f;

    static int score;
    static int best;
    static int round;
    static int lives;
    static int frame;
    static int timeLeft;
    static int quota;
    static int released;
    static int trapped;
    private static int releaseTimer;
    private static int nextColor;

    private Session() {
    }

    static void newGame() {
        score = 0;
        round = 1;
        lives = START_LIVES;
    }

    static void finishGame() {
        if (score > best) {
            best = score;
        }
    }

    static void advanceRound() {
        if (round % 3 == 0) {
            lives = Math.min(lives + 1, MAX_LIVES);
        }
        round = round + 1;
    }

    static void startRound(int[] suns, int[] hatches) {
        clear(suns, SUNS * S_STRIDE);
        clear(hatches, Grid.CELLS);
        quota = Math.min(3 + round, 12);
        released = 0;
        trapped = 0;
        releaseTimer = 10;
        timeLeft = ROUND_FRAMES;
        Controls.startRound();
    }

    /** Applies a single frame of rules to every hatch and sun. */
    static void step(int[] suns, int[] hatches) {
        countDown(hatches);
        releaseWhenReady(suns);
        moveSuns(suns, hatches);
        collide(suns);
    }

    private static void countDown(int[] hatches) {
        if (timeLeft > 0) {
            timeLeft = timeLeft - 1;
        }
        for (int cell = 0; cell < Grid.CELLS; cell++) {
            if (hatches[cell] > 0) {
                hatches[cell] = hatches[cell] - 1;
            }
        }
    }

    private static void releaseWhenReady(int[] suns) {
        if (releaseTimer > 0) {
            releaseTimer = releaseTimer - 1;
            return;
        }
        if (released < quota && count(suns, FLYING) < maxActive() && release(suns)) {
            releaseTimer = RELEASE_FRAMES;
        }
    }

    private static void moveSuns(int[] suns, int[] hatches) {
        for (int slot = 0; slot < SUNS; slot++) {
            int base = slot * S_STRIDE;
            int state = suns[base + S_STATE];
            if (state == FLYING) {
                suns[base + S_T] = suns[base + S_T] + 1;
                if (suns[base + S_T] >= suns[base + S_DUR]) {
                    land(suns, base, hatches);
                }
            } else if (state == TRAPPED || state == BURST) {
                suns[base + S_TIMER] = suns[base + S_TIMER] - 1;
                if (suns[base + S_TIMER] <= 0) {
                    suns[base + S_STATE] = FREE;
                }
            }
        }
    }

    /** Suns of this round still to come or in the air. */
    static int remaining(int[] suns) {
        return quota - released + count(suns, FLYING);
    }

    static boolean roundCleared(int[] suns) {
        return released == quota && count(suns, FLYING) + count(suns, TRAPPED) + count(suns, BURST) == 0;
    }

    static int finishRound() {
        int bonus = timeLeft / 25 * 10 * round;
        score = score + bonus;
        return bonus;
    }

    private static int maxActive() {
        return Math.min(2 + (round - 1) / 2, SUNS - 1);
    }

    private static int crossing() {
        return Math.max(22, 46 - 2 * round);
    }

    private static boolean release(int[] suns) {
        int slot = find(suns, FREE);
        if (slot < 0) {
            return false;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            int from = Random.nextInt(Grid.CELLS);
            if (clearOfSuns(suns, from)) {
                initializeFlyingSun(suns, slot, from);
                return true;
            }
        }
        return false;
    }

    private static void initializeFlyingSun(int[] suns, int slot, int from) {
        int base = slot * S_STRIDE;
        suns[base + S_STATE] = FLYING;
        suns[base + S_FROM] = from;
        suns[base + S_TO] = pickTarget(from);
        suns[base + S_T] = 0;
        suns[base + S_DUR] = crossing();
        suns[base + S_UP] = 0;
        suns[base + S_COLOR] = nextColor;
        nextColor = (nextColor + 1) % 4;
        released = released + 1;
    }

    private static boolean clearOfSuns(int[] suns, int cell) {
        for (int slot = 0; slot < SUNS; slot++) {
            int base = slot * S_STRIDE;
            if (suns[base + S_STATE] == FLYING && Grid.sunH(suns, base) > 0.5f) {
                float across = Grid.sunU(suns, base) - (cell % 3 + 0.5f);
                float depth = Grid.sunD(suns, base) - (cell / 3 + 0.5f);
                if (across * across + depth * depth < 2.5f) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int pickTarget(int cell) {
        int column = Math.clamp(cell % 3 + Random.nextInt(-1, 2), 0, 2);
        int row = Math.clamp(cell / 3 + Random.nextInt(-1, 2), 0, 2);
        return row * 3 + column;
    }

    private static void land(int[] suns, int base, int[] hatches) {
        int cell = suns[base + S_TO];
        if (hatches[cell] > 0) {
            suns[base + S_STATE] = TRAPPED;
            suns[base + S_TIMER] = TRAP_FRAMES;
            suns[base + S_FROM] = cell;
            trapped = trapped + 1;
            score = score + 100 * round;
            return;
        }
        suns[base + S_FROM] = cell;
        suns[base + S_TO] = pickTarget(cell);
        suns[base + S_T] = 0;
        suns[base + S_UP] = 1 - suns[base + S_UP];
    }

    private static void collide(int[] suns) {
        for (int first = 0; first < SUNS; first++) {
            int firstBase = first * S_STRIDE;
            if (suns[firstBase + S_STATE] == FLYING) {
                collideWithLaterSuns(suns, first, firstBase);
            }
        }
    }

    private static void collideWithLaterSuns(int[] suns, int first, int firstBase) {
        for (int second = first + 1; second < SUNS; second++) {
            int secondBase = second * S_STRIDE;
            if (suns[secondBase + S_STATE] == FLYING && touching(suns, firstBase, secondBase)) {
                burst(suns, firstBase);
                burst(suns, secondBase);
                lives = Math.max(0, lives - 1);
            }
        }
    }

    private static boolean touching(int[] suns, int first, int second) {
        float across = Grid.sunU(suns, first) - Grid.sunU(suns, second);
        float depth = Grid.sunD(suns, first) - Grid.sunD(suns, second);
        float height = (Grid.sunH(suns, first) - Grid.sunH(suns, second)) * GRID_GAP;
        return across * across + depth * depth + height * height < COLLISION * COLLISION;
    }

    private static void burst(int[] suns, int base) {
        int x = Grid.sunScreenX(suns, base);
        int y = Grid.sunScreenY(suns, base);
        suns[base + S_STATE] = BURST;
        suns[base + S_TIMER] = BURST_FRAMES;
        suns[base + S_FROM] = x;
        suns[base + S_TO] = y;
    }

    static void placeSun(int[] suns, int slot, int from, int to, int up, int progress, int color) {
        int base = slot * S_STRIDE;
        suns[base + S_STATE] = FLYING;
        suns[base + S_FROM] = from;
        suns[base + S_TO] = to;
        suns[base + S_T] = progress;
        suns[base + S_DUR] = 40;
        suns[base + S_UP] = up;
        suns[base + S_COLOR] = color;
    }

    static int find(int[] suns, int state) {
        for (int slot = 0; slot < SUNS; slot++) {
            if (suns[slot * S_STRIDE + S_STATE] == state) {
                return slot;
            }
        }
        return -1;
    }

    static int count(int[] suns, int state) {
        int total = 0;
        for (int slot = 0; slot < SUNS; slot++) {
            if (suns[slot * S_STRIDE + S_STATE] == state) {
                total = total + 1;
            }
        }
        return total;
    }

    static void clear(int[] records, int length) {
        for (int index = 0; index < length; index++) {
            records[index] = 0;
        }
    }
}
