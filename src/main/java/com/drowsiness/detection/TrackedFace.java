package com.drowsiness.detection;

import org.opencv.core.Rect;

/**
 * A face that is followed from frame to frame.
 *
 * @param trackId   identifier that stays the same while the person remains in view
 * @param box       smoothed bounding box in frame coordinates
 * @param landmarks landmarks from the current frame, or {@code null} when the face
 *                  was not detected in this frame (tracks survive short gaps)
 */
public record TrackedFace(int trackId, Rect box, FaceLandmarks landmarks) {

    /** @return {@code true} if the face was detected in the current frame */
    public boolean isVisible() {
        return landmarks != null;
    }
}
