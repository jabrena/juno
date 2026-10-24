package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.cover.DoomCoverView;
import io.github.jabrena.juno.games.doom.death.DeathView;
import io.github.jabrena.juno.games.doom.engine.DoomEngine;
import io.github.jabrena.juno.games.doom.episode.EpisodeSelectionView;
import io.github.jabrena.juno.games.doom.gameplay.GameplayView;
import io.github.jabrena.juno.games.doom.map.DoomMapLoader;
import io.github.jabrena.juno.games.doom.map.DoomMapLoadingView;
import io.github.jabrena.juno.games.doom.map.EngineMapReader;
import io.github.jabrena.juno.games.doom.pilot.PilotSelectionView;
import io.github.jabrena.juno.games.doom.report.ReportView;
import io.github.jabrena.juno.games.doom.services.DoomStartupService;
import io.github.jabrena.juno.games.doom.skill.SkillSelectionView;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.mvc.ViewRouter;

/**
 * MVC implementation of DOOM. This entrypoint owns the cover, pilot, episode, skill, death and report views, while
 * maps are played by the engine through {@link DoomEngine}. After the cover, pilot,
 * episode and skill, each map loads under the engine's progress bar and is played; an exit shows the report while
 * the next map loads, and a death returns to the cover. Everything runs on the main thread: the engine's tables leave no room on the UNO Q for
 * thread stacks. The WAD maps need the engine's arena: build with {@code -Djuno.Xmx=92k}.
 */
@Board(ArduinoOneQBoard.class)
public final class Doom {
    private static final int FRAME_MILLIS = 40;

    private Doom() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Doom: starting MVC cover");

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        // First, so the WAD-sized tables and renderer buffers find the arena empty.
        DoomEngine engine = new DoomEngine();

        DoomState model = new DoomState();
        ViewRouter controller = new ViewRouter(FRAME_MILLIS);
        controller.register(DoomViews.COVER, new DoomCoverView(controller, model));
        controller.register(DoomViews.PILOT, new PilotSelectionView(controller, model));
        controller.register(DoomViews.EPISODES, new EpisodeSelectionView(controller, model));
        controller.register(DoomViews.SKILL, new SkillSelectionView(controller, model));
        DoomMapLoader mapLoader = new DoomMapLoader(new EngineMapReader(engine));
        controller.register(DoomViews.MAP_LOADING, new DoomMapLoadingView(controller, model, mapLoader));
        controller.register(DoomViews.GAMEPLAY, new GameplayView(controller, model, engine));
        controller.register(DoomViews.DEATH, new DeathView(controller));
        controller.register(DoomViews.REPORT, new ReportView(controller, model, engine, mapLoader));
        controller.start(DoomViews.COVER);

        DoomStartupService startup = new DoomStartupService(model, TftTouchShield.getSdCard());
        startup.begin();
        controller.background(startup);
        controller.run();
    }
}
