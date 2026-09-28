package io.github.jabrena.juno.games.startrek;

import static io.github.jabrena.juno.games.startrek.DisplayList.BRIDGE_BOTTOM;
import static io.github.jabrena.juno.games.startrek.DisplayList.BRIDGE_TOP;
import static io.github.jabrena.juno.games.startrek.DisplayList.addLine;
import static io.github.jabrena.juno.games.startrek.Entities.ENTITIES;
import static io.github.jabrena.juno.games.startrek.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.startrek.Entities.E_TYPE;
import static io.github.jabrena.juno.games.startrek.Entities.E_X;
import static io.github.jabrena.juno.games.startrek.Entities.E_Y;
import static io.github.jabrena.juno.games.startrek.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.startrek.Entities.T_KLINGON;
import static io.github.jabrena.juno.games.startrek.Entities.T_NOMAD;
import static io.github.jabrena.juno.games.startrek.Entities.T_NONE;
import static io.github.jabrena.juno.games.startrek.Entities.T_PHOTON;
import static io.github.jabrena.juno.games.startrek.Entities.T_SAUCER;
import static io.github.jabrena.juno.games.startrek.Entities.T_STARBASE;
import static io.github.jabrena.juno.games.startrek.Geometry.FIX;
import static io.github.jabrena.juno.games.startrek.Geometry.dirX;
import static io.github.jabrena.juno.games.startrek.Geometry.dirY;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.FRAME;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.KLINGON;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.NOMAD;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.PHOTON;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.SAUCER;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.STAR;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.STARBASE;
import static io.github.jabrena.juno.games.startrek.SceneRenderer.TORPEDO;

/** The small window on the right that looks ahead from the bridge. */
final class BridgeView {
    private static final int BRIDGE_X = 271;
    private static final int BRIDGE_Y = 60;
    private static final int BRIDGE_FOCAL = 60;

    private BridgeView() {
    }

    /**
     * The view ahead from the bridge: stars at their bearings, and whatever lies within 40 degrees
     * of the heading, drawn in perspective on the plane of the sector.
     */
    static void draw(short[] lines, int[] ents) {
        for (int i = 0; i < 24; i++) {
            int relative = Geometry.angleBetween(Enterprise.heading, i * 15 + 7);
            if (Math.abs(relative) < 40) {
                int x = BRIDGE_X + Math.round(BRIDGE_FOCAL * (float) Math.tan(Math.toRadians(relative)));
                int y = BRIDGE_TOP + 8 + (i * 29) % (BRIDGE_BOTTOM - BRIDGE_TOP - 16);
                addLine(lines, x, y, x, y, STAR);
            }
        }
        float radians = (float) Math.toRadians(Enterprise.heading);
        float hx = (float) Math.sin(radians);
        float hy = -(float) Math.cos(radians);
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE || type == T_DEBRIS) {
                continue;
            }
            float dx = Geometry.wrap(ents[b + E_X] - Enterprise.x) / (float) FIX;
            float dy = Geometry.wrap(ents[b + E_Y] - Enterprise.y) / (float) FIX;
            int ahead = Math.round(dx * hx + dy * hy);
            int side = Math.round(dx * -hy + dy * hx);
            if (ahead < 20 || Math.abs(side) > ahead) {
                continue;
            }
            int x = BRIDGE_X + side * BRIDGE_FOCAL / ahead;
            int y = BRIDGE_Y + (slot % 3 - 1) * 400 / ahead;
            int size = Math.max(1, Entities.radius(type) * BRIDGE_FOCAL / ahead);
            drawAhead(lines, type, x, y, size);
        }
        // The sight.
        addLine(lines, BRIDGE_X - 6, BRIDGE_Y, BRIDGE_X - 2, BRIDGE_Y, FRAME);
        addLine(lines, BRIDGE_X + 2, BRIDGE_Y, BRIDGE_X + 6, BRIDGE_Y, FRAME);
    }

    private static void drawAhead(short[] lines, int type, int x, int y, int size) {
        if (type == T_KLINGON) {
            // Head-on: a head above swept wings.
            addLine(lines, x - size, y + size / 2, x, y - size / 3, KLINGON);
            addLine(lines, x, y - size / 3, x + size, y + size / 2, KLINGON);
            addLine(lines, x - size, y + size / 2, x - size, y, KLINGON);
            addLine(lines, x + size, y + size / 2, x + size, y, KLINGON);
            addLine(lines, x, y - size / 3, x, y - size, KLINGON);
        } else if (type == T_STARBASE) {
            for (int k = 0; k < 8; k++) {
                addLine(lines, x + dirX(k) * size / 10, y + dirY(k) * size / 20, x + dirX(k + 1) * size / 10,
                        y + dirY(k + 1) * size / 20, STARBASE);
            }
            addLine(lines, x, y - size, x, y + size, STARBASE);
        } else if (type == T_SAUCER) {
            addLine(lines, x - size, y, x + size, y, SAUCER);
            addLine(lines, x - size / 2, y - size / 2, x + size / 2, y - size / 2, SAUCER);
        } else if (type == T_NOMAD) {
            addLine(lines, x - size, y - size, x + size, y - size, NOMAD);
            addLine(lines, x + size, y - size, x + size, y + size, NOMAD);
            addLine(lines, x + size, y + size, x - size, y + size, NOMAD);
            addLine(lines, x - size, y + size, x - size, y - size, NOMAD);
        } else {
            int color = type == T_PHOTON ? PHOTON : TORPEDO;
            addLine(lines, x - size, y, x + size, y, color);
            addLine(lines, x, y - size, x, y + size, color);
        }
    }
}
