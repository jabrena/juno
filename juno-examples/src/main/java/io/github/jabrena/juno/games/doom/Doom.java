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
 * <p>At startup {@link World} reads E1M1 from your own {@code DOOM1.WAD} on the shield's SD card
 * ({@link WadLevel}); without a card or the file it plays the small original test map built into flash
 * ({@link Level}). The WAD's map needs a bigger arena than the default: build with {@code -Djuno.Xmx=26k}.
 * Like the other 3D vector games it targets the UNO Q, whose Cortex-M33 has the speed its per-column
 * floating-point projection needs.
 *
 * <p>After the title, choose the pilot ({@link Controls}): HUMAN walks with the touch screen, CPU lets
 * {@link Autopilot} walk the map (a route {@link RoutePlanner} plans to the exit, or the built-in map's patrol), and
 * tapping the status bar switches between them. With the WAD, an episode menu ({@link Episodes}) follows.
 * {@link Player} walks and opens doors, {@link Renderer} builds each frame and {@link DisplayList} draws
 * only what changed. {@link FrameStats} reports what each frame costs over the serial port.
 */
@Board(ArduinoUnoQ.class)
public final class Doom {
    private static final int FRAME_MILLIS = 40;

    private Doom() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_9600);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        // The map first: planning its route borrows arena the renderer's buffers take over afterwards.
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
        Interludes.title();
        Controls.choosePilot();
        chooseEpisode();
        Random.seed(Clock.micros());
        enterLevel(ceilings, monsters, shots, taken);

        int next = Clock.millis();
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
            if (Player.atExit()) {
                Interludes.complete();
                enterLevel(ceilings, monsters, shots, taken);
                next = Clock.millis();
            } else if (Player.health == 0) {
                Interludes.died();
                Interludes.title();
                Controls.choosePilot();
                chooseEpisode();
                enterLevel(ceilings, monsters, shots, taken);
                next = Clock.millis();
            }
        }
    }

    /** The episode menu, for the WAD's maps; the built-in map has no episodes. */
    private static void chooseEpisode() {
        if (World.fromWad) {
            Episodes.choose();
        }
    }

    /** Starts the map afresh: the marine at the start, every monster back at its post, every item in place. */
    private static void enterLevel(short[] ceilings, short[] monsters, short[] shots, byte[] taken) {
        Player.spawn(ceilings);
        Monsters.reset(monsters, shots);
        Items.reset(taken);
        Weapon.reset();
        Autopilot.restart();
        Hud.drawBar();
        DisplayList.clearView();
    }
}
