package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Random;

/**
 * The game's engine as one object another program can drive a frame at a time, so a screen flow other than
 * {@link Doom}'s can play the same maps: read a WAD map and plan its route, start it, run one frame, and read the
 * intermission's tally. The engine's state stays where {@link Doom} keeps it, in {@link World} and its neighbours, so
 * there is one engine per program; the renderer's buffers are allocated here, once, after the WAD-sized tables.
 */
public final class DoomEngine {
    private static final BoardMemory MEMORY = MCU.getBoardMemory();
    /** The engine's clock, shared with {@link Loading} and Doom2's views so the program allocates one. */
    public static final Clock CLOCK = MCU.getClock();
    public static final int PLAYING = 0;
    public static final int WON = 1;
    public static final int DIED = 2;
    public static final int QUIT = 3;
    /** The last map of an episode played in order. */
    public static final int LAST_MAP = World.LAST_MAP;

    private final boolean available;
    private final short[] lines;
    private final byte[] clips;
    private final byte[] changes;
    private final short[] stack;
    private final short[] depths;
    private final short[] shots;
    private int frame;
    private int mapStarted;
    private int elapsedMillis;

    /**
     * Allocates the WAD-sized tables and the route planner's, when the arena has room for them and the renderer;
     * call once at startup, before other large allocations. The SD card must already be started.
     */
    public DoomEngine() {
        boolean room = MEMORY.arenaCapacityBytes() - MEMORY.arenaUsedBytes()
                >= World.WAD_ARENA_BYTES + World.PLAN_ARENA_BYTES + World.RENDER_ARENA_BYTES;
        if (room) {
            World.allocateForWad();
            RoutePlanner.reserve();
            World.fromWad = true;
        } else {
            Serial.println("DOOM: arena too small for the WAD (build with -Djuno.Xmx=92k)");
        }
        available = room;
        // After the tables, as Doom allocates them: the room check above already counted these buffers.
        lines = new short[2 * DisplayList.LIST_SIZE];
        clips = new byte[2 * DisplayList.WIDTH];
        changes = new byte[DisplayList.MAX_LINES];
        stack = new short[64];
        depths = new short[DisplayList.WIDTH];
        shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        Serial.print("DOOM: arena ");
        Serial.print(MEMORY.arenaUsedBytes());
        Serial.print("/");
        Serial.println(MEMORY.arenaCapacityBytes());
    }

    /** Whether the arena held the WAD-sized tables, so maps can be loaded. */
    public boolean available() {
        return available;
    }

    /**
     * Reads map EeMm from the card's {@code DOOM1.WAD} at {@code skill} (1 to 5, as DOOM's menu numbers them) and
     * plans the CPU's route to its exit; {@code false} when the map is missing, broken or too large. It takes seconds, as in {@link Doom}; a bar from {@link #showProgress}
     * shows how far it has got.
     */
    public boolean load(int episode, int map, int skill) {
        if (!available) {
            return false;
        }
        World.episode = episode;
        World.map = map;
        World.skill = skill;
        if (!World.loadMap()) {
            return false;
        }
        World.planRoute();
        return true;
    }

    /**
     * Draws the next {@link #load}'s progress bar at ({@code x}, {@code y}), {@code width} pixels long and 6 high, with
     * the estimated seconds left after it; the load holds the caller until it ends, updating the bar as it reads.
     */
    public void showProgress(int x, int y, int width) {
        Loading.show(x, y, width);
    }

    /**
     * Starts the loaded map: the marine at its start, every monster and item in place, and the view and status bar
     * drawn. A {@code newGame} starts with the fist and pistol; otherwise the marine keeps his arsenal from the last
     * map. Waits for the tap that started it to be released, so it is not read as a step or a shot.
     */
    public void start(boolean autopilot, boolean newGame) {
        Controls.autopilot = autopilot;
        if (newGame) {
            Weapon.reset();
            Random.seed(CLOCK.micros());
        }
        Lifts.reset();
        Player.spawn(World.ceilings);
        Monsters.reset(World.monsterStates, shots);
        Items.reset(World.taken);
        Weapon.enterMap();
        Autopilot.restart();
        FrameStats.reset();
        Controls.waitForRelease();
        Hud.drawBar();
        DisplayList.clearView();
        frame = 0;
        mapStarted = CLOCK.millis();
    }

    /**
     * Plays one frame: the touch controls, the CPU pilot, doors, lifts, pickups and monsters, then the view and, every
     * fourth frame, the status bar. A tap on the marine's face opens the pause menu, which holds the frame until the
     * player continues or quits. Returns {@link #PLAYING}, {@link #WON}, {@link #DIED} or {@link #QUIT}.
     */
    public int frame() {
        short[] ceilings = World.ceilings;
        short[] monsters = World.monsterStates;
        byte[] taken = World.taken;
        frame = frame + 1;
        int started = CLOCK.micros();
        Weapon.tick();
        if (Player.hurt > 0) {
            Player.hurt = Player.hurt - 1;
        }
        if (Controls.handle(ceilings, monsters)) {
            Hud.drawBar();
        }
        if (Controls.pauseTapped()) {
            return pause();
        }
        if (Controls.autopilot) {
            Autopilot.step(ceilings, monsters, taken);
        }
        Doors.operate(ceilings);
        Lifts.operate(Player.x, Player.y);
        Player.settle();
        Items.pickUp(taken);
        Monsters.think(monsters, shots, ceilings, frame);
        int building = CLOCK.micros();
        Renderer.build(lines, clips, depths, stack, ceilings, monsters, shots, taken);
        int presenting = CLOCK.micros();
        DisplayList.present(lines, changes);
        FrameStats.record(started, building, presenting, CLOCK.micros());
        if ((frame & 3) == 0) {
            Hud.drawStatus();
        }
        if (Campaign.mapWon(monsters)) {
            elapsedMillis = CLOCK.millis() - mapStarted;
            return WON;
        }
        if (Player.health == 0) {
            Hud.drawStatus();
            return DIED;
        }
        return PLAYING;
    }

    private int pause() {
        int pausedAt = CLOCK.millis();
        if (Interludes.paused()) {
            return QUIT;
        }
        // Back where it stopped: the view and status bar drawn afresh, the pause left out of the map's time.
        FrameStats.reset();
        Hud.drawBar();
        DisplayList.clearView();
        mapStarted = mapStarted + CLOCK.millis() - pausedAt;
        return PLAYING;
    }

    /** Whether the CPU walks the map; the player can switch it in game with the CPU/HUMAN label. */
    public boolean autopilot() {
        return Controls.autopilot;
    }

    /** How long the map just won took, without pauses. */
    public int elapsedMillis() {
        return elapsedMillis;
    }

    public int kills() {
        return Monsters.kills;
    }

    public int monsters() {
        return World.monsters;
    }

    public int itemsFound() {
        return Campaign.itemsFound(World.taken, World.placedItems);
    }

    public int itemsCounted() {
        return Campaign.itemsCounted(World.placedItems);
    }

    public int secretsFound() {
        return World.secretsFound;
    }

    public int secrets() {
        return World.secrets;
    }

    /** A DOOM intermission percentage, safe for maps with no entries and capped at 100%. */
    public static int percent(int found, int total) {
        return Campaign.percent(found, total);
    }
}
