package com.drowsiness.analysis;

import com.drowsiness.detection.EyeResult;
import com.drowsiness.detection.TrackedFace;

/**
 * Everything known about one person in the current frame.
 *
 * @param rank             1 for the driver, 2, 3, ... for other people in view
 * @param face             the tracked face
 * @param eyes             eye-state classification
 * @param drowsinessScore  0..100, reaches 100 when the person counts as drowsy
 * @param distractionScore 0..100, reaches 100 while the head is turned away
 * @param risk             combined risk level
 */
public record DriverStatus(
        int rank,
        TrackedFace face,
        EyeResult eyes,
        int drowsinessScore,
        int distractionScore,
        RiskLevel risk) {

    /** @return {@code true} for the main driver (rank 1) */
    public boolean isDriver() {
        return rank == 1;
    }
}
