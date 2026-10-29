package com.drowsiness;

import com.drowsiness.camera.VideoSource;

import nu.pattern.OpenCV;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Command-line entry point of the Driver Drowsiness &amp; Distraction Detection Suite.
 *
 * <p>Opens the camera (or a video file), runs every frame through the
 * {@link DriverMonitor} and shows the annotated result in a window.
 */
public final class Main {

    private static final String WINDOW_TITLE = "Driver Drowsiness & Distraction Detection";

    /** Consecutive failed camera reads before giving up (camera unplugged, in use, ...). */
    private static final int MAX_FAILED_READS = 30;

    private static final Path SNAPSHOT_DIR = Path.of("data", "output");

    private static final DateTimeFormatter SNAPSHOT_NAME =
            DateTimeFormatter.ofPattern("'snapshot_'yyyyMMdd_HHmmss_SSS'.png'");

    /** Scale applied to frames so the window fits on the screen (computed once). */
    private static double displayScale = -1;

    private Main() {
    }

    /**
     * Program entry point.
     *
     * @param args command-line options, see {@link #printUsage()}
     */
    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printUsage();
            System.exit(2);
            return;
        }
        if (options.help()) {
            printUsage();
            return;
        }

        printBanner();
        int exitCode = 0;
        try {
            loadOpenCv();
            try (DriverMonitor monitor = new DriverMonitor(!options.mute());
                 VideoSource source = options.videoFile() != null
                         ? VideoSource.openFile(options.videoFile())
                         : VideoSource.openCamera(options.cameraIndex())) {
                System.out.println("Source: " + source.description());
                System.out.println("Running. In the video window press ESC or Q to quit, S to save a snapshot.");
                run(source, monitor);
            }
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            System.err.println("Error: " + e.getMessage());
            exitCode = 1;
        } finally {
            HighGui.destroyAllWindows();
        }
        System.out.println("Stopped.");
        // The window toolkit keeps background threads alive, so exit explicitly
        System.exit(exitCode);
    }

    /** Main loop: read, analyse, display, handle keys. */
    private static void run(VideoSource source, DriverMonitor monitor) {
        int failedReads = 0;
        while (true) {
            long frameStart = System.currentTimeMillis();
            Mat frame = source.read();
            if (frame.empty()) {
                frame.release();
                if (source.isFile()) {
                    System.out.println("End of video.");
                    return;
                }
                if (++failedReads >= MAX_FAILED_READS) {
                    throw new IllegalStateException("The camera stopped delivering frames.");
                }
                continue;
            }
            failedReads = 0;

            monitor.processFrame(frame, source.timestampMillis());

            Mat display = fitToScreen(frame);
            HighGui.imshow(WINDOW_TITLE, display);
            // For video files, wait out the rest of the frame interval to play at normal speed
            long remaining = source.frameIntervalMillis() - (System.currentTimeMillis() - frameStart);
            int key = HighGui.waitKey((int) Math.max(1, remaining));

            // waitKey() has copied the image into the window, so the frames can be freed
            if (display != frame) {
                display.release();
            }
            if (key == KeyEvent.VK_S) {
                saveSnapshot(frame);
            }
            frame.release();

            if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_Q) {
                return;
            }
        }
    }

    private static void loadOpenCv() {
        // Extracts and loads the native library bundled for this OS / CPU
        OpenCV.loadLocally();
        System.out.println("OpenCV " + Core.VERSION + " loaded.");
    }

    /**
     * The window shows images at their real size and macOS crops a window that
     * is larger than the screen, so shrink frames to fit when necessary.
     */
    private static Mat fitToScreen(Mat frame) {
        if (displayScale < 0) {
            displayScale = 1.0;
            try {
                Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                // Leave room for the window border and title bar
                displayScale = Math.min(1.0, Math.min(
                        (screen.width - 40.0) / frame.cols(),
                        (screen.height - 80.0) / frame.rows()));
            } catch (HeadlessException ignored) {
                // No screen information: show at full size
            }
        }
        if (displayScale >= 1.0) {
            return frame;
        }
        Mat display = new Mat();
        Imgproc.resize(frame, display, new Size(), displayScale, displayScale, Imgproc.INTER_AREA);
        return display;
    }

    private static void saveSnapshot(Mat frame) {
        try {
            Files.createDirectories(SNAPSHOT_DIR);
            Path file = SNAPSHOT_DIR.resolve(LocalDateTime.now().format(SNAPSHOT_NAME));
            if (Imgcodecs.imwrite(file.toString(), frame)) {
                System.out.println("Snapshot saved: " + file);
            } else {
                System.err.println("Could not save snapshot: " + file);
            }
        } catch (IOException e) {
            System.err.println("Could not create " + SNAPSHOT_DIR + ": " + e.getMessage());
        }
    }

    private static void printBanner() {
        System.out.println("==============================================");
        System.out.println(" DRIVER DROWSINESS & DISTRACTION DETECTION");
        System.out.println("==============================================");
    }

    private static void printUsage() {
        System.out.println("""
                Usage: java -jar driver-drowsiness-detection.jar [options]

                Options:
                  --camera <index>   camera to use (default: 0)
                  --video <file>     analyse a video file instead of a camera
                  --mute             disable the alarm sound
                  -h, --help         show this help

                Keys (in the video window):
                  ESC or Q           quit
                  S                  save a snapshot to data/output/""");
    }

    /**
     * Parsed command-line options.
     *
     * @param cameraIndex camera to open when no video file is given
     * @param videoFile   video file to analyse, or {@code null} to use the camera
     * @param mute        whether the alarm sound is disabled
     * @param help        whether usage help was requested
     */
    record Options(int cameraIndex, String videoFile, boolean mute, boolean help) {

        /**
         * @param args command-line arguments
         * @return the parsed options
         * @throws IllegalArgumentException for unknown options or invalid values
         */
        static Options parse(String[] args) {
            int cameraIndex = 0;
            String videoFile = null;
            boolean mute = false;
            boolean help = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--camera" -> cameraIndex = parseCameraIndex(valueOf(args, ++i, "--camera"));
                    case "--video" -> videoFile = valueOf(args, ++i, "--video");
                    case "--mute" -> mute = true;
                    case "-h", "--help" -> help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            return new Options(cameraIndex, videoFile, mute, help);
        }

        private static String valueOf(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return args[index];
        }

        private static int parseCameraIndex(String value) {
            try {
                int index = Integer.parseInt(value);
                if (index < 0) {
                    throw new NumberFormatException();
                }
                return index;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--camera needs a number >= 0, got: " + value);
            }
        }
    }
}
