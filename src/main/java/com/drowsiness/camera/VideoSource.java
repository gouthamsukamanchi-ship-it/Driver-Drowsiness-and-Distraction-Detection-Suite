package com.drowsiness.camera;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.io.File;

/**
 * Supplies frames from a webcam or from a video file.
 *
 * <p>For a camera, frame timestamps are wall-clock time. For a video file they
 * are derived from the file's frame rate, so a recording is analysed exactly as
 * if it were live, regardless of how fast the computer processes it.
 */
public final class VideoSource implements AutoCloseable {

    /** Requested camera resolution; the camera picks the closest size it supports. */
    private static final int CAMERA_WIDTH = 1280;

    private static final int CAMERA_HEIGHT = 720;

    private static final int STARTUP_READ_ATTEMPTS = 5;

    private static final double DEFAULT_FILE_FPS = 30;

    private final VideoCapture capture;

    private final boolean file;

    private final double frameIntervalMillis;

    private final String description;

    private long framesRead;

    private VideoSource(VideoCapture capture, boolean file, double fps, String description) {
        this.capture = capture;
        this.file = file;
        this.frameIntervalMillis = 1000.0 / fps;
        this.description = description;
    }

    /**
     * Opens a camera and checks that it actually delivers frames.
     *
     * @param index camera index (0 = default camera)
     * @return the opened source
     * @throws IllegalStateException if the camera cannot be opened or read
     */
    public static VideoSource openCamera(int index) {
        VideoCapture capture = new VideoCapture(index);
        if (!capture.isOpened()) {
            capture.release();
            throw new IllegalStateException("Could not open camera " + index + ".");
        }
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, CAMERA_WIDTH);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, CAMERA_HEIGHT);

        // On macOS a camera can report "opened" while every read fails,
        // e.g. when camera permission has been denied.
        if (!canRead(capture)) {
            capture.release();
            throw new IllegalStateException("Camera " + index + " opened but returned no frames. "
                    + "On macOS, allow camera access for the app you run this from "
                    + "(Terminal / VS Code) in System Settings > Privacy & Security > Camera, "
                    + "then restart that app.");
        }

        String description = String.format("camera %d (%dx%d)", index,
                (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH),
                (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));
        return new VideoSource(capture, false, DEFAULT_FILE_FPS, description);
    }

    /**
     * Opens a video file.
     *
     * @param path path to the video file
     * @return the opened source
     * @throws IllegalArgumentException if the file does not exist
     * @throws IllegalStateException    if the file cannot be decoded
     */
    public static VideoSource openFile(String path) {
        if (!new File(path).isFile()) {
            throw new IllegalArgumentException("Video file not found: " + path);
        }
        VideoCapture capture = new VideoCapture(path);
        if (!capture.isOpened()) {
            capture.release();
            throw new IllegalStateException("Could not open video file: " + path);
        }
        double fps = capture.get(Videoio.CAP_PROP_FPS);
        if (!(fps > 0 && fps <= 240)) {
            fps = DEFAULT_FILE_FPS;
        }
        String description = String.format("video %s (%dx%d, %.1f FPS)", path,
                (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH),
                (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT), fps);
        return new VideoSource(capture, true, fps, description);
    }

    private static boolean canRead(VideoCapture capture) {
        Mat frame = new Mat();
        try {
            for (int i = 0; i < STARTUP_READ_ATTEMPTS; i++) {
                if (capture.read(frame) && !frame.empty()) {
                    return true;
                }
            }
            return false;
        } finally {
            frame.release();
        }
    }

    /**
     * Reads the next frame. The caller owns the returned Mat and must release it.
     *
     * @return the frame, or an empty Mat if no frame could be read
     */
    public Mat read() {
        Mat frame = new Mat();
        if (capture.read(frame) && !frame.empty()) {
            framesRead++;
        }
        return frame;
    }

    /** @return timestamp of the frame returned by the last {@link #read()}, in ms */
    public long timestampMillis() {
        return file ? Math.round(framesRead * frameIntervalMillis) : System.currentTimeMillis();
    }

    /** @return time between frames when playing a file at normal speed, 0 for a camera */
    public long frameIntervalMillis() {
        return file ? Math.round(frameIntervalMillis) : 0;
    }

    /** @return {@code true} if this source is a video file (it can end) */
    public boolean isFile() {
        return file;
    }

    /** @return human-readable description, e.g. {@code camera 0 (1280x720)} */
    public String description() {
        return description;
    }

    @Override
    public void close() {
        capture.release();
    }
}
