package io.github.jabrena.juno.robotics.navigation;

/**
 * Mutable, allocation-free planar pose for a differential-drive robot, Cartesian as leJOS's pose and a
 * {@link io.github.jabrena.juno.robotics.DirectionFinder}'s direction are: heading zero points along +x, the heading
 * grows counter-clockwise, and +y is to the left of heading zero. The position is kept in micrometres, so many short
 * moves at a small angle add up their sideways part instead of rounding each one away; it reads in millimetres, and
 * stays exact within about two kilometres of the origin.
 */
public final class Pose {
    private static final int MICROMETRES_PER_MILLIMETRE = 1000;
    private static final int FULL_TURN_MILLIDEGREES = 360_000;
    private static final int HALF_TURN_MILLIDEGREES = 180_000;
    private static final double MILLIDEGREES_PER_RADIAN = 180_000.0 / Math.PI;

    private int xMicrometres;
    private int yMicrometres;
    private int headingMillidegrees;

    /** Creates a pose at the origin facing heading zero. */
    public Pose() {
    }

    /** Creates a pose with the supplied position and heading. */
    public Pose(int xMillimetres, int yMillimetres, int headingMillidegrees) {
        setLocation(xMillimetres, yMillimetres);
        setHeadingMillidegrees(headingMillidegrees);
    }

    /** The x position, in millimetres from the origin, rounded to the nearest. */
    public int xMillimetres() {
        return millimetres(xMicrometres);
    }

    /** The y position, in millimetres from the origin, rounded to the nearest. */
    public int yMillimetres() {
        return millimetres(yMicrometres);
    }

    /** The heading, in millidegrees counter-clockwise, normalised to {@code 0} through {@code 359_999}. */
    public int headingMillidegrees() {
        return headingMillidegrees;
    }

    /** Moves this pose to {@code (xMillimetres, yMillimetres)}, keeping its heading. */
    public void setLocation(int xMillimetres, int yMillimetres) {
        xMicrometres = xMillimetres * MICROMETRES_PER_MILLIMETRE;
        yMicrometres = yMillimetres * MICROMETRES_PER_MILLIMETRE;
    }

    /** Sets the heading, in millidegrees counter-clockwise; any value, normalised to a turn. */
    public void setHeadingMillidegrees(int headingMillidegrees) {
        this.headingMillidegrees = normalize(headingMillidegrees);
    }

    /** Resets this pose to the origin facing heading zero. */
    public void reset() {
        xMicrometres = 0;
        yMicrometres = 0;
        headingMillidegrees = 0;
    }

    /** The straight-line distance to {@code (xMillimetres, yMillimetres)}, in millimetres, rounded to the nearest. */
    public int distanceToMillimetres(int xMillimetres, int yMillimetres) {
        double dx = (double) xMillimetres * MICROMETRES_PER_MILLIMETRE - xMicrometres;
        double dy = (double) yMillimetres * MICROMETRES_PER_MILLIMETRE - yMicrometres;
        return (int) Math.round(Math.hypot(dx, dy) / MICROMETRES_PER_MILLIMETRE);
    }

    /**
     * The direction from this pose's position to {@code (xMillimetres, yMillimetres)}, in millidegrees
     * counter-clockwise from +x, normalised to {@code 0} through {@code 359_999}: leJOS's {@code angleTo}. Zero when the
     * point is this position.
     */
    public int angleToMillidegrees(int xMillimetres, int yMillimetres) {
        double dx = (double) xMillimetres * MICROMETRES_PER_MILLIMETRE - xMicrometres;
        double dy = (double) yMillimetres * MICROMETRES_PER_MILLIMETRE - yMicrometres;
        return normalize((int) Math.round(Math.atan2(dy, dx) * MILLIDEGREES_PER_RADIAN));
    }

    /**
     * The turn from this pose's heading to face {@code (xMillimetres, yMillimetres)}, in millidegrees, positive
     * counter-clockwise, the shorter way: {@code -179_999} through {@code 180_000}. leJOS's {@code relativeBearing}.
     */
    public int relativeBearingMillidegrees(int xMillimetres, int yMillimetres) {
        return shortestTurn(angleToMillidegrees(xMillimetres, yMillimetres) - headingMillidegrees);
    }

    /** {@code turn} millidegrees as the shorter turn the same way round: {@code -179_999} through {@code 180_000}. */
    static int shortestTurn(int turn) {
        int normalized = normalize(turn + HALF_TURN_MILLIDEGREES) - HALF_TURN_MILLIDEGREES;
        return normalized == -HALF_TURN_MILLIDEGREES ? HALF_TURN_MILLIDEGREES : normalized;
    }

    /** Moves {@code distanceMillimetres} along {@code newHeadingMillidegrees}, which becomes the heading. */
    void advance(int distanceMillimetres, int newHeadingMillidegrees) {
        double radians = newHeadingMillidegrees / MILLIDEGREES_PER_RADIAN;
        double micrometres = (double) distanceMillimetres * MICROMETRES_PER_MILLIMETRE;
        xMicrometres += (int) Math.round(micrometres * Math.cos(radians));
        yMicrometres += (int) Math.round(micrometres * Math.sin(radians));
        headingMillidegrees = normalize(newHeadingMillidegrees);
    }

    /** Rounds micrometres to the nearest millimetre, halves away from zero. */
    private static int millimetres(int micrometres) {
        int half = MICROMETRES_PER_MILLIMETRE / 2;
        return micrometres >= 0 ? (micrometres + half) / MICROMETRES_PER_MILLIMETRE
                : -((-micrometres + half) / MICROMETRES_PER_MILLIMETRE);
    }

    private static int normalize(int heading) {
        int normalized = heading % FULL_TURN_MILLIDEGREES;
        return normalized < 0 ? normalized + FULL_TURN_MILLIDEGREES : normalized;
    }
}
