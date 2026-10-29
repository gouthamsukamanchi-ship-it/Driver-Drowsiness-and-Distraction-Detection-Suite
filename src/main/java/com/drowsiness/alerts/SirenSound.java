package com.drowsiness.alerts;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/**
 * A loud two-tone (high / low) siren used as the driver alarm.
 *
 * <p>The sound is generated in code, so no audio files are needed. It loops on
 * the audio system's own thread, so starting and stopping it never blocks the
 * video processing.
 */
final class SirenSound implements AutoCloseable {

    static final int SAMPLE_RATE = 44_100;

    /** Frequency of the high tone, in Hz. */
    static final double HIGH_TONE_HZ = 1600;

    /** Frequency of the low tone, in Hz. */
    static final double LOW_TONE_HZ = 1000;

    /** Duration of each tone, in seconds. */
    static final double TONE_SECONDS = 0.25;

    /** Peak level as a fraction of full scale. */
    static final double VOLUME = 0.9;

    private Clip clip;

    private boolean unavailable;

    /**
     * Starts looping the siren; does nothing if it is already playing.
     *
     * @return {@code false} if no audio output is available
     */
    boolean start() {
        Clip siren = clip();
        if (siren == null) {
            return false;
        }
        if (!siren.isRunning()) {
            siren.setFramePosition(0);
            siren.loop(Clip.LOOP_CONTINUOUSLY);
        }
        return true;
    }

    /** Stops the siren if it is playing. */
    void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }

    @Override
    public void close() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    /** Opens the audio clip on first use. */
    private Clip clip() {
        if (clip == null && !unavailable) {
            try {
                byte[] pcm = generate();
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                Clip opened = AudioSystem.getClip();
                opened.open(format, pcm, 0, pcm.length);
                clip = opened;
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                unavailable = true;
                System.err.println("Alarm sound unavailable (" + e.getMessage() + "), using the system beep instead.");
            }
        }
        return clip;
    }

    /**
     * Generates one high-low cycle of the siren as 16-bit signed little-endian
     * mono PCM. Each tone holds a whole number of wave cycles, so the cycle
     * loops seamlessly without clicks.
     *
     * @return the PCM samples
     */
    static byte[] generate() {
        int toneSamples = (int) Math.round(SAMPLE_RATE * TONE_SECONDS);
        double[] wave = new double[2 * toneSamples];
        double peak = 0;

        int i = 0;
        for (double frequency : new double[] {HIGH_TONE_HZ, LOW_TONE_HZ}) {
            for (int n = 0; n < toneSamples; n++) {
                double phase = 2 * Math.PI * frequency * n / SAMPLE_RATE;
                // Fundamental plus odd harmonics: a harsh, buzzer-like tone that cuts through noise
                wave[i] = Math.sin(phase) + Math.sin(3 * phase) / 3 + Math.sin(5 * phase) / 5;
                peak = Math.max(peak, Math.abs(wave[i]));
                i++;
            }
        }

        byte[] pcm = new byte[wave.length * 2];
        for (int n = 0; n < wave.length; n++) {
            short sample = (short) Math.round(wave[n] / peak * VOLUME * Short.MAX_VALUE);
            pcm[2 * n] = (byte) sample;
            pcm[2 * n + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }
}
