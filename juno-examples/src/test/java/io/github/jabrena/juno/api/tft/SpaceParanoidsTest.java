package io.github.jabrena.juno.api.tft;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.get;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rules of Space Paranoids: the maze, ray casting, walls, shots, shields and pools, the pilot screen,
 * and the CPU driver that clears whole sectors through the game's own simulation and rendering.
 */
class SpaceParanoidsTest {
    private static final Class<?> GAME = SpaceParanoids.class;
    private static final int SIZE = 13;
    private static final int ONE = 1024;
    private static final int F_STRIDE = 6;
    private static final int I_STRIDE = 4;
    private static final int HUNTER = 1;
    private static final int TURRET = 3;
    private static final int POOL = 4;
    private static final int ENEMY_SHOT = 6;

    private byte[] maze;
    private short[] paths;
    private short[] lines;
    private int[] depth;
    private int[] faces;
    private int[] fs;
    private int[] is;

    @BeforeEach
    void newGame() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(3);
        maze = new byte[SIZE * SIZE];
        paths = new short[2 * SIZE * SIZE];
        lines = new short[2 * 150 * 5];
        depth = new int[81];
        faces = new int[81];
        fs = new int[30 * F_STRIDE];
        is = new int[30 * I_STRIDE];
        set(GAME, "score", 0);
        set(GAME, "lives", 3);
        set(GAME, "level", 1);
        set(GAME, "nextBonus", 10000);
        set(GAME, "shield", 100);
        set(GAME, "shown", 0);
        set(GAME, "built", 0);
        set(GAME, "fireCooldown", 0);
    }

    @Test
    void everyRoomOfEveryMazeIsReachableAndTheBorderIsSolid() {
        for (int seed = 0; seed < 100; seed++) {
            Random.seed(seed);
            call(GAME, "generateMaze", maze, paths);
            call(GAME, "bfs", maze, paths, SIZE + 1);
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    int cell = z * SIZE + x;
                    if (x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1) {
                        assertThat((int) maze[cell]).as("border %d,%d seed %d", x, z, seed).isEqualTo(1);
                    }
                    if ((x & 1) == 1 && (z & 1) == 1) {
                        assertThat((int) paths[cell]).as("room %d,%d seed %d", x, z, seed).isGreaterThanOrEqualTo(0);
                    }
                }
            }
        }
    }

    @Test
    void raysMeasureThePerpendicularDistanceToTheWall() {
        corridor();
        set(GAME, "posX", 1.5f);
        set(GAME, "posZ", 1.5f);
        call(GAME, "setAngle", 0f);
        call(GAME, "castRay", maze, 160);
        assertThat(Math.round((float) get(GAME, "rayDistance") * 100)).as("straight down the corridor").isEqualTo(1050);
        call(GAME, "castRay", maze, 319);
        assertThat(Math.round((float) get(GAME, "rayDistance") * 100)).as("the side wall, 45 degrees off").isEqualTo(50);
    }

    @Test
    void wallsStopTheTank() {
        corridor();
        set(GAME, "posX", 1.5f);
        set(GAME, "posZ", 1.5f);
        call(GAME, "setAngle", (float) Math.PI);
        for (int i = 0; i < 40; i++) {
            call(GAME, "drive", maze, 0.07f);
        }
        assertThat((float) get(GAME, "posX")).isGreaterThanOrEqualTo(1.26f);
        call(GAME, "setAngle", 0f);
        for (int i = 0; i < 400; i++) {
            call(GAME, "drive", maze, 0.07f);
        }
        assertThat((float) get(GAME, "posX")).isLessThanOrEqualTo(11.74f).isGreaterThan(11.5f);
    }

    @Test
    void aShotDownTheCorridorDestroysTheHunter() {
        corridor();
        set(GAME, "posX", 1.5f);
        set(GAME, "posZ", 1.5f);
        call(GAME, "setAngle", 0f);
        int hunter = place(HUNTER, 6, 1);
        set(GAME, "huntersLeft", 1);
        is[hunter * I_STRIDE + 2] = 1000;
        call(GAME, "fire", fs, is);
        for (int frame = 0; frame < 30; frame++) {
            call(GAME, "step", maze, paths, fs, is);
        }
        assertThat(is[hunter * I_STRIDE]).as("destroyed").isNotEqualTo(HUNTER);
        assertThat(getInt(GAME, "huntersLeft")).isZero();
        assertThat(getInt(GAME, "score")).isEqualTo(1000);
    }

    @Test
    void wallsStopShots() {
        corridor();
        maze[SIZE + 4] = 1;
        set(GAME, "posX", 1.5f);
        set(GAME, "posZ", 1.5f);
        call(GAME, "setAngle", 0f);
        int turret = place(TURRET, 6, 1);
        call(GAME, "fire", fs, is);
        for (int frame = 0; frame < 30; frame++) {
            call(GAME, "step", maze, paths, fs, is);
        }
        assertThat(is[turret * I_STRIDE]).isEqualTo(TURRET);
        assertThat(getInt(GAME, "score")).isZero();
    }

    @Test
    void enemyFireDrainsTheShieldAndPoolsRechargeIt() {
        corridor();
        set(GAME, "posX", 1.5f);
        set(GAME, "posZ", 1.5f);
        call(GAME, "setAngle", 0f);
        int turret = place(TURRET, 5, 1);
        is[turret * I_STRIDE + 2] = 1;
        call(GAME, "enemyFires", maze, fs, is, turret);
        is[turret * I_STRIDE + 2] = 1000;
        int shot = -1;
        for (int slot = 0; slot < 30; slot++) {
            if (is[slot * I_STRIDE] == ENEMY_SHOT) {
                shot = slot;
            }
        }
        assertThat(shot).as("the turret fired").isGreaterThanOrEqualTo(0);
        for (int frame = 0; frame < 60 && is[shot * I_STRIDE] == ENEMY_SHOT; frame++) {
            call(GAME, "step", maze, paths, fs, is);
        }
        assertThat(getInt(GAME, "shield")).isEqualTo(80);
        place(POOL, 1, 1);
        call(GAME, "step", maze, paths, fs, is);
        assertThat(getInt(GAME, "shield")).isEqualTo(100);
        assertThat(getInt(GAME, "score")).isEqualTo(100);
    }

    /** The CPU driver hunts along the shortest path and shoots what it sees: it clears three sectors. */
    @Test
    void theCpuDriverClearsSectorAfterSector() {
        short[] route = new short[2 * SIZE * SIZE];
        set(GAME, "autopilot", true);
        for (int level = 1; level <= 3; level++) {
            set(GAME, "level", level);
            call(GAME, "startLevel", maze, paths, fs, is);
            boolean cleared = false;
            for (int frame = 1; frame < 30 * 120 && !cleared; frame++) {
                set(GAME, "frame", frame);
                if (getInt(GAME, "fireCooldown") > 0) {
                    set(GAME, "fireCooldown", getInt(GAME, "fireCooldown") - 1);
                }
                call(GAME, "flyAutopilot", maze, route, fs, is);
                call(GAME, "step", maze, paths, fs, is);
                call(GAME, "render", maze, lines, depth, faces, fs, is);
                if (getInt(GAME, "shield") <= 0) {
                    // It is not a perfect driver: a lost tank costs a life, and the sector goes on.
                    call(GAME, "respawn", maze, fs, is);
                }
                cleared = getInt(GAME, "huntersLeft") == 0;
            }
            assertThat(cleared).as("sector %d cleared within two minutes", level).isTrue();
        }
        assertThat(getInt(GAME, "score")).isGreaterThan(8000);
    }

    @Test
    void theCpuDriverHeadsForAnEnergyPoolWhenItsShieldIsLow() {
        corridor();
        set(GAME, "posX", 1.5f);
        set(GAME, "posZ", 1.5f);
        call(GAME, "setAngle", 0f);
        set(GAME, "shield", 30);
        place(POOL, 5, 1);
        place(HUNTER, 11, 1);
        maze[SIZE + 9] = 1;
        short[] route = new short[2 * SIZE * SIZE];
        for (int frame = 0; frame < 200 && getInt(GAME, "shield") == 30; frame++) {
            call(GAME, "flyAutopilot", maze, route, fs, is);
            call(GAME, "step", maze, paths, fs, is);
        }
        assertThat(getInt(GAME, "shield")).as("picked up the pool").isEqualTo(65);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat((int) call(GAME, "choiceAt", 80, 140)).isZero();
        assertThat((int) call(GAME, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat((int) call(GAME, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat((int) call(GAME, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
    }

    /** A maze that is solid but for one straight corridor along row 1, from x = 1 to 11. */
    private void corridor() {
        for (int i = 0; i < maze.length; i++) {
            maze[i] = 1;
        }
        for (int x = 1; x < SIZE - 1; x++) {
            maze[SIZE + x] = 0;
        }
    }

    private int place(int type, int x, int z) {
        int slot = (int) call(GAME, "freeSlot", (Object) is);
        is[slot * I_STRIDE] = type;
        is[slot * I_STRIDE + 2] = 1000;
        fs[slot * F_STRIDE] = x * ONE + ONE / 2;
        fs[slot * F_STRIDE + 1] = z * ONE + ONE / 2;
        fs[slot * F_STRIDE + 4] = fs[slot * F_STRIDE];
        fs[slot * F_STRIDE + 5] = fs[slot * F_STRIDE + 1];
        return slot;
    }
}
