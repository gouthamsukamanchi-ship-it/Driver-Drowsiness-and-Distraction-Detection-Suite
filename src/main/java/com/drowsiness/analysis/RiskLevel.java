package com.drowsiness.analysis;

/**
 * Overall risk level of a person, from least to most severe.
 */
public enum RiskLevel {

    /** Eyes open and looking ahead. */
    SAFE,

    /** Looking away from the road for too long. */
    DISTRACTED,

    /** Eyes closed for too long. */
    DROWSY,

    /** Drowsy and distracted at the same time. */
    CRITICAL;

    /** @return {@code true} for every level that should raise an alert */
    public boolean isAlert() {
        return this != SAFE;
    }
}
