package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The explosions: starting one, growing/holding/shrinking it every frame, and any warhead it
 * catches. Split out of {@link Session} to keep that class's own cyclomatic complexity in check;
 * the record layout ({@code B_*}/{@code BLASTS}/{@code BLAST_*}) stays there since {@link Interludes}
 * and {@link MissileCommand} also reference it.
 */
final class Blasts {
    private Blasts() {
    }

    static void startBlast(int[] blasts, int x, int y) {
        int slot = Session.firstInactive(blasts, Session.BLASTS, Session.B_STRIDE);
        if (slot < 0) {
            return;
        }
        int base = slot * Session.B_STRIDE;
        blasts[base + Session.B_ACTIVE] = 1;
        blasts[base + Session.B_X] = x;
        blasts[base + Session.B_Y] = y;
        blasts[base + Session.B_AGE] = 0;
        blasts[base + Session.B_RADIUS] = 0;
    }

    static void updateBlasts(int[] missiles, int[] blasts, boolean[] alive, int[] ammo) {
        boolean groundTouched = false;
        boolean headerTouched = false;
        for (int slot = 0; slot < Session.BLASTS; slot++) {
            int base = slot * Session.B_STRIDE;
            if (blasts[base + Session.B_ACTIVE] == 0) {
                continue;
            }
            int x = blasts[base + Session.B_X];
            int y = blasts[base + Session.B_Y];
            int age = blasts[base + Session.B_AGE] + 1;
            blasts[base + Session.B_AGE] = age;
            int radius = blastRadius(age);
            int shown = blasts[base + Session.B_RADIUS];
            if (age >= Session.BLAST_LIFE) {
                TftTouchShield.fillCircle(x, y, shown, SceneRenderer.SKY);
                blasts[base + Session.B_ACTIVE] = 0;
                groundTouched = groundTouched || y + Session.BLAST_RADIUS >= Session.GROUND_Y - 16;
                headerTouched = headerTouched || y - Session.BLAST_RADIUS <= Session.HEADER;
                continue;
            }
            SceneRenderer.drawBlast(x, y, shown, radius, age);
            blasts[base + Session.B_RADIUS] = radius;

            // Any warhead inside the fireball explodes too.
            for (int m = 0; m < Session.MISSILES; m++) {
                int mBase = m * Session.T_STRIDE;
                if (missiles[mBase + Session.T_ACTIVE] == 0) {
                    continue;
                }
                int dx = missiles[mBase + Session.T_HEAD_X] - x;
                int dy = missiles[mBase + Session.T_HEAD_Y] - y;
                if (dx * dx + dy * dy <= radius * radius) {
                    int headX = missiles[mBase + Session.T_HEAD_X];
                    int headY = missiles[mBase + Session.T_HEAD_Y];
                    SceneRenderer.eraseTrail(missiles, m);
                    Session.addScore(Session.MISSILE_POINTS * Session.multiplier(), alive);
                    Hud.drawHeader();
                    startBlast(blasts, headX, headY);
                }
            }
        }
        if (groundTouched) {
            SceneRenderer.drawGround(alive, ammo);
        }
        if (headerTouched) {
            Hud.drawHeader();
        }
    }

    private static int blastRadius(int age) {
        if (age <= Session.BLAST_RADIUS) {
            return age;
        }
        if (age <= Session.BLAST_RADIUS + Session.BLAST_HOLD) {
            return Session.BLAST_RADIUS;
        }
        return Math.max(0, Session.BLAST_LIFE - age);
    }
}
