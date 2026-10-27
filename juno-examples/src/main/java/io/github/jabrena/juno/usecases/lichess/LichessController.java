package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.util.mvc.ViewRouter;

/** Wires the model, API service and views into the board-side MVC controller. */
public final class LichessController {
    private final ViewRouter router;

    public LichessController(LichessModel model, LichessApi api) {
        router = new ViewRouter(40);
        router.register(LichessViews.COVER, new CoverView(router));
        router.register(LichessViews.MENU, new ChallengeMenuView(router, api, model));
        router.register(LichessViews.CHALLENGE, new ChallengeView(router, model));
    }

    public void run() {
        router.start(LichessViews.COVER);
        router.run();
    }
}
