package io.github.jabrena.juno.games.empirestrikesback;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.ATAT_HITS;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_FLAG;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_HP;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATAT;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The scenes before the flying: the film's opening on Hoth and the title. */
final class Interludes {
    // The opening: the probe's fall, then the walkers' advance.
    private static final int POD_FRAMES = 44;
    private static final int WALK_FRAMES = 70;
    private static final String TITLE_TOP = "THE EMPIRE";
    private static final String TITLE_BOTTOM = "STRIKES BACK";

    private Interludes() {
    }

    /**
     * The opening, after the film: snow falls on Hoth as an Imperial probe streaks down and lands in
     * a flash, then two AT-ATs stride in from the distance to where the title screen shows them.
     */
    static void opening(short[] lines, int[] ents) {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        DisplayList.forget();
        Session.round = Round.PROBES;
        Camera.center();
        Session.travel = 0;
        Entities.clear(ents);
        int impactX = 0;
        for (int f = 0; f < POD_FRAMES; f++) {
            Session.frame = f;
            DisplayList.begin();
            SceneRenderer.drawHoth(lines);
            drawSnow(lines, f);
            // The probe's pod: a white-hot head with a fiery tail, falling towards the horizon.
            int x = 300 - f * 5;
            int y = DisplayList.HEADER + 4 + f * (Camera.CENTER_Y - DisplayList.HEADER - 6) / POD_FRAMES;
            DisplayList.addLine(lines, x, y, x + 30, y - 16, SceneRenderer.FIRE_A);
            DisplayList.addLine(lines, x + 1, y + 1, x + 22, y - 10, SceneRenderer.FIRE_B);
            DisplayList.addLine(lines, x - 1, y, x + 1, y, TftTouchShield.WHITE);
            impactX = x;
            DisplayList.present(lines);
            Delay.millis(30);
        }
        for (int r = 3; r <= 36; r = r + 3) {
            TftTouchShield.drawCircle(impactX, Camera.CENTER_Y, r, (r & 4) == 0 ? TftTouchShield.WHITE : SceneRenderer.FIRE_A);
            Delay.millis(25);
        }
        Delay.millis(200);
        DisplayList.clearView();
        // The walkers: the near one ends at z 1500, the far one at 2300, as on the title screen.
        ents[E_TYPE] = T_ATAT;
        ents[E_X] = 120;
        ents[E_HP] = ATAT_HITS;
        ents[E_FLAG] = -1;
        ents[E_STRIDE + E_TYPE] = T_ATAT;
        ents[E_STRIDE + E_X] = -520;
        ents[E_STRIDE + E_HP] = ATAT_HITS;
        ents[E_STRIDE + E_FLAG] = -1;
        int far = Camera.FAR;
        for (int f = 0; f <= WALK_FRAMES; f++) {
            Session.frame = POD_FRAMES + f;
            DisplayList.begin();
            SceneRenderer.drawHoth(lines);
            drawSnow(lines, POD_FRAMES + f);
            ents[E_Z] = far + 1400 - (far + 1400 - 1500) * f / WALK_FRAMES;
            ents[E_STRIDE + E_Z] = far + 900 - (far + 900 - 2300) * f / WALK_FRAMES;
            WalkersRound.drawAtat(lines, ents, E_STRIDE);
            WalkersRound.drawAtat(lines, ents, 0);
            DisplayList.present(lines);
            Delay.millis(30);
        }
        Entities.clear(ents);
    }

    /** Snowflakes drifting down and a little sideways, each at its own pace. */
    private static void drawSnow(short[] lines, int f) {
        for (int i = 0; i < 30; i++) {
            int x = (i * 97 + f * (1 + i % 3) / 2) % DisplayList.WIDTH;
            int y = DisplayList.HEADER + (i * 53 + f * (2 + i % 3)) % (DisplayList.HEIGHT - DisplayList.HEADER);
            DisplayList.addLine(lines, x, y, x, y, TftTouchShield.WHITE);
        }
    }

    /**
     * The title screen, over the last frame of the opening: the two title lines assemble letter by
     * letter, their spacing tightening until they lock into place, then flash from white to yellow.
     */
    static void drawTitle(byte[] letter) {
        // At 27 pixels apart "STRIKES BACK" still fits the screen; any wider and it would wrap.
        for (int spacing = 27; spacing >= 18; spacing = spacing - 3) {
            TftTouchShield.fillRect(0, 28, DisplayList.WIDTH, 56, DisplayList.SPACE);
            spaceOut(TITLE_TOP, 30, spacing, letter);
            spaceOut(TITLE_BOTTOM, 58, spacing, letter);
            Delay.millis(110);
        }
        Hud.showCentered(TITLE_TOP, 30, 3, TftTouchShield.WHITE);
        Hud.showCentered(TITLE_BOTTOM, 58, 3, TftTouchShield.WHITE);
        Delay.millis(100);
        Hud.showCentered(TITLE_TOP, 30, 3, TftTouchShield.YELLOW);
        Hud.showCentered(TITLE_BOTTOM, 58, 3, TftTouchShield.YELLOW);
        Hud.showCentered("Drag to steer and aim, tap to fire", 204, 1, TftTouchShield.WHITE);
        Hud.showCentered("Tap to start", 218, 1, TftTouchShield.CYAN);
    }

    /** One title line at text size 3 with its letters {@code spacing} pixels apart, centered. */
    private static void spaceOut(String text, int y, int spacing, byte[] letter) {
        int left = (DisplayList.WIDTH - (text.length() - 1) * spacing - 18) / 2;
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(SceneRenderer.SNOW, DisplayList.SPACE);
        for (int i = 0; i < text.length(); i++) {
            letter[0] = (byte) text.charAt(i);
            TftTouchShield.setCursor(left + i * spacing, y);
            TftTouchShield.print(letter, 1);
        }
    }
}
