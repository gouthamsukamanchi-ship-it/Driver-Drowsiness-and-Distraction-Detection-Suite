package com.drowsiness.utils;

/**
 * Measures the processing frame rate, averaged over one-second windows.
 */
public class FPSCounter {

    private static final long WINDOW_MILLIS = 1000;

    private long windowStart = System.currentTimeMillis();
    private int framesInWindow;
    private double fps;

    /** Records that one frame has been processed. */
    public void update() {
        framesInWindow++;
        long now = System.currentTimeMillis();
        long elapsed = now - windowStart;
        if (elapsed >= WINDOW_MILLIS) {
            fps = framesInWindow * 1000.0 / elapsed;
            framesInWindow = 0;
            windowStart = now;
        }
    }

    /** @return frames per second measured over the last full window */
    public double getFps() {
        return fps;
    }
}
