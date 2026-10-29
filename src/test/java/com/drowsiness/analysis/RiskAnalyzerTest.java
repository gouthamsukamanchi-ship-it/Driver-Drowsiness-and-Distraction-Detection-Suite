package com.drowsiness.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAnalyzerTest {

    private final RiskAnalyzer analyzer = new RiskAnalyzer();

    @Test
    void combinesDrowsinessAndDistraction() {
        assertEquals(RiskLevel.SAFE, analyzer.assess(false, false));
        assertEquals(RiskLevel.DISTRACTED, analyzer.assess(false, true));
        assertEquals(RiskLevel.DROWSY, analyzer.assess(true, false));
        assertEquals(RiskLevel.CRITICAL, analyzer.assess(true, true));
    }

    @Test
    void onlySafeIsNotAnAlert() {
        assertFalse(RiskLevel.SAFE.isAlert());
        assertTrue(RiskLevel.DISTRACTED.isAlert());
        assertTrue(RiskLevel.DROWSY.isAlert());
        assertTrue(RiskLevel.CRITICAL.isAlert());
    }
}
