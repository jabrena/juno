package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.util.mvc.ViewRouter;

/** Wires the model, API service and views into the board-side MVC controller. */
public final class LichessController {
    private final ViewRouter router;

    public LichessController(LichessModel model, LichessApi api) {
        router = new ViewRouter(40);
        TvModel tvModel = new TvModel();
        router.register(LichessViews.COVER, new CoverView(router));
        router.register(LichessViews.MENU, new ChallengeMenuView(router, api, model, tvModel));
        router.register(LichessViews.CHALLENGE, new ChallengeView(router, model));
        router.register(LichessViews.TV, new LichessTvView(router, api, tvModel));
        router.register(LichessViews.WIN, new WinView(router));
    }

    public void run() {
        router.start(LichessViews.COVER);
        router.run();
    }
}
