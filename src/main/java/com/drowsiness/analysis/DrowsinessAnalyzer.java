package com.drowsiness.analysis;

import com.drowsiness.detection.EyeState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a person is drowsy from their eye state over time.
 *
 * <p>A person is drowsy when their eyes stay closed for at least
 * {@value #DROWSY_CLOSED_MILLIS} ms. The measure is time based rather than
 * frame based, so it behaves the same at any frame rate. Normal blinks
 * (100-400 ms) never reach the threshold.
 *
 * <p>State is kept separately for every tracked person.
 */
public class DrowsinessAnalyzer {

    /** Eyes closed continuously for this long counts as drowsy. */
    static final long DROWSY_CLOSED_MILLIS = 1500;

    /**
     * Consecutive open-eye frames needed to reset the closed-eye timer, so a
     * single misread frame in the middle of a long closure does not reset it.
     */
    static final int OPEN_RESET_FRAMES = 2;

    /** Upper limit on the time credited per frame, so a stalled frame cannot jump the timer. */
    static final long MAX_FRAME_STEP_MILLIS = 200;

    private final Map<Integer, State> states = new HashMap<>();

    /**
     * Updates one person with the eye state of the current frame.
     *
     * @param personId        track id of the person
     * @param eyeState        eye state in this frame
     * @param timestampMillis time of this frame
     * @return {@code true} if the person is now drowsy
     */
    public boolean update(int personId, EyeState eyeState, long timestampMillis) {
        State state = states.computeIfAbsent(personId, id -> new State());
        long step = state.lastUpdateMillis < 0
                ? 0
                : Math.max(0, Math.min(timestampMillis - state.lastUpdateMillis, MAX_FRAME_STEP_MILLIS));
        state.lastUpdateMillis = timestampMillis;

        switch (eyeState) {
            case OPEN -> {
                state.openFrames++;
                if (state.openFrames >= OPEN_RESET_FRAMES) {
                    state.closedMillis = 0;
                }
            }
            case CLOSED -> {
                state.openFrames = 0;
                state.closedMillis += step;
            }
            case UNKNOWN -> {
                // Face briefly lost or eyes cut off: keep the current state
            }
        }
        return isDrowsy(personId);
    }

    /** @return {@code true} if the person's eyes have been closed long enough */
    public boolean isDrowsy(int personId) {
        return closedMillis(personId) >= DROWSY_CLOSED_MILLIS;
    }

    /** @return how long the person's eyes have currently been closed, in ms */
    public long closedMillis(int personId) {
        State state = states.get(personId);
        return state == null ? 0 : state.closedMillis;
    }

    /** @return 0..100, the closed-eye time as a percentage of the drowsiness threshold */
    public int score(int personId) {
        return (int) Math.min(100, Math.round(closedMillis(personId) * 100.0 / DROWSY_CLOSED_MILLIS));
    }

    /** Discards the state of everyone not in {@code activeIds} (people who left the view). */
    public void retainOnly(Collection<Integer> activeIds) {
        states.keySet().retainAll(activeIds);
    }

    private static final class State {
        long closedMillis;
        int openFrames;
        long lastUpdateMillis = -1;
    }
}
