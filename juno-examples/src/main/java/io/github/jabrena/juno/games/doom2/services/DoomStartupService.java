package io.github.jabrena.juno.games.doom2.services;

import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.mvc.Task;
import io.github.jabrena.juno.api.thirdparty.sd.SdCard;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;

/**
 * Owns Doom2's startup I/O. The controller advances it one short step per frame, on the main thread, while it
 * catalogs DOOM1.WAD a block at a time. Views consume only the results published in the model, and the map loader
 * waits for {@link Doom2State#startupComplete()} before it touches the card.
 */
public final class DoomStartupService implements Task {
    private final Doom2State model;
    private final DoomWadCatalog catalog = new DoomWadCatalog();
    private boolean done;

    public DoomStartupService(Doom2State model) {
        this.model = model;
    }

    /** Initializes the SD card before the first frame; without it, startup completes at once. */
    public void begin() {
        boolean ready = SdCard.begin();
        model.publishCardReady(ready);
        if (!ready) {
            model.publishEpisodesInWad(0);
            Serial.println("Doom2: SD card unavailable");
            finish();
        }
    }

    @Override
    public void step() {
        if (done) {
            return;
        }
        catalog.step();
        if (catalog.complete()) {
            model.publishEpisodesInWad(catalog.episodes());
            finish();
        }
    }

    private void finish() {
        done = true;
        model.publishStartupComplete();
    }
}
