package com.drowsiness;

import com.drowsiness.alerts.AlertManager;
import com.drowsiness.analysis.DistractionAnalyzer;
import com.drowsiness.analysis.DriverStatus;
import com.drowsiness.analysis.DrowsinessAnalyzer;
import com.drowsiness.analysis.RiskAnalyzer;
import com.drowsiness.analysis.RiskLevel;
import com.drowsiness.detection.EyeDetector;
import com.drowsiness.detection.EyeResult;
import com.drowsiness.detection.FaceDetector;
import com.drowsiness.detection.TrackedFace;
import com.drowsiness.ui.OverlayRenderer;
import com.drowsiness.utils.FPSCounter;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-frame processing pipeline.
 *
 * <pre>
 *   frame → FaceDetector → EyeDetector → DrowsinessAnalyzer  ─┐
 *                │                                           ├→ RiskAnalyzer → overlay + alarm
 *                └──────────────────────→ DistractionAnalyzer ┘
 * </pre>
 *
 * <p>Every person in view is analysed and shown. The first-ranked person
 * (largest, most central face) is treated as the driver: only the driver's state
 * drives the status panel and the alarm, so passengers or people in the
 * background cannot trigger it.
 */
public class DriverMonitor implements AutoCloseable {

    private final FaceDetector faceDetector = new FaceDetector();

    private final EyeDetector eyeDetector = new EyeDetector();

    private final DrowsinessAnalyzer drowsinessAnalyzer = new DrowsinessAnalyzer();

    private final DistractionAnalyzer distractionAnalyzer = new DistractionAnalyzer();

    private final RiskAnalyzer riskAnalyzer = new RiskAnalyzer();

    private final OverlayRenderer renderer = new OverlayRenderer();

    private final FPSCounter fpsCounter = new FPSCounter();

    private final AlertManager alertManager;

    /**
     * Loads the models. OpenCV must already be loaded.
     *
     * @param soundEnabled whether the audible alarm is enabled
     */
    public DriverMonitor(boolean soundEnabled) {
        this.alertManager = new AlertManager(soundEnabled);
    }

    /**
     * Analyses one frame, draws the results onto it and updates the alarm.
     *
     * @param frame           BGR frame; overlays are drawn onto it
     * @param timestampMillis time the frame was captured
     * @return the status of every person in view, driver first
     */
    public List<DriverStatus> processFrame(Mat frame, long timestampMillis) {
        List<TrackedFace> faces = faceDetector.detect(frame);
        List<DriverStatus> people = new ArrayList<>(faces.size());
        List<Integer> activeIds = new ArrayList<>(faces.size());

        for (int i = 0; i < faces.size(); i++) {
            TrackedFace face = faces.get(i);
            int id = face.trackId();
            activeIds.add(id);

            EyeResult eyes = eyeDetector.detect(frame, face.landmarks());
            boolean drowsy = drowsinessAnalyzer.update(id, eyes.state(), timestampMillis);
            boolean distracted = distractionAnalyzer.update(id, face.landmarks(), timestampMillis);

            people.add(new DriverStatus(
                    i + 1,
                    face,
                    eyes,
                    drowsinessAnalyzer.score(id),
                    distractionAnalyzer.score(id),
                    riskAnalyzer.assess(drowsy, distracted)));
        }
        drowsinessAnalyzer.retainOnly(activeIds);
        distractionAnalyzer.retainOnly(activeIds);

        DriverStatus driver = people.isEmpty() ? null : people.get(0);

        // Draw the driver last so their label is on top
        for (int i = people.size() - 1; i >= 0; i--) {
            renderer.drawPerson(frame, people.get(i));
        }
        renderer.drawDriverPanel(frame, driver, people.size());
        fpsCounter.update();
        renderer.drawFooter(frame, fpsCounter.getFps());

        alertManager.update(driver == null ? RiskLevel.SAFE : driver.risk());
        return people;
    }

    /** Stops any alarm sound and releases the audio device. */
    @Override
    public void close() {
        alertManager.close();
    }
}
