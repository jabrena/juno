package io.github.jabrena.juno.games.doom.commons;

/** The mutable game state shared by Doom views, beginning with the cover animation. */
public final class DoomState {
    private static final int BLINK_FRAMES = 21;
    private static final int VISIBLE_FRAMES = 13;
    /** The last map of an episode played in order; E?M9 is the secret level, reached only by a secret exit. */
    public static final int LAST_MAP = 8;
    /** DOOM's five skill levels, from I'M TOO YOUNG TO DIE to NIGHTMARE!. */
    public static final int SKILLS = 5;

    private int coverFrame;
    private boolean autopilot = true;
    private volatile boolean cardReady;
    private volatile boolean wadScanComplete;
    private volatile int episodesInWad;
    private volatile boolean startupComplete;
    private int episode;
    private int map = 1;
    private boolean newGame;
    private int skill = 3;

    /** Restarts the cover's prompt animation. */
    public void resetCover() {
        coverFrame = 0;
    }

    /** Whether the cover prompt is visible in the current animation frame. */
    public boolean coverPromptVisible() {
        return coverFrame % BLINK_FRAMES < VISIBLE_FRAMES;
    }

    /** Advances the cover animation by one frame. */
    public void advanceCover() {
        coverFrame = coverFrame + 1;
    }

    /** Whether the CPU pilot is selected. */
    public boolean autopilot() {
        return autopilot;
    }

    /** Selects the human pilot. */
    public void chooseHuman() {
        autopilot = false;
    }

    /** Selects the CPU pilot. */
    public void chooseCpu() {
        autopilot = true;
    }

    /** Whether the SD card was available at startup. */
    public boolean cardReady() {
        return cardReady;
    }

    /** Publishes the SD-card startup result before its capacity is measured. */
    public void publishCardReady(boolean ready) {
        cardReady = ready;
    }

    /** Whether the startup service has finished inspecting DOOM1.WAD. */
    public boolean wadScanComplete() {
        return wadScanComplete;
    }

    /** A bit for each episode whose E?M1 marker was found in DOOM1.WAD. */
    public int episodesInWad() {
        return episodesInWad;
    }

    /** Publishes the WAD catalog as one completed startup result. */
    public void publishEpisodesInWad(int episodes) {
        episodesInWad = episodes;
        wadScanComplete = true;
    }

    public boolean episodeAvailable(int candidate) {
        return candidate >= 1 && candidate <= 4 && (episodesInWad & 1 << candidate) != 0;
    }

    public int episode() {
        return episode;
    }

    public void chooseEpisode(int chosenEpisode) {
        if (episodeAvailable(chosenEpisode)) {
            episode = chosenEpisode;
            map = 1;
            newGame = true;
        }
    }

    /** The chosen skill, 1 to {@link #SKILLS}; HURT ME PLENTY until another is chosen. */
    public int skill() {
        return skill;
    }

    public void chooseSkill(int chosenSkill) {
        if (chosenSkill >= 1 && chosenSkill <= SKILLS) {
            skill = chosenSkill;
        }
    }

    /** The selected map within the episode; episode selection starts at E?M1. */
    public int map() {
        return map;
    }

    /** Whether the map is the episode's last, so finishing it ends the episode. */
    public boolean lastMap() {
        return map >= LAST_MAP;
    }

    /** Moves on to the episode's next map, keeping the marine's arsenal. */
    public void advanceMap() {
        if (!lastMap()) {
            map = map + 1;
        }
    }

    /** Whether the map about to start begins a new game (the first map after choosing an episode); asks once. */
    public boolean takeNewGame() {
        boolean fresh = newGame;
        newGame = false;
        return fresh;
    }

    /** Whether the startup service has finished with the SD card, so another thread may read it. */
    public boolean startupComplete() {
        return startupComplete;
    }

    public void publishStartupComplete() {
        startupComplete = true;
    }
}
