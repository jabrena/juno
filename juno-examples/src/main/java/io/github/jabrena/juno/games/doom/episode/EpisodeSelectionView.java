package io.github.jabrena.juno.games.doom.episode;

import io.github.jabrena.juno.games.doom.commons.ChoiceList;
import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.commons.Screen;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Doom1-style episode selection, enabled from the E?M1 markers discovered in DOOM1.WAD. */
public final class EpisodeSelectionView implements View {
    private static final int UNSHOWN = Integer.MIN_VALUE;

    private final Navigator navigator;
    private final DoomState model;
    private final ChoiceList list = new ChoiceList(46, 32, 4);
    private int shownEpisodes = UNSHOWN;
    private int shownEpisode = UNSHOWN;
    private boolean shownScanComplete;

    public EpisodeSelectionView(Navigator navigator, DoomState model) {
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(Screen.BACKGROUND);
        Screen.showCentered("CHOOSE EPISODE", 8, 3, TftTouchShield.RED);
        Screen.drawBack();
        shownEpisodes = UNSHOWN;
        shownEpisode = UNSHOWN;
        shownScanComplete = !model.wadScanComplete();
        refresh();
        Serial.println("Doom: episode view active");
    }

    @Override
    public void render() {
        refresh();
    }

    @Override
    public void touch(int x, int y) {
        if (Screen.isBack(x, y)) {
            Serial.println("Doom: returning to pilot view");
            navigator.back();
            return;
        }
        int row = list.rowAt(x, y);
        int episode = row + 1;
        if (row >= 0 && model.episodeAvailable(episode)) {
            model.chooseEpisode(episode);
            drawEpisodes(model.episodesInWad());
            shownEpisode = episode;
            Serial.print("Doom: episode selected E");
            Serial.println(episode);
            navigator.show(DoomViews.SKILL);
        }
    }

    private void refresh() {
        boolean scanComplete = model.wadScanComplete();
        int episodes = model.episodesInWad();
        int episode = model.episode();
        if (scanComplete == shownScanComplete && episodes == shownEpisodes && episode == shownEpisode) {
            return;
        }
        shownScanComplete = scanComplete;
        shownEpisodes = episodes;
        shownEpisode = episode;
        TftTouchShield.fillRect(0, 38, Screen.WIDTH, 160, Screen.BACKGROUND);
        if (!scanComplete) {
            Screen.showCentered("SCANNING DOOM1.WAD...", 102, 2, TftTouchShield.YELLOW);
        } else if (episodes == 0) {
            Screen.showCentered("DOOM1.WAD NOT FOUND", 92, 2, TftTouchShield.RED);
            Screen.showCentered("Copy a valid WAD to the SD root", 122, 1, TftTouchShield.WHITE);
        } else {
            drawEpisodes(episodes);
        }
    }

    private void drawEpisodes(int episodes) {
        for (int episode = 1; episode <= 4; episode++) {
            boolean enabled = (episodes & 1 << episode) != 0;
            list.draw(episode - 1, name(episode), enabled ? map(episode) : "Not in this DOOM1.WAD", enabled,
                    model.episode() == episode);
        }
    }

    private static String name(int episode) {
        return switch (episode) {
            case 1 -> "KNEE-DEEP IN THE DEAD";
            case 2 -> "THE SHORES OF HELL";
            case 3 -> "INFERNO";
            default -> "THY FLESH CONSUMED";
        };
    }

    private static String map(int episode) {
        return switch (episode) {
            case 1 -> "E1M1 Hangar";
            case 2 -> "E2M1 Deimos Anomaly";
            case 3 -> "E3M1 Hell Keep";
            default -> "E4M1 Hell Beneath";
        };
    }

}
