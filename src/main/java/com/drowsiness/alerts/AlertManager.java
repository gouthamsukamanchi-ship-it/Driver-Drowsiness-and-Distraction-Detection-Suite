package com.drowsiness.alerts;

import com.drowsiness.analysis.RiskLevel;

import java.awt.HeadlessException;
import java.awt.Toolkit;

/**
 * Sounds an audible alarm while the driver is at risk.
 *
 * <p>A driver whose eyes are closed, or who is looking away, cannot see an
 * on-screen warning, so the sound is what actually reaches them. A continuous
 * two-tone siren plays for every warning level ({@code DISTRACTED},
 * {@code DROWSY}, {@code CRITICAL}) and stops as soon as the driver is safe again.
 */
public class AlertManager implements AutoCloseable {

    /** Beep interval used when no audio output is available for the siren. */
    private static final long FALLBACK_BEEP_INTERVAL_MILLIS = 600;

    private final boolean soundEnabled;

    private final SirenSound siren = new SirenSound();

    private long lastBeepMillis;

    /**
     * @param soundEnabled {@code false} to keep the alarm silent (e.g. during a presentation)
     */
    public AlertManager(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    /**
     * Call once per frame with the driver's current risk level.
     *
     * @param driverRisk risk level of the driver
     */
    public void update(RiskLevel driverRisk) {
        if (!soundEnabled) {
            return;
        }
        if (!driverRisk.isAlert()) {
            siren.stop();
            return;
        }
        if (!siren.start()) {
            beepEvery(FALLBACK_BEEP_INTERVAL_MILLIS);
        }
    }

    /** Stops the siren and releases the audio device. */
    @Override
    public void close() {
        siren.close();
    }

    private void beepEvery(long intervalMillis) {
        long now = System.currentTimeMillis();
        if (now - lastBeepMillis < intervalMillis) {
            return;
        }
        lastBeepMillis = now;
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (HeadlessException ignored) {
            // No audio/display available; the visual warning is still shown
        }
    }
}
