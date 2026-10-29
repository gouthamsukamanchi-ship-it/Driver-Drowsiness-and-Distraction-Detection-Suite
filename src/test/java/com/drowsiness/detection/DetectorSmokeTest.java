package com.drowsiness.detection;

import nu.pattern.OpenCV;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the bundled models load and that the detectors handle edge cases.
 * Needs the OpenCV native library, which is loaded from the Maven dependency.
 */
class DetectorSmokeTest {

    private Mat blankFrame;

    @BeforeAll
    static void loadOpenCv() {
        OpenCV.loadLocally();
    }

    @BeforeEach
    void createFrame() {
        blankFrame = new Mat(480, 640, CvType.CV_8UC3, new Scalar(90, 90, 90));
    }

    @AfterEach
    void releaseFrame() {
        blankFrame.release();
    }

    @Test
    void bundledModelsLoad() {
        assertDoesNotThrow(FaceDetector::new);
        assertDoesNotThrow(EyeDetector::new);
    }

    @Test
    void blankFrameContainsNoFaces() {
        assertTrue(new FaceDetector().detect(blankFrame).isEmpty());
    }

    @Test
    void eyesAreUnknownWithoutLandmarks() {
        assertEquals(EyeState.UNKNOWN, new EyeDetector().detect(blankFrame, null).state());
    }

    @Test
    void eyesAreUnknownWhenFaceIsTooSmall() {
        FaceLandmarks tiny = landmarks(300, 200, 10);
        assertEquals(EyeState.UNKNOWN, new EyeDetector().detect(blankFrame, tiny).state());
    }

    @Test
    void eyesAreUnknownWhenCutOffByFrameEdge() {
        FaceLandmarks atCorner = landmarks(2, 2, 28);
        assertEquals(EyeState.UNKNOWN, new EyeDetector().detect(blankFrame, atCorner).state());
    }

    @Test
    void eyesAreClassifiedWhenFullyVisible() {
        FaceLandmarks centred = landmarks(290, 220, 60);
        EyeResult result = new EyeDetector().detect(blankFrame, centred);
        assertTrue(result.state() == EyeState.OPEN || result.state() == EyeState.CLOSED);
        assertTrue(result.openProbability() >= 0 && result.openProbability() <= 1);
    }

    private static FaceLandmarks landmarks(double x, double y, double eyeDistance) {
        return new FaceLandmarks(
                new Point(x, y),
                new Point(x + eyeDistance, y),
                new Point(x + eyeDistance / 2, y + eyeDistance * 0.6),
                new Point(x + eyeDistance * 0.15, y + eyeDistance * 1.1),
                new Point(x + eyeDistance * 0.85, y + eyeDistance * 1.1));
    }
}
