package io.github.jabrena.juno.games.doom;

import java.io.IOException;
import java.io.RandomAccessFile;

/** The THINGS lump of a WAD map: where the marine starts and which monsters and pickups {@link WadLevel} places. */
final class WadThings {
    static final int THING_BYTES = 10;
    /** THINGS flag bits: placed on Ultra-Violence ("hard") skill, and only in multiplayer. */
    private static final int SKILL_HARD = 4;
    private static final int MULTIPLAYER_ONLY = 16;
    private static final int PLAYER_ONE_START = 1;

    private WadThings() {
    }

    /** The player start, plus the monsters and pickups placed on Ultra-Violence in single player. */
    static boolean read(RandomAccessFile wad, byte[] record, int offset, int things)
            throws IOException {
        World.monsters = 0;
        World.items = 0;
        wad.seek(offset);
        for (int thing = 0; thing < things; thing++) {
            wad.readFully(record, 0, THING_BYTES);
            int x = WadLevel.int16(record, 0);
            int y = WadLevel.int16(record, 2);
            int type = WadLevel.int16(record, 6);
            int flags = WadLevel.int16(record, 8);
            boolean placed = (flags & SKILL_HARD) != 0 && (flags & MULTIPLAYER_ONLY) == 0;
            int monster = monsterKind(type);
            int item = itemKind(type);
            if (type == PLAYER_ONE_START) {
                World.startX = x;
                World.startY = y;
                World.startAngle = WadLevel.int16(record, 4);
            } else if (placed && monster >= 0) {
                if (World.monsters == World.MAX_MONSTERS) {
                    return false;
                }
                World.monsterX[World.monsters] = (short) x;
                World.monsterY[World.monsters] = (short) y;
                World.monsterKind[World.monsters] = (short) monster;
                World.monsters = World.monsters + 1;
            } else if (placed && item >= 0) {
                if (World.items == World.MAX_ITEMS) {
                    return false;
                }
                World.itemX[World.items] = (short) x;
                World.itemY[World.items] = (short) y;
                World.itemKind[World.items] = (short) item;
                World.items = World.items + 1;
            }
        }
        return true;
    }

    /** The monster kinds the engine animates: zombieman, shotgun sergeant, imp, demon; -1 for anything else. */
    private static int monsterKind(int type) {
        return switch (type) {
            case 3004 -> 0;
            case 9 -> 1;
            case 3001 -> 2;
            case 3002 -> 3;
            default -> -1;
        };
    }

    /** Pickups: health bonus, stimpack, medikit, armor bonus, green armor, blue armor; -1 for anything else. */
    private static int itemKind(int type) {
        return switch (type) {
            case 2014 -> 0;
            case 2011 -> 1;
            case 2012 -> 2;
            case 2015 -> 3;
            case 2018 -> 4;
            case 2019 -> 5;
            default -> -1;
        };
    }
}
