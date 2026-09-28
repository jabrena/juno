package io.github.jabrena.juno.games.spaceparanoids;

/** Tank position, view direction, ray casting and 3D projection. */
final class Camera {
    static final int CENTER_X = 160;
    static final int CENTER_Y = 110;
    private static final float PLANE = 1f;
    static final float FOCAL = 160f / PLANE;
    static final float EYE = 0.35f;
    static final float WALL_HEIGHT = 0.7f;
    static final float NEAR = 0.12f;
    static final float RADIUS = 0.26f;

    static float posX;
    static float posZ;
    static float angle;
    static float dirX;
    static float dirZ;
    static float planeX;
    static float planeZ;
    static float inverse;

    static int rayFace;
    static float rayDistance;
    static float rayAlong;

    private Camera() {
    }

    static void placePlayer(byte[] maze) {
        posX = 1.5f;
        posZ = 1.5f;
        setAngle(maze[Maze.SIZE + 2] == Maze.OPEN ? 0f : (float) (Math.PI / 2));
    }

    static void setAngle(float value) {
        angle = value;
        dirX = (float) Math.cos(value);
        dirZ = (float) Math.sin(value);
        planeX = -dirZ * PLANE;
        planeZ = dirX * PLANE;
        inverse = 1f / (planeX * dirZ - dirX * planeZ);
    }

    static void drive(byte[] maze, float speed) {
        float nx = posX + dirX * speed;
        if (isFree(maze, nx, posZ)) {
            posX = nx;
        }
        float nz = posZ + dirZ * speed;
        if (isFree(maze, posX, nz)) {
            posZ = nz;
        }
    }

    private static boolean isFree(byte[] maze, float x, float z) {
        return !Maze.isWall(maze, x - RADIUS, z - RADIUS) && !Maze.isWall(maze, x + RADIUS, z - RADIUS)
                && !Maze.isWall(maze, x - RADIUS, z + RADIUS) && !Maze.isWall(maze, x + RADIUS, z + RADIUS);
    }

    static void castRay(byte[] maze, int column) {
        float camera = 2f * column / (DisplayList.WIDTH - 1) - 1f;
        float rayX = dirX + planeX * camera;
        float rayZ = dirZ + planeZ * camera;
        int mapX = (int) posX;
        int mapZ = (int) posZ;
        float deltaX = rayX == 0f ? 1e30f : Math.abs(1f / rayX);
        float deltaZ = rayZ == 0f ? 1e30f : Math.abs(1f / rayZ);
        int stepX;
        int stepZ;
        float sideX;
        float sideZ;
        if (rayX < 0f) {
            stepX = -1;
            sideX = (posX - mapX) * deltaX;
        } else {
            stepX = 1;
            sideX = (mapX + 1f - posX) * deltaX;
        }
        if (rayZ < 0f) {
            stepZ = -1;
            sideZ = (posZ - mapZ) * deltaZ;
        } else {
            stepZ = 1;
            sideZ = (mapZ + 1f - posZ) * deltaZ;
        }
        int side = 0;
        for (int guard = 0; guard < 4 * Maze.SIZE; guard++) {
            if (sideX < sideZ) {
                sideX = sideX + deltaX;
                mapX = mapX + stepX;
                side = 0;
            } else {
                sideZ = sideZ + deltaZ;
                mapZ = mapZ + stepZ;
                side = 1;
            }
            if (mapX < 0 || mapZ < 0 || mapX >= Maze.SIZE || mapZ >= Maze.SIZE
                    || maze[mapZ * Maze.SIZE + mapX] != Maze.OPEN) {
                break;
            }
        }
        if (side == 0) {
            rayDistance = sideX - deltaX;
            rayAlong = posZ + rayDistance * rayZ;
            rayFace = ((stepX > 0 ? mapX : mapX + 1) * 2 + (stepX > 0 ? 1 : 0)) * 2;
        } else {
            rayDistance = sideZ - deltaZ;
            rayAlong = posX + rayDistance * rayX;
            rayFace = ((stepZ > 0 ? mapZ : mapZ + 1) * 2 + (stepZ > 0 ? 1 : 0)) * 2 + 1;
        }
        rayDistance = Math.max(rayDistance, 0.05f);
    }

    static void line3(short[] lines, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float ax = x0 - posX;
        float az = z0 - posZ;
        float bx = x1 - posX;
        float bz = z1 - posZ;
        float lat0 = inverse * (dirZ * ax - dirX * az);
        float dep0 = inverse * (-planeZ * ax + planeX * az);
        float lat1 = inverse * (dirZ * bx - dirX * bz);
        float dep1 = inverse * (-planeZ * bx + planeX * bz);
        if (dep0 < NEAR && dep1 < NEAR) {
            return;
        }
        if (dep0 < NEAR) {
            float t = (NEAR - dep0) / (dep1 - dep0);
            lat0 = lat0 + (lat1 - lat0) * t;
            y0 = y0 + (y1 - y0) * t;
            dep0 = NEAR;
        } else if (dep1 < NEAR) {
            float t = (NEAR - dep1) / (dep0 - dep1);
            lat1 = lat1 + (lat0 - lat1) * t;
            y1 = y1 + (y0 - y1) * t;
            dep1 = NEAR;
        }
        DisplayList.addLine(lines, screenX(lat0, dep0), screenY(y0, dep0),
                screenX(lat1, dep1), screenY(y1, dep1), color);
    }

    static int screenX(float lateral, float distance) {
        return clamp(CENTER_X + (int) (CENTER_X * lateral / distance), -20000, 20000);
    }

    static int screenY(float y, float distance) {
        return clamp(CENTER_Y - (int) (FOCAL * (y - EYE) / distance), -20000, 20000);
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
