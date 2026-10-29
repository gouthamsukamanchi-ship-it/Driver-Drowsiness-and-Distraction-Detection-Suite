package com.drowsiness.analysis;

import com.drowsiness.detection.EyeState;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.drowsiness.detection.EyeState.CLOSED;
import static com.drowsiness.detection.EyeState.OPEN;
import static com.drowsiness.detection.EyeState.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrowsinessAnalyzerTest {

    private static final int DRIVER = 1;
    private static final int PASSENGER = 2;

    /** About 30 frames per second. */
    private static final long FRAME_MILLIS = 33;

    private final DrowsinessAnalyzer analyzer = new DrowsinessAnalyzer();

    private long clock = 10_000;

    /** Feeds a number of frames with the same eye state and returns the last result. */
    private boolean feed(int person, EyeState state, int frames) {
        boolean drowsy = false;
        for (int i = 0; i < frames; i++) {
            clock += FRAME_MILLIS;
            drowsy = analyzer.update(person, state, clock);
        }
        return drowsy;
    }

    @Test
    void openEyesAreNeverDrowsy() {
        assertFalse(feed(DRIVER, OPEN, 300));
        assertEquals(0, analyzer.score(DRIVER));
    }

    @Test
    void normalBlinkIsNotDrowsy() {
        feed(DRIVER, OPEN, 30);
        assertFalse(feed(DRIVER, CLOSED, 9)); // ~300 ms blink
        feed(DRIVER, OPEN, 2);
        assertEquals(0, analyzer.closedMillis(DRIVER));
    }

    @Test
    void eyesClosedForThresholdIsDrowsy() {
        feed(DRIVER, OPEN, 30);
        assertFalse(feed(DRIVER, CLOSED, 45), "45 x 33 ms = 1485 ms is below the threshold");
        assertTrue(feed(DRIVER, CLOSED, 1), "46 x 33 ms = 1518 ms reaches the threshold");
        assertEquals(100, analyzer.score(DRIVER));
    }

    @Test
    void scoreGrowsWithClosedTime() {
        feed(DRIVER, OPEN, 1);
        feed(DRIVER, CLOSED, 15); // 495 ms of 1500 ms
        assertEquals(33, analyzer.score(DRIVER));
    }

    @Test
    void singleOpenFrameDoesNotResetClosure() {
        feed(DRIVER, OPEN, 30);
        feed(DRIVER, CLOSED, 30);
        long closed = analyzer.closedMillis(DRIVER);

        feed(DRIVER, OPEN, 1);
        assertEquals(closed, analyzer.closedMillis(DRIVER), "one open frame may be a misread");

        feed(DRIVER, OPEN, 1);
        assertEquals(0, analyzer.closedMillis(DRIVER), "two open frames reset the timer");
    }

    @Test
    void unknownFramesNeitherAddNorReset() {
        feed(DRIVER, OPEN, 5);
        feed(DRIVER, CLOSED, 20);
        long closed = analyzer.closedMillis(DRIVER);

        feed(DRIVER, UNKNOWN, 50);
        assertEquals(closed, analyzer.closedMillis(DRIVER));
    }

    @Test
    void stalledFrameAddsAtMostTheCap() {
        feed(DRIVER, OPEN, 5);
        feed(DRIVER, CLOSED, 1);
        clock += 5_000; // e.g. the window was being dragged
        analyzer.update(DRIVER, CLOSED, clock);
        assertEquals(FRAME_MILLIS + DrowsinessAnalyzer.MAX_FRAME_STEP_MILLIS, analyzer.closedMillis(DRIVER));
    }

    @Test
    void peopleAreAnalysedIndependently() {
        for (int i = 0; i < 60; i++) {
            clock += FRAME_MILLIS;
            analyzer.update(DRIVER, OPEN, clock);
            analyzer.update(PASSENGER, CLOSED, clock);
        }
        assertFalse(analyzer.isDrowsy(DRIVER));
        assertTrue(analyzer.isDrowsy(PASSENGER));
    }

    @Test
    void retainOnlyForgetsPeopleWhoLeft() {
        feed(PASSENGER, OPEN, 1);
        feed(PASSENGER, CLOSED, 60);
        analyzer.retainOnly(List.of(DRIVER));
        assertFalse(analyzer.isDrowsy(PASSENGER));
        assertEquals(0, analyzer.closedMillis(PASSENGER));
    }
}
