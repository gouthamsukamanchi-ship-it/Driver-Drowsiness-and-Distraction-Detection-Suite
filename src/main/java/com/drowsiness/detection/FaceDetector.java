package com.drowsiness.detection;

import com.drowsiness.utils.ModelLoader;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects and tracks faces with the YuNet deep-learning face detector.
 *
 * <p>Detection runs on a down-scaled copy of the frame for speed; results are
 * mapped back to full-frame coordinates. Each detection is matched to the
 * nearest existing track so that every person keeps a stable id while in view.
 * A new track must be seen in several consecutive frames before it is reported,
 * which suppresses one-off false detections.
 *
 * <p>Faces are returned in priority order: the first face is the most likely
 * driver (the largest and most central one).
 */
public class FaceDetector {

    private static final String MODEL_RESOURCE = "/models/face_detection_yunet_2023mar.onnx";

    /** Width of the down-scaled copy the detector runs on, in pixels. */
    private static final int DETECTION_WIDTH = 640;

    /** Minimum detector confidence (0..1). */
    private static final float SCORE_THRESHOLD = 0.80f;

    private static final float NMS_THRESHOLD = 0.30f;

    private static final int TOP_K = 50;

    /** Faces smaller than this (full-frame pixels) are ignored. */
    private static final int MIN_FACE_SIZE = 50;

    /** Number of frames a track survives without a matching detection. */
    private static final int MAX_MISSED_FRAMES = 8;

    /** Consecutive detections needed before a new track is reported. */
    private static final int MIN_HITS_TO_CONFIRM = 3;

    /** Maximum centre distance for matching, as a fraction of the frame diagonal. */
    private static final double MAX_MATCH_DISTANCE = 0.25;

    /** Weight of the newest box when smoothing a track's box. */
    private static final double BOX_SMOOTHING = 0.7;

    /** A perfectly centred face scores like this much extra face area (px²). */
    private static final double CENTRE_BONUS = 20_000;

    private final FaceDetectorYN detector;

    private final List<Track> tracks = new ArrayList<>();

    private Size detectorInputSize = new Size();

    private int nextTrackId = 1;

    /**
     * Loads the YuNet model.
     *
     * @throws IllegalStateException if the bundled model cannot be found
     */
    public FaceDetector() {
        MatOfByte model = ModelLoader.load(MODEL_RESOURCE);
        try {
            detector = FaceDetectorYN.create(
                    "onnx", model, new MatOfByte(),
                    new Size(DETECTION_WIDTH, DETECTION_WIDTH),
                    SCORE_THRESHOLD, NMS_THRESHOLD, TOP_K);
        } finally {
            model.release();
        }
    }

