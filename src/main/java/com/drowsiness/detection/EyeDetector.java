package com.drowsiness.detection;

import com.drowsiness.utils.ModelLoader;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

/**
 * Classifies whether a person's eyes are open or closed.
 *
 * <p>Uses the {@code open-closed-eye-0001} CNN from the Intel Open Model Zoo,
 * which classifies a 32x32 eye crop. The crops are placed on the eye centres
 * reported by the face detector. (Haar eye cascades cannot do this job: they
 * find closed eyes just as readily as open ones.)
 */
public class EyeDetector {

    private static final String MODEL_RESOURCE = "/models/open_closed_eye.onnx";

    private static final Size MODEL_INPUT_SIZE = new Size(32, 32);

    private static final Scalar MODEL_MEAN = new Scalar(127, 127, 127);

    /** Side of the square eye crop, as a fraction of the distance between the eyes. */
    private static final double EYE_CROP_SCALE = 0.65;

    /** Below this distance between the eyes (pixels) the eyes are too small to judge. */
    private static final double MIN_EYE_DISTANCE = 20;

    /** Average open probability at or above which the eyes count as open. */
    private static final double OPEN_THRESHOLD = 0.5;

    private final Net eyeNet;

    /**
     * Loads the eye-state model.
     *
     * @throws IllegalStateException if the bundled model cannot be found
     */
    public EyeDetector() {
        MatOfByte model = ModelLoader.load(MODEL_RESOURCE);
        try {
            eyeNet = Dnn.readNetFromONNX(model);
        } finally {
            model.release();
        }
    }

    /**
     * Classifies the eyes of one face.
     *
     * @param frame     BGR image
     * @param landmarks the face's landmarks, or {@code null} if it was not detected this frame
     * @return the eye state; {@link EyeResult#UNKNOWN} if it cannot be judged
     */
    public EyeResult detect(Mat frame, FaceLandmarks landmarks) {
        if (frame == null || frame.empty() || landmarks == null) {
            return EyeResult.UNKNOWN;
        }
        double eyeDistance = landmarks.eyeDistance();
        if (eyeDistance < MIN_EYE_DISTANCE) {
            return EyeResult.UNKNOWN;
        }

        int side = (int) Math.round(eyeDistance * EYE_CROP_SCALE);
        Rect rightEye = eyeBox(landmarks.rightEye(), side, frame);
        Rect leftEye = eyeBox(landmarks.leftEye(), side, frame);

        double sum = 0;
        int count = 0;
        for (Rect eye : new Rect[] {rightEye, leftEye}) {
            if (eye != null) {
                sum += openProbability(frame, eye);
                count++;
            }
        }
        if (count == 0) {
            return EyeResult.UNKNOWN;
        }

        double probability = sum / count;
        EyeState state = probability >= OPEN_THRESHOLD ? EyeState.OPEN : EyeState.CLOSED;
        return new EyeResult(rightEye, leftEye, probability, state);
    }

    /**
     * Square crop centred on an eye, or {@code null} if it would be cut off by
     * the frame edge (a partial crop gives unreliable results).
     */
    private static Rect eyeBox(Point centre, int side, Mat frame) {
        int x = (int) Math.round(centre.x - side / 2.0);
        int y = (int) Math.round(centre.y - side / 2.0);
        if (x < 0 || y < 0 || x + side > frame.cols() || y + side > frame.rows()) {
            return null;
        }
        return new Rect(x, y, side, side);
    }

    /** Runs the CNN on one eye crop. The network outputs [P(closed), P(open)]. */
    private double openProbability(Mat frame, Rect eye) {
        Mat crop = frame.submat(eye);
        Mat blob = Dnn.blobFromImage(crop, 1.0 / 255.0, MODEL_INPUT_SIZE, MODEL_MEAN, false, false);
        Mat output = null;
        try {
            eyeNet.setInput(blob);
            output = eyeNet.forward();
            float[] probabilities = new float[2];
            output.reshape(1, 1).get(0, 0, probabilities);
            return probabilities[1];
        } finally {
            if (output != null) {
                output.release();
            }
            blob.release();
            crop.release();
        }
    }
}
