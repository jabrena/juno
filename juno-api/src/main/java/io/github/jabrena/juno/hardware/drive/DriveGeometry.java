package io.github.jabrena.juno.hardware.drive;

/**
 * Fixed geometry and encoder ratios for a two-wheel differential drive. Values are expressed in
 * millimetres and encoder degrees; create one instance during setup and reuse it.
 */
public final class DriveGeometry {
    private static final long PI_MILLION = 3_141_593L;

    private final int wheelDiameterMillimetres;
    private final int trackWidthMillimetres;
    private final int leftEncoderDegreesPerWheelRevolution;
    private final int rightEncoderDegreesPerWheelRevolution;

    /**
     * Creates geometry for equal wheel diameter and track width, with independent encoder ratios for
     * the left and right sides.
     */
    public DriveGeometry(
            int wheelDiameterMillimetres,
            int trackWidthMillimetres,
            int leftEncoderDegreesPerWheelRevolution,
            int rightEncoderDegreesPerWheelRevolution) {
        if (wheelDiameterMillimetres <= 0
                || trackWidthMillimetres <= 0
                || leftEncoderDegreesPerWheelRevolution <= 0
                || rightEncoderDegreesPerWheelRevolution <= 0) {
            throw new IllegalArgumentException("drive dimensions and encoder ratios must be positive");
        }
        this.wheelDiameterMillimetres = wheelDiameterMillimetres;
        this.trackWidthMillimetres = trackWidthMillimetres;
        this.leftEncoderDegreesPerWheelRevolution = leftEncoderDegreesPerWheelRevolution;
        this.rightEncoderDegreesPerWheelRevolution = rightEncoderDegreesPerWheelRevolution;
    }

    public int wheelDiameterMillimetres() {
        return wheelDiameterMillimetres;
    }

    public int trackWidthMillimetres() {
        return trackWidthMillimetres;
    }

    public int leftEncoderDegreesPerWheelRevolution() {
        return leftEncoderDegreesPerWheelRevolution;
    }

    public int rightEncoderDegreesPerWheelRevolution() {
        return rightEncoderDegreesPerWheelRevolution;
    }

    int leftDistanceMillimetres(int encoderDegrees) {
        return distanceMillimetres(encoderDegrees, leftEncoderDegreesPerWheelRevolution);
    }

    int rightDistanceMillimetres(int encoderDegrees) {
        return distanceMillimetres(encoderDegrees, rightEncoderDegreesPerWheelRevolution);
    }

    private int distanceMillimetres(int encoderDegrees, int degreesPerRevolution) {
        long numerator = (long) encoderDegrees * wheelDiameterMillimetres * PI_MILLION;
        return (int) (numerator / (1_000_000L * degreesPerRevolution));
    }
}
