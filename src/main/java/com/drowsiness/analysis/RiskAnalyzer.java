package com.drowsiness.analysis;

/**
 * Combines the drowsiness and distraction results into a single risk level.
 */
public class RiskAnalyzer {

    /**
     * @param drowsy     whether the person is drowsy
     * @param distracted whether the person is distracted
     * @return the combined risk level
     */
    public RiskLevel assess(boolean drowsy, boolean distracted) {
        if (drowsy && distracted) {
            return RiskLevel.CRITICAL;
        }
        if (drowsy) {
            return RiskLevel.DROWSY;
        }
        if (distracted) {
            return RiskLevel.DISTRACTED;
        }
        return RiskLevel.SAFE;
    }
}
