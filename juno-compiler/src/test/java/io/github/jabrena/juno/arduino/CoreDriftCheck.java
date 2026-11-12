package io.github.jabrena.juno.arduino;

/**
 * Decides whether a board's static RAM for the baseline program has grown beyond what its recorded margin allows. The
 * decision is kept apart from the container run so that its rules are unit-tested without Docker.
 */
final class CoreDriftCheck {

    /** What was measured on the board's core in the container: the installed version and the static RAM. */
    record Measurement(String installedVersion, int staticRamBytes) {
    }

    /** The outcome and a message that names the board, the figures, and both core versions. */
    record Verdict(boolean passes, String message) {
    }

    private CoreDriftCheck() {
    }

    static Verdict evaluate(CoreBaseline baseline, Measurement measured) {
        int growth = measured.staticRamBytes() - baseline.baselineBytes();
        String recorded = baseline.platform() + "@" + baseline.version();
        String installed = baseline.platform() + "@" + measured.installedVersion();
        if (growth <= baseline.marginBytes()) {
            return new Verdict(true, baseline.board().id() + ": static RAM " + measured.staticRamBytes()
                    + " bytes, " + (growth >= 0 ? "+" + growth : growth) + " against the baseline of "
                    + baseline.baselineBytes() + " bytes (recorded with " + recorded + ", installed " + installed
                    + "), within the " + baseline.marginBytes() + "-byte margin");
        }
        return new Verdict(false, baseline.board().id() + ": the Arduino core grew the static RAM of the baseline"
                + " program from " + baseline.baselineBytes() + " to " + measured.staticRamBytes() + " bytes (+"
                + growth + "), beyond the " + baseline.marginBytes() + "-byte margin. The baseline was recorded with "
                + recorded + " and the container installs " + installed + ". Every program on this board loses that"
                + " RAM. If the growth is expected, re-measure and update core-drift-baselines.txt.");
    }
}
