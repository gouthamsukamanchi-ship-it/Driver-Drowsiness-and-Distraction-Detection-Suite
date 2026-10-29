package com.drowsiness.alerts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SirenSoundTest {

    private static final int TONE_SAMPLES = (int) Math.round(SirenSound.SAMPLE_RATE * SirenSound.TONE_SECONDS);

    private final short[] samples = toSamples(SirenSound.generate());

    @Test
    void containsOneHighAndOneLowTone() {
        assertEquals(2 * TONE_SAMPLES, samples.length);
        assertEquals(SirenSound.HIGH_TONE_HZ * SirenSound.TONE_SECONDS, upwardZeroCrossings(0, TONE_SAMPLES), 2);
        assertEquals(SirenSound.LOW_TONE_HZ * SirenSound.TONE_SECONDS,
                upwardZeroCrossings(TONE_SAMPLES, 2 * TONE_SAMPLES), 2);
    }

    @Test
    void isLoudWithoutClipping() {
        int peak = 0;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        int expectedPeak = (int) Math.round(SirenSound.VOLUME * Short.MAX_VALUE);
        assertEquals(expectedPeak, peak, 1);
    }

    @Test
    void loopsWithoutAClick() {
        // The cycle starts at zero and ends just below zero, so repeating it is seamless
        assertEquals(0, samples[0]);
        int lastStep = Math.abs(samples[samples.length - 1] - samples[0]);
        int typicalStep = Math.abs(samples[1] - samples[0]);
        assertTrue(lastStep <= typicalStep * 2, "jump at the loop point: " + lastStep);
    }

    private int upwardZeroCrossings(int from, int to) {
        int crossings = 0;
        for (int n = from + 1; n < to; n++) {
            if (samples[n - 1] < 0 && samples[n] >= 0) {
                crossings++;
            }
        }
        return crossings;
    }

    private static short[] toSamples(byte[] pcm) {
        short[] samples = new short[pcm.length / 2];
        for (int n = 0; n < samples.length; n++) {
            samples[n] = (short) ((pcm[2 * n] & 0xff) | (pcm[2 * n + 1] << 8));
        }
        return samples;
    }
}
