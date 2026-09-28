package io.github.jabrena.juno.games.spaceparanoids;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.get;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rules of Space Paranoids: the maze, ray casting, walls, shots, shields and pools, the pilot screen,
 * and the CPU driver that clears whole sectors through the game's own simulation and rendering.
 */
class SpaceParanoidsTest {
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
        set(Session.class, "score", 0);
        set(Session.class, "lives", 3);
        set(Session.class, "level", 1);
        set(Session.class, "nextBonus", 10000);
        set(Session.class, "shield", 100);
        set(DisplayList.class, "shown", 0);
        set(DisplayList.class, "built", 0);
        set(Combat.class, "fireCooldown", 0);
    }

    @Test
    void everyRoomOfEveryMazeIsReachableAndTheBorderIsSolid() {
        for (int seed = 0; seed < 100; seed++) {
            Random.seed(seed);
            call(Maze.class, "generate", maze, paths);
            call(Maze.class, "bfs", maze, paths, SIZE + 1);
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
        set(Camera.class, "posX", 1.5f);
        set(Camera.class, "posZ", 1.5f);
        call(Camera.class, "setAngle", 0f);
        call(Camera.class, "castRay", maze, 160);
        assertThat(Math.round((float) get(Camera.class, "rayDistance") * 100))
                .as("straight down the corridor").isEqualTo(1050);
        call(Camera.class, "castRay", maze, 319);
        assertThat(Math.round((float) get(Camera.class, "rayDistance") * 100))
                .as("the side wall, 45 degrees off").isEqualTo(50);
    }

    @Test
    void wallsStopTheTank() {
        corridor();
        set(Camera.class, "posX", 1.5f);
        set(Camera.class, "posZ", 1.5f);
        call(Camera.class, "setAngle", (float) Math.PI);
        for (int i = 0; i < 40; i++) {
            call(Camera.class, "drive", maze, 0.07f);
        }
        assertThat((float) get(Camera.class, "posX")).isGreaterThanOrEqualTo(1.26f);
        call(Camera.class, "setAngle", 0f);
        for (int i = 0; i < 400; i++) {
            call(Camera.class, "drive", maze, 0.07f);
        }
        assertThat((float) get(Camera.class, "posX")).isLessThanOrEqualTo(11.74f).isGreaterThan(11.5f);
    }

    @Test
    void aShotDownTheCorridorDestroysTheHunter() {
        corridor();
        set(Camera.class, "posX", 1.5f);
        set(Camera.class, "posZ", 1.5f);
        call(Camera.class, "setAngle", 0f);
        int hunter = place(HUNTER, 6, 1);
        set(Session.class, "huntersLeft", 1);
        is[hunter * I_STRIDE + 2] = 1000;
        call(Combat.class, "fire", fs, is);
        for (int frame = 0; frame < 30; frame++) {
            call(Entities.class, "move", maze, paths, fs, is);
        }
        assertThat(is[hunter * I_STRIDE]).as("destroyed").isNotEqualTo(HUNTER);
        assertThat(getInt(Session.class, "huntersLeft")).isZero();
        assertThat(getInt(Session.class, "score")).isEqualTo(1000);
    }

    @Test
    void wallsStopShots() {
        corridor();
        maze[SIZE + 4] = 1;
        set(Camera.class, "posX", 1.5f);
        set(Camera.class, "posZ", 1.5f);
        call(Camera.class, "setAngle", 0f);
        int turret = place(TURRET, 6, 1);
        call(Combat.class, "fire", fs, is);
        for (int frame = 0; frame < 30; frame++) {
            call(Entities.class, "move", maze, paths, fs, is);
        }
        assertThat(is[turret * I_STRIDE]).isEqualTo(TURRET);
        assertThat(getInt(Session.class, "score")).isZero();
    }

    @Test
    void enemyFireDrainsTheShieldAndPoolsRechargeIt() {
        corridor();
        set(Camera.class, "posX", 1.5f);
        set(Camera.class, "posZ", 1.5f);
        call(Camera.class, "setAngle", 0f);
        int turret = place(TURRET, 5, 1);
        is[turret * I_STRIDE + 2] = 1;
        call(Combat.class, "enemyFires", maze, fs, is, turret);
        is[turret * I_STRIDE + 2] = 1000;
        int shot = -1;
        for (int slot = 0; slot < 30; slot++) {
            if (is[slot * I_STRIDE] == ENEMY_SHOT) {
                shot = slot;
            }
        }
        assertThat(shot).as("the turret fired").isGreaterThanOrEqualTo(0);
        for (int frame = 0; frame < 60 && is[shot * I_STRIDE] == ENEMY_SHOT; frame++) {
            call(Entities.class, "move", maze, paths, fs, is);
        }
        assertThat(getInt(Session.class, "shield")).isEqualTo(80);
        place(POOL, 1, 1);
        call(Entities.class, "move", maze, paths, fs, is);
        assertThat(getInt(Session.class, "shield")).isEqualTo(100);
        assertThat(getInt(Session.class, "score")).isEqualTo(100);
    }

    /** The CPU driver hunts along the shortest path and shoots what it sees: it clears three sectors. */
    @Test
    void theCpuDriverClearsSectorAfterSector() {
        short[] route = new short[2 * SIZE * SIZE];
        set(Controls.class, "autopilot", true);
        for (int level = 1; level <= 3; level++) {
            set(Session.class, "level", level);
            call(Sector.class, "start", maze, paths, fs, is);
            boolean cleared = false;
            for (int frame = 1; frame < 30 * 120 && !cleared; frame++) {
                set(Session.class, "frame", frame);
                if (getInt(Combat.class, "fireCooldown") > 0) {
                    set(Combat.class, "fireCooldown", getInt(Combat.class, "fireCooldown") - 1);
                }
                call(AutopilotSP.class, "fly", maze, route, fs, is);
                call(Entities.class, "move", maze, paths, fs, is);
                call(SceneRenderer.class, "render", maze, lines, depth, faces, fs, is);
                if (getInt(Session.class, "shield") <= 0) {
                    // It is not a perfect driver: a lost tank costs a life, and the sector goes on.
                    call(Sector.class, "respawn", maze, is);
                }
                cleared = getInt(Session.class, "huntersLeft") == 0;
            }
            assertThat(cleared).as("sector %d cleared within two minutes", level).isTrue();
        }
        assertThat(getInt(Session.class, "score")).isGreaterThan(8000);
    }

    @Test
    void theCpuDriverHeadsForAnEnergyPoolWhenItsShieldIsLow() {
        corridor();
        set(Camera.class, "posX", 1.5f);
        set(Camera.class, "posZ", 1.5f);
        call(Camera.class, "setAngle", 0f);
        set(Session.class, "shield", 30);
        place(POOL, 5, 1);
        place(HUNTER, 11, 1);
        maze[SIZE + 9] = 1;
        short[] route = new short[2 * SIZE * SIZE];
        for (int frame = 0; frame < 200 && getInt(Session.class, "shield") == 30; frame++) {
            call(AutopilotSP.class, "fly", maze, route, fs, is);
            call(Entities.class, "move", maze, paths, fs, is);
        }
        assertThat(getInt(Session.class, "shield")).as("picked up the pool").isEqualTo(65);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat((int) call(Controls.class, "choiceAt", 80, 140)).isZero();
        assertThat((int) call(Controls.class, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat((int) call(Controls.class, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat((int) call(Controls.class, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
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
        int slot = (int) call(Entities.class, "freeSlot", (Object) is);
        is[slot * I_STRIDE] = type;
        is[slot * I_STRIDE + 2] = 1000;
        fs[slot * F_STRIDE] = x * ONE + ONE / 2;
        fs[slot * F_STRIDE + 1] = z * ONE + ONE / 2;
        fs[slot * F_STRIDE + 4] = fs[slot * F_STRIDE];
        fs[slot * F_STRIDE + 5] = fs[slot * F_STRIDE + 1];
        return slot;
    }
}
