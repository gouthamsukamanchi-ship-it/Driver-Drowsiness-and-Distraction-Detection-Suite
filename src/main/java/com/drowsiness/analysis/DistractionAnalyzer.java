package com.drowsiness.analysis;

import com.drowsiness.detection.FaceLandmarks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a person is distracted from their head pose over time.
 *
 * <p>Head yaw (turning left/right) and pitch (looking up/down) are estimated
 * from the five face landmarks and compared with the person's own normal pose,
 * which is learned during the first frames and slowly adapted afterwards. This
 * makes the result independent of where the person sits in the image and of
 * how the camera is mounted.
 *
 * <p>A person is distracted when their head stays turned away for at least
 * {@value #DISTRACTED_MILLIS} ms; short glances (mirrors, dashboard) are ignored.
 */
public class DistractionAnalyzer {

    /**
     * Allowed yaw deviation. Yaw is the sideways offset of the nose tip from the
     * midpoint of the eyes, divided by the eye distance; 0.25 is roughly a 30-35
     * degree head turn.
     */
    static final double YAW_LIMIT = 0.25;

    /**
     * Allowed pitch deviation. Pitch is how far down the nose tip sits between the
     * eye line (0) and the mouth (1); it changes by about 0.12 when looking roughly
     * 30 degrees down or 25 degrees up.
     */
    static final double PITCH_LIMIT = 0.12;

    /** Looking away continuously for this long counts as distracted. */
    static final long DISTRACTED_MILLIS = 2000;

    /** Number of first frames used to learn a person's normal head pose. */
    static final int CALIBRATION_FRAMES = 30;

    private static final double CALIBRATION_RATE = 0.2;

    /** Rate at which the normal pose keeps adapting while looking ahead. */
    private static final double ADAPT_RATE = 0.01;

    private static final long MAX_FRAME_STEP_MILLIS = 200;

    private final Map<Integer, State> states = new HashMap<>();

    /**
     * Updates one person with the landmarks of the current frame.
     *
     * @param personId        track id of the person
     * @param landmarks       face landmarks, or {@code null} if the face was not detected this frame
     * @param timestampMillis time of this frame
     * @return {@code true} if the person is now distracted
     */
    public boolean update(int personId, FaceLandmarks landmarks, long timestampMillis) {
        State state = states.computeIfAbsent(personId, id -> new State());
        long step = state.lastUpdateMillis < 0
                ? 0
                : Math.max(0, Math.min(timestampMillis - state.lastUpdateMillis, MAX_FRAME_STEP_MILLIS));
        state.lastUpdateMillis = timestampMillis;

        HeadPose pose = landmarks == null ? null : estimateHeadPose(landmarks);
        if (pose == null) {
            // Face not visible this frame: keep the current state
            return isDistracted(personId);
        }

        if (state.calibrationFrames == 0) {
            state.normalYaw = pose.yaw();
            state.normalPitch = pose.pitch();
        }
        if (state.calibrationFrames < CALIBRATION_FRAMES) {
            state.calibrationFrames++;
            state.adapt(pose, CALIBRATION_RATE);
            state.score = 0;
            return false;
        }

        double deviation = Math.max(
                Math.abs(pose.yaw() - state.normalYaw) / YAW_LIMIT,
                Math.abs(pose.pitch() - state.normalPitch) / PITCH_LIMIT);
        state.score = (int) Math.min(100, Math.round(deviation * 100));

        if (deviation >= 1.0) {
            state.awayMillis += step;
        } else {
            state.awayMillis = 0;
            state.adapt(pose, ADAPT_RATE);
        }
        return isDistracted(personId);
    }

    /** @return {@code true} if the person has been looking away long enough */
    public boolean isDistracted(int personId) {
        State state = states.get(personId);
        return state != null && state.awayMillis >= DISTRACTED_MILLIS;
    }

    /** @return 0..100, how far the head is turned relative to the allowed limit */
    public int score(int personId) {
        State state = states.get(personId);
        return state == null ? 0 : state.score;
    }

    /** Discards the state of everyone not in {@code activeIds} (people who left the view). */
    public void retainOnly(Collection<Integer> activeIds) {
        states.keySet().retainAll(activeIds);
    }

    /**
     * Estimates head yaw and pitch from the landmarks. Both are measured along
     * the eye line, so tilting the head sideways (roll) does not count as turning.
     *
     * @return the pose, or {@code null} if the landmarks are degenerate
     */
    static HeadPose estimateHeadPose(FaceLandmarks landmarks) {
        double eyeDistance = landmarks.eyeDistance();
        if (eyeDistance < 1) {
            return null;
        }

        // Unit vector along the eye line (u) and perpendicular to it, pointing down (v)
        double ux = (landmarks.leftEye().x - landmarks.rightEye().x) / eyeDistance;
        double uy = (landmarks.leftEye().y - landmarks.rightEye().y) / eyeDistance;
        double vx = -uy;
        double vy = ux;

        double eyeMidX = (landmarks.rightEye().x + landmarks.leftEye().x) / 2;
        double eyeMidY = (landmarks.rightEye().y + landmarks.leftEye().y) / 2;
        double mouthMidX = (landmarks.rightMouthCorner().x + landmarks.leftMouthCorner().x) / 2;
        double mouthMidY = (landmarks.rightMouthCorner().y + landmarks.leftMouthCorner().y) / 2;

        double noseX = landmarks.noseTip().x - eyeMidX;
        double noseY = landmarks.noseTip().y - eyeMidY;
        double noseAlong = noseX * ux + noseY * uy;
        double noseDown = noseX * vx + noseY * vy;
        double mouthDown = (mouthMidX - eyeMidX) * vx + (mouthMidY - eyeMidY) * vy;
        if (mouthDown < 1) {
            return null;
        }
        return new HeadPose(noseAlong / eyeDistance, noseDown / mouthDown);
    }

    /** Head pose measures, see {@link #YAW_LIMIT} and {@link #PITCH_LIMIT}. */
    record HeadPose(double yaw, double pitch) {
    }

    private static final class State {
        double normalYaw;
        double normalPitch;
        int calibrationFrames;
        long awayMillis;
        long lastUpdateMillis = -1;
        int score;

        void adapt(HeadPose pose, double rate) {
            normalYaw += rate * (pose.yaw() - normalYaw);
            normalPitch += rate * (pose.pitch() - normalPitch);
        }
    }
}
