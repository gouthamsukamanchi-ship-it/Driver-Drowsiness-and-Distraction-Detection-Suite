package com.drowsiness.detection;

/**
 * Eye state of one person in one frame.
 */
public enum EyeState {

    /** Eyes are open. */
    OPEN,

    /** Eyes are closed. */
    CLOSED,

    /** Could not be judged: face not detected this frame, or eyes too small / cut off. */
    UNKNOWN
}
