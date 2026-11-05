package io.github.jabrena.juno.usecases.lichess.controller;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.lichess.challenge.ChallengeMenuView;
import io.github.jabrena.juno.usecases.lichess.challenge.ChallengeView;
import io.github.jabrena.juno.usecases.lichess.challenge.LichessModel;
import io.github.jabrena.juno.usecases.lichess.commons.LichessViews;
import io.github.jabrena.juno.usecases.lichess.cover.CoverView;
import io.github.jabrena.juno.usecases.lichess.services.LichessApi;
import io.github.jabrena.juno.usecases.lichess.tv.LichessTvView;
import io.github.jabrena.juno.usecases.lichess.tv.TvModel;
import io.github.jabrena.juno.usecases.lichess.win.WinView;
import io.github.jabrena.juno.util.mvc.ViewRouter;

/** Wires the model, API service and views into the board-side MVC controller. */
public final class LichessController {
    private final ViewRouter router;

    public LichessController(TftTouchShield tft, LichessModel model, LichessApi api) {
        router = new ViewRouter(tft, 40);
        TvModel tvModel = new TvModel();
        router.register(LichessViews.COVER, new CoverView(tft, router));
        router.register(LichessViews.MENU, new ChallengeMenuView(tft, router, api, model, tvModel));
        router.register(LichessViews.CHALLENGE, new ChallengeView(tft, router, model));
        router.register(LichessViews.TV, new LichessTvView(tft, router, api, tvModel));
        router.register(LichessViews.WIN, new WinView(tft, router));
    }

    public void run() {
        router.start(LichessViews.COVER);
        router.run();
    }
}
