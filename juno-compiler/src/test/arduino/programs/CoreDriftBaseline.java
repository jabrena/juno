package demo;

/**
 * The smallest program that every board builds: no peripherals and no allocation, so the arena is discarded by the
 * linker and its static RAM is the core's own footprint plus the runtime it links in. {@code CoreDriftIT} links it on
 * every board and compares that figure with the recorded baseline, which catches an Arduino core upgrade that quietly
 * takes RAM.
 */
public final class CoreDriftBaseline {
    public static void main(String[] args) {
    }
}
