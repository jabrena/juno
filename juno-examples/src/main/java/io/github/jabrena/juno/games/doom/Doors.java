package io.github.jabrena.juno.games.doom;

/** The map's doors: each opens as the marine comes within reach of it and closes once he is far behind. */
final class Doors {
    private static final int DOOR_REACH = 200;
    private static final int DOOR_FORGET = 520;
    private static final int DOOR_SPEED = 6;

    private Doors() {
    }

    /** Whether standing at ({@code px}, {@code py}) would start a door that is not fully open opening. */
    static boolean wouldOpen(float px, float py, short[] ceilings) {
        for (int d = 0; d < World.doors; d++) {
            float dx = World.doorX[d] - px;
            float dy = World.doorY[d] - py;
            if (dx * dx + dy * dy < DOOR_REACH * DOOR_REACH && ceilings[World.doorSector[d]] < World.doorTop[d]) {
                return true;
            }
        }
        return false;
    }

    /** Opens the doors the marine walks up to and closes the ones left far behind. */
    static void operate(short[] ceilings) {
        for (int d = 0; d < World.doors; d++) {
            int door = World.doorSector[d];
            float dx = World.doorX[d] - Player.x;
            float dy = World.doorY[d] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            int ceiling = ceilings[door];
            if (distance < DOOR_REACH && ceiling < World.doorTop[d]) {
                ceilings[door] = (short) Math.min(World.doorTop[d], ceiling + DOOR_SPEED);
            } else if (distance > DOOR_FORGET && ceiling > World.sectorFloor[door]) {
                ceilings[door] = (short) Math.max(World.sectorFloor[door], ceiling - DOOR_SPEED);
            }
        }
    }
}
