package io.github.jabrena.juno.robotics.navigation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pose: Cartesian geometry")
class PoseTest {

    @Test
    @DisplayName("A move along heading 90 goes to +y, to the left of heading zero")
    void shouldMoveCounterClockwiseHeadingsToTheLeft() {
        // Given
        Pose pose = new Pose();

        // When
        pose.advance(100, 90_000);

        // Then
        assertThat(pose.xMillimetres()).isZero();
        assertThat(pose.yMillimetres()).isEqualTo(100);
    }

    @Test
    @DisplayName("Distance and direction to a point")
    void shouldMeasureDistanceAndAngleToAPoint() {
        // Given
        Pose pose = new Pose(100, 100, 0);

        // When & Then
        assertThat(pose.distanceToMillimetres(400, 500)).isEqualTo(500);
        assertThat(pose.angleToMillidegrees(100, 0)).isEqualTo(270_000);
        assertThat(pose.angleToMillidegrees(0, 200)).isEqualTo(135_000);
    }

    @Test
    @DisplayName("The relative bearing is the shorter turn to face the point")
    void shouldTurnTheShorterWayToFaceAPoint() {
        // Given: facing 350 degrees
        Pose pose = new Pose(0, 0, 350_000);

        // When & Then: a point at 10 degrees is 20 counter-clockwise, one at 300 degrees 50 clockwise
        assertThat(pose.relativeBearingMillidegrees(1000, 176)).isBetween(19_900, 20_100);
        assertThat(pose.relativeBearingMillidegrees(500, -866)).isBetween(-50_100, -49_900);
    }

    @Test
    @DisplayName("The shortest turn stays within half a turn either way")
    void shouldNormalizeTurnsToHalfATurn() {
        // When & Then
        assertThat(Pose.shortestTurn(340_000)).isEqualTo(-20_000);
        assertThat(Pose.shortestTurn(-200_000)).isEqualTo(160_000);
        assertThat(Pose.shortestTurn(-180_000)).isEqualTo(180_000);
    }

    @Test
    @DisplayName("Setting the heading normalizes it to a turn")
    void shouldNormalizeTheHeadingItIsGiven() {
        // Given
        Pose pose = new Pose();

        // When
        pose.setHeadingMillidegrees(-90_000);

        // Then
        assertThat(pose.headingMillidegrees()).isEqualTo(270_000);
    }
}
