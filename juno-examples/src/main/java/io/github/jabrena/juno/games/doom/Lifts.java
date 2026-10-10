package io.github.jabrena.juno.games.doom;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * DOOM's lifts and remotely opened doors, which a line names by its tag rather than by the sector behind it. While a
 * map is read, {@link #noteLine} keeps each lift line and the tags of door lines, and {@link #resolve} then finds the
 * tagged sectors. In play a lift rests at its top; when the marine comes within reach of one of its lines it lowers
 * to its lowest neighbouring floor, waits as DOOM's does, and rises again, carrying whoever stands on it. A remote
 * door becomes one of {@link World}'s doors, which open as the marine approaches.
 *
 * <p>For route planning ({@link #planning}) every lift is down and may be left upward at any height, since it
 * carries the marine to its top; so a route can lead across a lift either way.
 */
final class Lifts {
    // The most any of E1M1-E4M9 needs, rounded up: lift sectors, lift lines, and lines opening doors by tag.
    static final int MAX_LIFTS = 24;
    static final int MAX_LINES = 136;
    static final int MAX_DOOR_TAGS = 48;
    /** Lift tables plus their block headers, for {@link World#WAD_ARENA_BYTES}. */
    static final int ARENA_BYTES = 2 * (7 * MAX_LIFTS + 2 * MAX_LINES + MAX_DOOR_TAGS) + 10 * 16;

    private static final int REST = 0;
    private static final int LOWERING = 1;
    private static final int WAITING = 2;
    private static final int RISING = 3;
    /** Floor units a lift moves per frame: DOOM's 4 per tic at 35 tics a second, over 40 ms frames. */
    private static final int SPEED = 6;
    /** Frames a lift waits at the bottom: DOOM's three seconds. */
    private static final int WAIT_FRAMES = 75;
    /** How close the marine must come to a lift line to work it: DOOM's use range plus the body radius. */
    private static final float REACH = 64f + Player.RADIUS;

    static int lifts;
    static int lines;
    /** Whether {@link Player#blocked} answers for the planner: lifts down and able to carry the marine up. */
    static boolean planning;

    private static short[] sector;
    private static short[] top;
    private static short[] low;
    private static short[] tag;
    private static short[] state;
    private static short[] timer;
    private static short[] line;
    private static short[] lineTag;
    private static short[] doorTag;
    private static short[] saved;
    private static int doorTags;
    /** Set when a map has more lifts or tagged lines than the tables hold: it then does not load. */
    private static boolean overflow;

    private Lifts() {
    }

    /** Allocates the tables, once, sized for the largest map of the WAD. */
    static void allocateForWad() {
        short[] sectors = new short[MAX_LIFTS];
        short[] tops = new short[MAX_LIFTS];
        short[] lows = new short[MAX_LIFTS];
        short[] tags = new short[MAX_LIFTS];
        short[] states = new short[MAX_LIFTS];
        short[] timers = new short[MAX_LIFTS];
        short[] floors = new short[MAX_LIFTS];
        short[] lineIndex = new short[MAX_LINES];
        short[] lineTags = new short[MAX_LINES];
        short[] doors = new short[MAX_DOOR_TAGS];
        publish(sectors, tops, lows, tags, states, timers, floors);
        publishLines(lineIndex, lineTags, doors);
    }

    /** The built-in map has no lifts: tables of one entry, never used. */
    static void allocateForBuiltIn() {
        short[] sectors = new short[1];
        short[] tops = new short[1];
        short[] lows = new short[1];
        short[] tags = new short[1];
        short[] states = new short[1];
        short[] timers = new short[1];
        short[] floors = new short[1];
        short[] lineIndex = new short[1];
        short[] lineTags = new short[1];
        short[] doors = new short[1];
        publish(sectors, tops, lows, tags, states, timers, floors);
        publishLines(lineIndex, lineTags, doors);
    }

    private static void publish(short[] sectors, short[] tops, short[] lows, short[] tags, short[] states,
                                short[] timers, short[] floors) {
        sector = sectors;
        top = tops;
        low = lows;
        tag = tags;
        state = states;
        timer = timers;
        saved = floors;
        clear();
    }

    private static void publishLines(short[] lineIndex, short[] lineTags, short[] doors) {
        line = lineIndex;
        lineTag = lineTags;
        doorTag = doors;
    }

    /** Forgets the previous map's lifts and door tags, before a map is read. */
    static void clear() {
        lifts = 0;
        lines = 0;
        doorTags = 0;
        overflow = false;
    }

    /** Keeps a linedef that works a lift, or the tag of one that opens a door elsewhere. */
    static void noteLine(int index, int special, int lineTagValue) {
        if (lineTagValue == 0) {
            return;
        }
        if (isLift(special)) {
            overflow = overflow || lines == MAX_LINES;
            if (!overflow) {
                line[lines] = (short) index;
                lineTag[lines] = (short) lineTagValue;
                lines = lines + 1;
            }
        } else if (isRemoteDoor(special)) {
            overflow = overflow || doorTags == MAX_DOOR_TAGS;
            if (!overflow) {
                doorTag[doorTags] = (short) lineTagValue;
                doorTags = doorTags + 1;
            }
        }
    }

    /**
     * Reads every sector's tag (its last field) and makes each sector a lift line names a lift, and each one a door
     * line names a door; {@code false} when the map has more lifts, tagged lines or doors than the tables hold.
     */
    static boolean resolve(RandomAccessFile wad, byte[] record, int sectors, int sectorBytes) throws IOException {
        if (overflow) {
            return false;
        }
        if (lines == 0 && doorTags == 0) {
            Loading.advance(World.sectors);
            return true;
        }
        for (int s = 0; s < World.sectors; s++) {
            wad.seek(sectors + s * sectorBytes + sectorBytes - 2);
            wad.readFully(record, 0, 2);
            Loading.advance(1);
            int sectorTag = WadLevel.int16(record, 0);
            if (sectorTag == 0) {
                continue;
            }
            if (namesLift(sectorTag)) {
                if (lifts == MAX_LIFTS) {
                    return false;
                }
                addLift(s, sectorTag);
            }
            if (namesDoor(sectorTag) && !WadLevel.addDoor(s)) {
                return false;
            }
        }
        return true;
    }

    /** Puts every lift back at the top, as the map starts. */
    static void reset() {
        for (int i = 0; i < lifts; i++) {
            World.sectorFloor[sector[i]] = top[i];
            state[i] = REST;
            timer[i] = 0;
        }
    }

    /** Moves the lifts one frame: a resting one the marine reaches lowers, waits, and rises again. */
    static void operate(float x, float y) {
        for (int i = 0; i < lifts; i++) {
            int s = sector[i];
            switch (state[i]) {
                case REST -> {
                    if (reached(i, x, y)) {
                        state[i] = LOWERING;
                    }
                }
                case LOWERING -> {
                    World.sectorFloor[s] = (short) Math.max(low[i], World.sectorFloor[s] - SPEED);
                    if (World.sectorFloor[s] == low[i]) {
                        state[i] = WAITING;
                        timer[i] = WAIT_FRAMES;
                    }
                }
                case WAITING -> {
                    timer[i] = (short) (timer[i] - 1);
                    if (timer[i] <= 0) {
                        state[i] = RISING;
                    }
                }
                default -> {
                    World.sectorFloor[s] = (short) Math.min(top[i], World.sectorFloor[s] + SPEED);
                    if (World.sectorFloor[s] == top[i]) {
                        state[i] = REST;
                    }
                }
            }
        }
    }

    /** Whether {@code s} is a lift, which the planner lets the marine ride up out of. */
    static boolean isLiftSector(int s) {
        for (int i = 0; i < lifts; i++) {
            if (sector[i] == s) {
                return true;
            }
        }
        return false;
    }

    /** Lowers every lift for the planner, keeping where each one really is. */
    static void beginPlanning() {
        for (int i = 0; i < lifts; i++) {
            saved[i] = World.sectorFloor[sector[i]];
            World.sectorFloor[sector[i]] = low[i];
        }
        planning = true;
    }

    /** Puts the lifts back where they were before planning. */
    static void endPlanning() {
        planning = false;
        for (int i = 0; i < lifts; i++) {
            World.sectorFloor[sector[i]] = saved[i];
        }
    }

    /** DOOM's lift lines: W1/WR/S1/SR lower-wait-raise (10, 88, 21, 62) and their fast versions (120-123). */
    static boolean isLift(int special) {
        return special == 10 || special == 21 || special == 62 || special == 88
                || special >= 120 && special <= 123;
    }

    /** Line specials that open a door in the sectors they tag, from a trigger, switch or shot somewhere else. */
    static boolean isRemoteDoor(int special) {
        return special == 2 || special == 4 || special == 29 || special == 46 || special == 61 || special == 63
                || special == 86 || special == 90 || special == 103;
    }

    private static boolean namesLift(int sectorTag) {
        for (int i = 0; i < lines; i++) {
            if (lineTag[i] == sectorTag) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesDoor(int sectorTag) {
        for (int i = 0; i < doorTags; i++) {
            if (doorTag[i] == sectorTag) {
                return true;
            }
        }
        return false;
    }

    /** A lift's top is where the map leaves it; its bottom the lowest floor next to it, as DOOM's lifts go. */
    private static void addLift(int s, int sectorTag) {
        int lowest = World.sectorFloor[s];
        for (int l = 0; l < World.lines; l++) {
            int front = World.lineFront[l];
            int back = World.lineBack[l];
            int other = front == s ? back : back == s ? front : -1;
            if (other >= 0) {
                lowest = Math.min(lowest, World.sectorFloor[other]);
            }
        }
        sector[lifts] = (short) s;
        top[lifts] = World.sectorFloor[s];
        low[lifts] = (short) lowest;
        tag[lifts] = (short) sectorTag;
        state[lifts] = REST;
        timer[lifts] = 0;
        lifts = lifts + 1;
    }

    /** Whether the marine at ({@code x}, {@code y}) is within reach of one of lift {@code i}'s lines. */
    private static boolean reached(int i, float x, float y) {
        for (int l = 0; l < lines; l++) {
            if (lineTag[l] == tag[i] && Clearance.distanceToLine(x, y, line[l]) < REACH) {
                return true;
            }
        }
        return false;
    }
}
