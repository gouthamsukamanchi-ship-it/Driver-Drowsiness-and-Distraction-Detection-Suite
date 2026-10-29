package com.drowsiness.detection;

import org.opencv.core.Point;

/**
 * The five facial landmarks produced by the YuNet face detector, in frame
 * coordinates.
 *
 * <p>"Right" and "left" are the person's own right and left, so the right eye
 * appears on the left-hand side of the image.
 *
 * @param rightEye         centre of the right eye
 * @param leftEye          centre of the left eye
 * @param noseTip          tip of the nose
 * @param rightMouthCorner right corner of the mouth
 * @param leftMouthCorner  left corner of the mouth
 */
public record FaceLandmarks(
        Point rightEye,
        Point leftEye,
        Point noseTip,
        Point rightMouthCorner,
        Point leftMouthCorner) {

    /** @return distance between the two eye centres, in pixels */
    public double eyeDistance() {
        return Math.hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y);
    }
}
