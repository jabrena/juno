package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * DOOM in wireframe on the ELEGOO 2.8" TFT touch screen shield, after the original 1993 game by id Software. 
 * The player is a space marine
 * renderer: full BSP traversal, perspective projection, occlusion and working doors, plus the map's
 * monsters ({@link Monsters}) to fight with the pistol ({@link Weapon}).
 *
 * <p>At startup {@link World} finds the episodes in your own {@code DOOM1.WAD} on the shield's SD card without
 * loading a map. Once an episode and skill are chosen, {@link WadLevel} reads its first map; without a card or the
 * file the game plays the small original test map built into flash ({@link Level}). The WAD maps need a bigger
 * arena than the default: build with {@code -Djuno.Xmx=92k}.
 * Like the other 3D vector games it targets the UNO Q, whose Cortex-M33 has the speed its per-column
 * floating-point projection needs.
 *
 * <p>After the title, choose the pilot ({@link Controls}): HUMAN walks with the touch screen, CPU lets
 * {@link Autopilot} walk the map (a route {@link RoutePlanner} plans to the exit, or the built-in map's patrol), and
 * tapping the CPU/HUMAN label switches between them. With the WAD, the episode ({@link Episodes}) and skill
 * ({@link Skills}) menus follow; each map is read from the card when it starts, and the exit leads to the next one.
 * A DOOM-style intermission tallies kills, items, secrets and time, and a tap loads the next map; E?M8 ends with an
 * episode story before the menus. When the marine dies, the map restarts or the game quits to its title.
 * {@link Player} walks and opens doors, {@link Renderer} builds each frame and {@link DisplayList} draws
 * only what changed. {@link FrameStats} reports what each frame costs over the serial port.
 */
@Board(ArduinoUnoQ.class)
public final class Doom {
    private static final int FRAME_MILLIS = 40;

    private Doom() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Interludes.cover();
        Interludes.loading();
        // Discover and allocate the WAD tables before the renderer buffers; the selected map is loaded after menus.
        World.load();
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        byte[] clips = new byte[2 * DisplayList.WIDTH];
        byte[] changes = new byte[DisplayList.MAX_LINES];
        short[] stack = new short[64];
        short[] depths = new short[DisplayList.WIDTH];
        short[] shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        short[] ceilings = World.ceilings;
        short[] monsters = World.monsterStates;
        byte[] taken = World.taken;
        Interludes.waitForTap();
        Controls.choosePilot();
        chooseGame();
        Random.seed(Clock.micros());
        enterLevel(ceilings, monsters, shots, taken, true);

        int next = Clock.millis();
        int mapStarted = next;
        int frame = 0;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                next = Clock.millis();
            }
            frame = frame + 1;
            int started = Clock.micros();
            Weapon.tick();
            if (Player.hurt > 0) {
                Player.hurt = Player.hurt - 1;
            }
            if (Controls.handle(ceilings, monsters)) {
                Hud.drawBar();
            }
            if (Controls.autopilot) {
                Autopilot.step(ceilings, monsters, taken);
            }
            Player.operateDoors(ceilings);
            Lifts.operate(Player.x, Player.y);
            Player.settle();
            Items.pickUp(taken);
            Monsters.think(monsters, shots, ceilings, frame);
            int building = Clock.micros();
            Renderer.build(lines, clips, depths, stack, ceilings, monsters, shots, taken);
            int presenting = Clock.micros();
            DisplayList.present(lines, changes);
            FrameStats.record(started, building, presenting, Clock.micros());
            if ((frame & 3) == 0) {
                Hud.drawStatus();
            }
            if (Campaign.mapWon(monsters)) {
                // The next map is read and its route planned while the tally shows; a tap then starts it.
                Interludes.complete(Clock.millis() - mapStarted, taken);
                boolean ready = !World.fromWad || !Campaign.episodeFinished() && nextMap(true);
                if (ready) {
                    World.planRoute();
                }
                Interludes.waitToPlay();
                if (!ready) {
                    // The episode story returns to the title, then the pilot, episode and skill menus.
                    Interludes.episodeComplete();
                    Interludes.title();
                    Controls.choosePilot();
                    chooseGame();
                }
                enterLevel(ceilings, monsters, shots, taken, !ready);
                next = Clock.millis();
                mapStarted = next;
            } else if (Player.health == 0) {
                // As in DOOM the map can restart where the marine died, or the player quits to the title.
                if (!Interludes.died()) {
                    Interludes.title();
                    Controls.choosePilot();
                    chooseGame();
                }
                enterLevel(ceilings, monsters, shots, taken, true);
                next = Clock.millis();
                mapStarted = next;
            }
        }
    }

    /**
     * The episode and skill menus, for the WAD's maps, then the episode's first map that loads; the built-in map
     * has no episodes. If none of the episode's maps loads, the menus come back.
     */
    private static void chooseGame() {
        if (!World.fromWad) {
            return;
        }
        boolean loaded = false;
        while (!loaded) {
            Episodes.choose();
            Skills.choose();
            World.map = 0;
            loaded = nextMap(false);
        }
    }

    /**
     * Loads the episode's next map, skipping any that does not load (too large or missing), saying so on the tally
     * ({@code overTally}) or on a loading screen; {@code false} once the episode's last map is done. The built-in
     * map simply starts over.
     */
    private static boolean nextMap(boolean overTally) {
        if (!World.fromWad) {
            return true;
        }
        while (World.map < World.LAST_MAP) {
            World.map = World.map + 1;
            if (overTally) {
                Interludes.tallyLoading();
            } else {
                Interludes.loadingMap();
            }
            if (World.loadMap()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts the map afresh: the marine at the start, every monster back at its post, every item in place, and the
     * CPU's route planned again from the start, unless it was already planned while the tally showed.
     */
    private static void enterLevel(short[] ceilings, short[] monsters, short[] shots, byte[] taken, boolean plan) {
        Lifts.reset();
        Player.spawn(ceilings);
        if (plan) {
            World.planRoute();
        }
        Monsters.reset(monsters, shots);
        Items.reset(taken);
        Weapon.reset();
        Autopilot.restart();
        FrameStats.reset();
        Hud.drawBar();
        DisplayList.clearView();
    }
}
