package io.github.jabrena.juno.games.doom2;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.ViewRouter;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.util.Delay;
import io.github.jabrena.juno.games.doom.DoomEngine;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
import io.github.jabrena.juno.games.doom2.cover.DoomCoverView;
import io.github.jabrena.juno.games.doom2.death.DeathView;
import io.github.jabrena.juno.games.doom2.episode.EpisodeSelectionView;
import io.github.jabrena.juno.games.doom2.gameplay.GameplayView;
import io.github.jabrena.juno.games.doom2.map.DoomMapLoader;
import io.github.jabrena.juno.games.doom2.map.DoomMapLoadingView;
import io.github.jabrena.juno.games.doom2.map.EngineMapReader;
import io.github.jabrena.juno.games.doom2.pilot.PilotSelectionView;
import io.github.jabrena.juno.games.doom2.report.ReportView;
import io.github.jabrena.juno.games.doom2.services.DoomStartupService;
import io.github.jabrena.juno.games.doom2.skill.SkillSelectionView;

/**
 * Incremental MVC migration of DOOM. The original game remains in {@code games.doom}; its screens move here one at a
 * time as views, while the maps are played by the original engine through {@link DoomEngine}. After the cover, pilot,
 * episode and skill, each map loads under the engine's progress bar and is played; an exit shows the report while
 * the next map loads, and a death returns to the cover. Everything runs on the main thread: the engine's tables leave no room on the UNO Q for
 * thread stacks. The WAD maps need the engine's arena: build with {@code -Djuno.Xmx=92k}.
 */
@Board(ArduinoUnoQ.class)
public final class Doom2 {
    private static final int FRAME_MILLIS = 40;

    private Doom2() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Doom2: starting MVC cover");

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        // First, so the WAD-sized tables and renderer buffers find the arena empty.
        DoomEngine engine = new DoomEngine();

        Doom2State model = new Doom2State();
        ViewRouter controller = new ViewRouter(FRAME_MILLIS);
        controller.register(Doom2Views.COVER, new DoomCoverView(controller, model));
        controller.register(Doom2Views.PILOT, new PilotSelectionView(controller, model));
        controller.register(Doom2Views.EPISODES, new EpisodeSelectionView(controller, model));
        controller.register(Doom2Views.SKILL, new SkillSelectionView(controller, model));
        DoomMapLoader mapLoader = new DoomMapLoader(new EngineMapReader(engine));
        controller.register(Doom2Views.MAP_LOADING, new DoomMapLoadingView(controller, model, mapLoader));
        controller.register(Doom2Views.GAMEPLAY, new GameplayView(controller, model, engine));
        controller.register(Doom2Views.DEATH, new DeathView(controller));
        controller.register(Doom2Views.REPORT, new ReportView(controller, model, engine, mapLoader));
        controller.start(Doom2Views.COVER);

        DoomStartupService startup = new DoomStartupService(model);
        startup.begin();
        controller.background(startup);
        controller.run();
    }
}
