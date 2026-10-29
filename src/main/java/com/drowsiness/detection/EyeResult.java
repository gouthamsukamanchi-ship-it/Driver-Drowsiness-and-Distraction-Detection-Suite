package com.drowsiness.detection;

import org.opencv.core.Rect;

/**
 * Result of the eye-state classification for one face.
 *
 * @param rightEyeBox     crop used for the right eye, or {@code null} if not classified
 * @param leftEyeBox      crop used for the left eye, or {@code null} if not classified
 * @param openProbability average probability (0..1) that the eyes are open,
 *                        or {@link Double#NaN} when unknown
 * @param state           resulting eye state
 */
public record EyeResult(Rect rightEyeBox, Rect leftEyeBox, double openProbability, EyeState state) {

    /** Result used when the eyes cannot be judged. */
    public static final EyeResult UNKNOWN = new EyeResult(null, null, Double.NaN, EyeState.UNKNOWN);
}