    /**
     * Detects the faces in a frame and updates the tracks.
     *
     * @param frame BGR image
     * @return the confirmed faces, most likely driver first
     */
    public List<TrackedFace> detect(Mat frame) {
        if (frame == null || frame.empty()) {
            tracks.clear();
            return List.of();
        }

        Size frameSize = frame.size();
        updateTracks(runDetector(frame), frameSize);

        List<Track> confirmed = new ArrayList<>();
        for (Track track : tracks) {
            if (track.hits >= MIN_HITS_TO_CONFIRM) {
                confirmed.add(track);
            }
        }
        confirmed.sort(Comparator.comparingDouble(
                (Track track) -> driverPriority(track.box, frameSize)).reversed());

        List<TrackedFace> faces = new ArrayList<>(confirmed.size());
        for (Track track : confirmed) {
            FaceLandmarks landmarks = track.missedFrames == 0 ? track.landmarks : null;
            faces.add(new TrackedFace(track.id, track.box, landmarks));
        }
        return faces;
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    private List<Detection> runDetector(Mat frame) {
        double scale = Math.min(1.0, (double) DETECTION_WIDTH / frame.cols());
        Mat input = frame;
        Mat resized = null;
        Mat results = new Mat();
        try {
            if (scale < 1.0) {
                resized = new Mat();
                Imgproc.resize(frame, resized, new Size(), scale, scale, Imgproc.INTER_AREA);
                input = resized;
            }

            // The detector must be told the exact size of its input
            if (input.cols() != detectorInputSize.width || input.rows() != detectorInputSize.height) {
                detectorInputSize = input.size();
                detector.setInputSize(detectorInputSize);
            }
            detector.detect(input, results);

            // Each row: x, y, w, h, five landmark (x, y) pairs, score
            List<Detection> detections = new ArrayList<>();
            float[] row = new float[15];
            for (int i = 0; i < results.rows(); i++) {
                results.get(i, 0, row);
                Rect box = clampToFrame(row[0] / scale, row[1] / scale,
                        row[2] / scale, row[3] / scale, frame);
                if (box.width < MIN_FACE_SIZE || box.height < MIN_FACE_SIZE) {
                    continue;
                }
                FaceLandmarks landmarks = new FaceLandmarks(
                        point(row, 4, scale),
                        point(row, 6, scale),
                        point(row, 8, scale),
                        point(row, 10, scale),
                        point(row, 12, scale));
                detections.add(new Detection(box, landmarks));
            }
            return detections;
        } finally {
            if (resized != null) {
                resized.release();
            }
            results.release();
        }
    }

    private static Point point(float[] row, int index, double scale) {
        return new Point(row[index] / scale, row[index + 1] / scale);
    }

    private static Rect clampToFrame(double x, double y, double width, double height, Mat frame) {
        int x1 = (int) Math.max(0, Math.round(x));
        int y1 = (int) Math.max(0, Math.round(y));
        int x2 = (int) Math.min(frame.cols(), Math.round(x + width));
        int y2 = (int) Math.min(frame.rows(), Math.round(y + height));
        return new Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    // ------------------------------------------------------------------
    // Tracking
    // ------------------------------------------------------------------

    /**
     * Greedily matches detections to tracks (closest pair first), then ages
     * unmatched tracks and starts new tracks for unmatched detections.
     */
    private void updateTracks(List<Detection> detections, Size frameSize) {
        double diagonal = Math.hypot(frameSize.width, frameSize.height);
        boolean[] trackMatched = new boolean[tracks.size()];
        boolean[] detectionMatched = new boolean[detections.size()];

        while (true) {
            int bestTrack = -1;
            int bestDetection = -1;
            double bestDistance = Double.MAX_VALUE;

            for (int t = 0; t < tracks.size(); t++) {
                if (trackMatched[t]) {
                    continue;
                }
                for (int d = 0; d < detections.size(); d++) {
                    if (detectionMatched[d]) {
                        continue;
                    }
                    double distance = centreDistance(tracks.get(t).box, detections.get(d).box) / diagonal;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestTrack = t;
                        bestDetection = d;
                    }
                }
            }

            if (bestTrack < 0 || bestDistance > MAX_MATCH_DISTANCE) {
                break;
            }
            tracks.get(bestTrack).update(detections.get(bestDetection));
            trackMatched[bestTrack] = true;
            detectionMatched[bestDetection] = true;
        }

        for (int t = 0; t < trackMatched.length; t++) {
            if (!trackMatched[t]) {
                tracks.get(t).missedFrames++;
            }
        }
        for (int d = 0; d < detections.size(); d++) {
            if (!detectionMatched[d]) {
                tracks.add(new Track(nextTrackId++, detections.get(d)));
            }
        }
        tracks.removeIf(Track::isDead);
    }

    private static double centreDistance(Rect a, Rect b) {
        double dx = (a.x + a.width / 2.0) - (b.x + b.width / 2.0);
        double dy = (a.y + a.height / 2.0) - (b.y + b.height / 2.0);
        return Math.hypot(dx, dy);
    }

    /**
     * Ranks faces for "who is the driver": face area dominates, being close to
     * the centre of the image breaks ties between similar-sized faces.
     */
    private static double driverPriority(Rect box, Size frameSize) {
        double dx = (box.x + box.width / 2.0) - frameSize.width / 2.0;
        double dy = (box.y + box.height / 2.0) - frameSize.height / 2.0;
        double diagonal = Math.hypot(frameSize.width, frameSize.height);
        double centreScore = 1.0 - Math.min(Math.hypot(dx, dy) / diagonal, 1.0);
        return box.area() * 0.8 + centreScore * CENTRE_BONUS;
    }

    private static Rect smooth(Rect previous, Rect current) {
        double a = BOX_SMOOTHING;
        return new Rect(
                (int) (previous.x * (1 - a) + current.x * a),
                (int) (previous.y * (1 - a) + current.y * a),
                (int) (previous.width * (1 - a) + current.width * a),
                (int) (previous.height * (1 - a) + current.height * a));
    }

    /** A raw detection from the current frame. */
    private record Detection(Rect box, FaceLandmarks landmarks) {
    }

    /** Mutable tracking state of one person. */
    private static final class Track {

        final int id;
        Rect box;
        FaceLandmarks landmarks;
        int missedFrames;
        int hits = 1;

        Track(int id, Detection detection) {
            this.id = id;
            this.box = detection.box();
            this.landmarks = detection.landmarks();
        }

        void update(Detection detection) {
            box = smooth(box, detection.box());
            // Landmarks are not smoothed: eye crops need the current position
            landmarks = detection.landmarks();
            missedFrames = 0;
            hits++;
        }

        /** Unconfirmed tracks die on their first miss, so flicker never gets confirmed. */
        boolean isDead() {
            return missedFrames > MAX_MISSED_FRAMES
                    || (hits < MIN_HITS_TO_CONFIRM && missedFrames > 0);
        }
    }
}
