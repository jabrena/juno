package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** The puzzle controller/view: translates touch coordinates to board squares and delegates rules to the model. */
final class ChallengeView implements View {
    private static final int FEEDBACK_MILLIS = 1500;

    private final Navigator navigator;
    private final LichessModel model;
    private final LichessBoardView boardView;
    private final Clock clock = ArduinoUno.getClock();
    private int selected = -1;
    private int feedback = LichessModel.MOVE_CORRECT;
    private int feedbackAt;
    private boolean dirty;

    ChallengeView(Navigator navigator, LichessModel model) {
        this.navigator = navigator;
        this.model = model;
        boardView = new LichessBoardView(model);
    }

    @Override
    public void enter() {
        model.resetPuzzle();
        boardView.reset();
        selected = -1;
        feedback = LichessModel.MOVE_CORRECT;
        feedbackAt = 0;
        dirty = true;
    }

    @Override
    public void render() {
        if (feedback != LichessModel.MOVE_CORRECT && clock.millis() - feedbackAt > FEEDBACK_MILLIS) {
            feedback = LichessModel.MOVE_CORRECT;
            dirty = true;
        }
        if (dirty) {
            boardView.draw(selected, feedback);
            dirty = false;
        }
    }

    @Override
    public void touch(int x, int y) {
        if (boardView.isMenuTouch(x, y)) {
            navigator.show(LichessViews.MENU);
            return;
        }
        int square = boardView.squareAt(x, y);
        if (square < 0) {
            return;
        }
        if (selected < 0) {
            if (model.board().pieceAt(square) * model.board().playerColor() > 0) {
                selected = square;
                dirty = true;
            }
            return;
        }
        if (selected == square) {
            selected = -1;
            dirty = true;
            return;
        }
        feedback = model.play(selected, square);
        selected = -1;
        feedbackAt = clock.millis();
        dirty = true;
    }
}
