package io.github.jabrena.juno.hardware.drive;

/** Mutable, allocation-free planar pose for a differential-drive robot. */
public final class Pose {
    private int xMillimetres;
    private int yMillimetres;
    private int headingMillidegrees;

    /** Creates a pose at the origin facing heading zero. */
    public Pose() {
    }

    /** Creates a pose with the supplied position and heading. */
    public Pose(int xMillimetres, int yMillimetres, int headingMillidegrees) {
        this.xMillimetres = xMillimetres;
        this.yMillimetres = yMillimetres;
        this.headingMillidegrees = normalizeHeading(headingMillidegrees);
    }

    public int xMillimetres() {
        return xMillimetres;
    }

    public int yMillimetres() {
        return yMillimetres;
    }

    public int headingMillidegrees() {
        return headingMillidegrees;
    }

    /** Resets this pose to the origin facing heading zero. */
    public void reset() {
        xMillimetres = 0;
        yMillimetres = 0;
        headingMillidegrees = 0;
    }

    void advance(int distanceMillimetres, int newHeadingMillidegrees) {
        double radians = newHeadingMillidegrees * Math.PI / 180_000.0;
        xMillimetres += (int) Math.round(distanceMillimetres * Math.cos(radians));
        yMillimetres += (int) Math.round(distanceMillimetres * Math.sin(radians));
        headingMillidegrees = normalizeHeading(newHeadingMillidegrees);
    }

    private static int normalizeHeading(int heading) {
        int normalized = heading % 360_000;
        return normalized < 0 ? normalized + 360_000 : normalized;
    }
}
