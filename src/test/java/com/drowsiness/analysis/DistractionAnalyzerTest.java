package com.drowsiness.analysis;

import com.drowsiness.analysis.DistractionAnalyzer.HeadPose;
import com.drowsiness.detection.FaceLandmarks;

import org.junit.jupiter.api.Test;
import org.opencv.core.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistractionAnalyzerTest {

    private static final int DRIVER = 1;

    /** About 30 frames per second. */
    private static final long FRAME_MILLIS = 33;

    /** Distance between the eyes of the synthetic face, in pixels. */
    private static final double EYE_DISTANCE = 60;

    private final DistractionAnalyzer analyzer = new DistractionAnalyzer();

    private long clock = 10_000;

    /** A face looking straight at the camera, shifted by (dx, dy). */
    private static FaceLandmarks facingCamera(double dx, double dy) {
        return face(dx, dy, 0, 0);
    }

    /** Head turned sideways: the nose tip moves along the eye line. */
    private static FaceLandmarks headTurned(double noseShift) {
        return face(0, 0, noseShift, 0);
    }

    /** Looking down: the nose tip moves towards the mouth. */
    private static FaceLandmarks lookingDown(double noseDrop) {
        return face(0, 0, 0, noseDrop);
    }

    private static FaceLandmarks face(double dx, double dy, double noseDx, double noseDy) {
        return new FaceLandmarks(
                new Point(100 + dx, 100 + dy),
                new Point(100 + EYE_DISTANCE + dx, 100 + dy),
                new Point(130 + dx + noseDx, 135 + dy + noseDy),
                new Point(110 + dx, 165 + dy),
                new Point(150 + dx, 165 + dy));
    }

    private boolean feed(FaceLandmarks landmarks, int frames) {
        boolean distracted = false;
        for (int i = 0; i < frames; i++) {
            clock += FRAME_MILLIS;
            distracted = analyzer.update(DRIVER, landmarks, clock);
        }
        return distracted;
    }

    private void calibrate() {
        feed(facingCamera(0, 0), DistractionAnalyzer.CALIBRATION_FRAMES + 5);
    }

    @Test
    void lookingAheadIsNotDistracted() {
        calibrate();
        assertFalse(feed(facingCamera(0, 0), 300));
        assertEquals(0, analyzer.score(DRIVER));
    }

    @Test
    void headTurnedForTwoSecondsIsDistracted() {
        calibrate();
        FaceLandmarks turned = headTurned(0.4 * EYE_DISTANCE); // well past the yaw limit
        assertFalse(feed(turned, 60), "60 x 33 ms = 1980 ms is below the threshold");
        assertTrue(feed(turned, 1), "61 x 33 ms = 2013 ms reaches the threshold");
        assertEquals(100, analyzer.score(DRIVER));
    }

    @Test
    void shortGlanceIsIgnored() {
        calibrate();
        FaceLandmarks turned = headTurned(0.4 * EYE_DISTANCE);
        assertFalse(feed(turned, 30)); // one-second glance at a mirror
        assertFalse(feed(facingCamera(0, 0), 1));
        assertFalse(feed(turned, 45), "the timer restarts after looking back");
    }

    @Test
    void lookingDownIsDistracted() {
        calibrate();
        assertTrue(feed(lookingDown(13), 70));
    }

    @Test
    void sittingAwayFromImageCentreIsNotDistracted() {
        // The previous version flagged anyone who was not in the middle of the image
        calibrate();
        assertFalse(feed(facingCamera(600, 40), 300));
    }

    @Test
    void neverDistractedWhileCalibrating() {
        assertFalse(feed(headTurned(0.4 * EYE_DISTANCE), DistractionAnalyzer.CALIBRATION_FRAMES));
    }

    @Test
    void stateIsKeptWhileFaceIsNotVisible() {
        calibrate();
        assertTrue(feed(headTurned(0.4 * EYE_DISTANCE), 70));
        assertTrue(feed(null, 10));
    }

    @Test
    void tiltingTheHeadIsNotTurningIt() {
        FaceLandmarks upright = facingCamera(0, 0);
        FaceLandmarks tilted = rotate(upright, Math.toRadians(25), new Point(130, 130));

        HeadPose a = DistractionAnalyzer.estimateHeadPose(upright);
        HeadPose b = DistractionAnalyzer.estimateHeadPose(tilted);
        assertEquals(a.yaw(), b.yaw(), 1e-9);
        assertEquals(a.pitch(), b.pitch(), 1e-9);
    }

    private static FaceLandmarks rotate(FaceLandmarks f, double angle, Point centre) {
        return new FaceLandmarks(
                rotate(f.rightEye(), angle, centre),
                rotate(f.leftEye(), angle, centre),
                rotate(f.noseTip(), angle, centre),
                rotate(f.rightMouthCorner(), angle, centre),
                rotate(f.leftMouthCorner(), angle, centre));
    }

    private static Point rotate(Point p, double angle, Point centre) {
        double x = p.x - centre.x;
        double y = p.y - centre.y;
        return new Point(
                centre.x + x * Math.cos(angle) - y * Math.sin(angle),
                centre.y + x * Math.sin(angle) + y * Math.cos(angle));
    }
}
