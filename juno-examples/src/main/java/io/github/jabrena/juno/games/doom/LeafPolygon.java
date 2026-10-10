package io.github.jabrena.juno.games.doom;

/**
 * The convex floor area of one BSP subsector, rebuilt on demand for {@link RoutePlanner}. A subsector stores only its
 * segs, but part of its border is the partition lines of the BSP nodes above it, which have no seg: walking out
 * across one of those leads into the neighboring subsector of the same room. So the polygon starts as a box around
 * the map and is cut by every ancestor's partition line (keeping the subsector's side) and then by each of its own
 * segs (a subsector lies to the right of its segs).
 */
final class LeafPolygon {
    static final int MAX_CORNERS = 48;
    private static final float MARGIN = 64f;

    private static float minX;
    private static float minY;
    private static float maxX;
    private static float maxY;

    private LeafPolygon() {
    }

    /** Records each node's and subsector's parent node (-1 for the root), and the map's extent. */
    static void link(short[] nodeParent, short[] leafParent) {
        for (int node = 0; node < World.nodes; node++) {
            nodeParent[node] = -1;
        }
        for (int leaf = 0; leaf < World.subsectors; leaf++) {
            leafParent[leaf] = -1;
        }
        for (int node = 0; node < World.nodes; node++) {
            adopt(nodeParent, leafParent, node, World.nodeRight[node] & 0xFFFF);
            adopt(nodeParent, leafParent, node, World.nodeLeft[node] & 0xFFFF);
        }
        minX = Float.MAX_VALUE;
        minY = Float.MAX_VALUE;
        maxX = -Float.MAX_VALUE;
        maxY = -Float.MAX_VALUE;
        for (int vertex = 0; vertex < World.vertices; vertex++) {
            minX = Math.min(minX, World.vertexX[vertex]);
            minY = Math.min(minY, World.vertexY[vertex]);
            maxX = Math.max(maxX, World.vertexX[vertex]);
            maxY = Math.max(maxY, World.vertexY[vertex]);
        }
    }

    private static void adopt(short[] nodeParent, short[] leafParent, int node, int child) {
        if ((child & 0x8000) != 0) {
            leafParent[child & 0x7FFF] = (short) node;
        } else {
            nodeParent[child] = (short) node;
        }
    }

    /** Writes {@code leaf}'s polygon into {@code x}/{@code y} and returns its corner count (0 if it vanished). */
    static int build(int leaf, short[] nodeParent, short[] leafParent, float[] x, float[] y, float[] spareX,
                     float[] spareY) {
        x[0] = minX - MARGIN;
        y[0] = minY - MARGIN;
        x[1] = maxX + MARGIN;
        y[1] = minY - MARGIN;
        x[2] = maxX + MARGIN;
        y[2] = maxY + MARGIN;
        x[3] = minX - MARGIN;
        y[3] = maxY + MARGIN;
        int count = 4;
        int child = 0x8000 | leaf;
        int node = leafParent[leaf];
        while (node >= 0 && count > 0) {
            // The right child is the partition's front: (px - X) * DY - (py - Y) * DX > 0 (see Player.onBackSide).
            float sign = (World.nodeRight[node] & 0xFFFF) == child ? 1f : -1f;
            float a = sign * World.nodeDy[node];
            float b = -sign * World.nodeDx[node];
            float c = sign * ((float) World.nodeY[node] * World.nodeDx[node] - (float) World.nodeX[node] * World.nodeDy[node]);
            count = clip(x, y, count, a, b, c, spareX, spareY);
            child = node;
            node = nodeParent[node];
        }
        int first = World.subsectorFirst[leaf];
        for (int seg = first; seg < first + World.subsectorCount[leaf] && count > 0; seg++) {
            float ax = World.vertexX[World.segV1[seg]];
            float ay = World.vertexY[World.segV1[seg]];
            float dx = World.vertexX[World.segV2[seg]] - ax;
            float dy = World.vertexY[World.segV2[seg]] - ay;
            count = clip(x, y, count, dy, -dx, ay * dx - ax * dy, spareX, spareY);
        }
        return count;
    }

    /** Sutherland-Hodgman: keeps the part of the polygon where {@code a*x + b*y + c >= 0}. */
    private static int clip(float[] x, float[] y, int count, float a, float b, float c, float[] spareX,
                            float[] spareY) {
        int kept = 0;
        for (int i = 0; i < count; i++) {
            int j = i + 1 == count ? 0 : i + 1;
            float here = a * x[i] + b * y[i] + c;
            float next = a * x[j] + b * y[j] + c;
            if (here >= 0 && kept < MAX_CORNERS) {
                spareX[kept] = x[i];
                spareY[kept] = y[i];
                kept = kept + 1;
            }
            if ((here >= 0) != (next >= 0) && kept < MAX_CORNERS) {
                float t = here / (here - next);
                spareX[kept] = x[i] + t * (x[j] - x[i]);
                spareY[kept] = y[i] + t * (y[j] - y[i]);
                kept = kept + 1;
            }
        }
        for (int i = 0; i < kept; i++) {
            x[i] = spareX[i];
            y[i] = spareY[i];
        }
        return kept;
    }
}
