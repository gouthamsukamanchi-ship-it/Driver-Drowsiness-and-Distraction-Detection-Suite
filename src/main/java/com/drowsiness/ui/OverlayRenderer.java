package com.drowsiness.ui;

import com.drowsiness.analysis.DriverStatus;
import com.drowsiness.analysis.RiskLevel;
import com.drowsiness.detection.EyeResult;
import com.drowsiness.detection.EyeState;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Draws the visual feedback onto a video frame: a colour-coded box and status
 * for every person, a status panel for the driver and a footer with the frame
 * rate and keyboard shortcuts.
 *
 * <p>All sizes are designed for a 1280-pixel-wide frame and scaled for others.
 */
public class OverlayRenderer {

    // OpenCV colours are in BGR order
    private static final Scalar GREEN = new Scalar(80, 200, 60);
    private static final Scalar ORANGE = new Scalar(0, 165, 255);
    private static final Scalar RED = new Scalar(50, 50, 235);
    private static final Scalar GREY = new Scalar(170, 170, 170);
    private static final Scalar WHITE = new Scalar(255, 255, 255);
    private static final Scalar DARK = new Scalar(25, 25, 25);
    private static final Scalar BAR_BACKGROUND = new Scalar(70, 70, 70);

    private static final int FONT = Imgproc.FONT_HERSHEY_SIMPLEX;

    private static final double REFERENCE_WIDTH = 1280;

    private static final int PANEL_WIDTH = 330;

    private static final int MARGIN = 16;

    /**
     * Draws the box, label, eye boxes and status text of one person.
     *
     * @param frame  frame to draw on
     * @param status the person's status
     */
    public void drawPerson(Mat frame, DriverStatus status) {
        double u = unit(frame);
        Rect box = status.face().box();
        Scalar colour = colourFor(status.risk());

        Imgproc.rectangle(frame, box, colour, Math.max(2, (int) Math.round(2 * u)), Imgproc.LINE_AA);
        drawEyes(frame, status.eyes(), u);

        // Label tag above the box
        String label = (status.isDriver() ? "DRIVER" : "PERSON " + status.rank())
                + (status.risk().isAlert() ? " - " + status.risk() : "");
        double labelScale = 0.55 * u;
        int tagHeight = (int) Math.round(24 * u);
        int tagWidth = textWidth(label, labelScale, 1) + (int) Math.round(12 * u);
        int tagY = box.y - tagHeight >= 0 ? box.y - tagHeight : box.y;
        fill(frame, new Rect(box.x, tagY, tagWidth, tagHeight), colour, 0.9);
        text(frame, label, box.x + (int) Math.round(6 * u), tagY + (int) Math.round(17 * u),
                labelScale, textColourOn(status.risk()), 1);

        // Status lines below the box (or above the tag if there is no room)
        EyeResult eyes = status.eyes();
        String eyeText = eyes.state() == EyeState.UNKNOWN
                ? "Eyes: --"
                : String.format("Eyes: %s (%d%% open)", eyes.state(), Math.round(eyes.openProbability() * 100));
        String[] lines = {
                eyeText,
                String.format("Drowsy %d%%  |  Distracted %d%%",
                        status.drowsinessScore(), status.distractionScore())
        };
        double lineScale = 0.48 * u;
        int lineHeight = (int) Math.round(20 * u);
        int blockHeight = lines.length * lineHeight + (int) Math.round(8 * u);
        int blockWidth = 0;
        for (String line : lines) {
            blockWidth = Math.max(blockWidth, textWidth(line, lineScale, 1));
        }
        blockWidth += (int) Math.round(12 * u);

        int blockY = box.y + box.height + (int) Math.round(4 * u);
        if (blockY + blockHeight > frame.rows()) {
            blockY = Math.max(0, tagY - blockHeight - (int) Math.round(4 * u));
        }
        fill(frame, new Rect(box.x, blockY, blockWidth, blockHeight), DARK, 0.55);
        for (int i = 0; i < lines.length; i++) {
            text(frame, lines[i], box.x + (int) Math.round(6 * u),
                    blockY + (i + 1) * lineHeight - (int) Math.round(2 * u), lineScale, WHITE, 1);
        }
    }

    private static void drawEyes(Mat frame, EyeResult eyes, double u) {
        if (eyes.state() == EyeState.UNKNOWN) {
            return;
        }
        Scalar colour = eyes.state() == EyeState.OPEN ? GREEN : RED;
        int thickness = Math.max(1, (int) Math.round(1.5 * u));
        for (Rect eye : new Rect[] {eyes.rightEyeBox(), eyes.leftEyeBox()}) {
            if (eye != null) {
                Imgproc.rectangle(frame, eye, colour, thickness, Imgproc.LINE_AA);
            }
        }
    }

    /**
     * Draws the driver status panel. It sits in the top-right corner, or the
     * top-left corner if it would cover the driver's face.
     *
     * @param frame        frame to draw on
     * @param driver       the driver's status, or {@code null} if nobody is in view
     * @param peopleInView number of people currently detected
     */
    public void drawDriverPanel(Mat frame, DriverStatus driver, int peopleInView) {
        double u = unit(frame);
        RiskLevel risk = driver == null ? RiskLevel.SAFE : driver.risk();
        Scalar colour = driver == null ? GREY : colourFor(risk);

        int width = (int) Math.round(PANEL_WIDTH * u);
        int height = (int) Math.round((risk.isAlert() ? 240 : 198) * u);
        int margin = (int) Math.round(MARGIN * u);
        Rect panel = new Rect(frame.cols() - width - margin, margin, width, height);
        if (driver != null && overlaps(panel, driver.face().box())) {
            panel = new Rect(margin, margin, width, height);
        }
        int x = panel.x;
        int y = panel.y;

        // Background, header and border
        fill(frame, panel, DARK, 0.65);
        fill(frame, new Rect(x, y, width, (int) Math.round(40 * u)), colour, 0.9);
        Imgproc.rectangle(frame, panel, colour, Math.max(1, (int) Math.round(2 * u)), Imgproc.LINE_AA);
        text(frame, panelTitle(driver), x + (int) Math.round(12 * u), y + (int) Math.round(27 * u),
                0.62 * u, driver == null ? DARK : textColourOn(risk), 2);

        int labelX = x + (int) Math.round(14 * u);
        int valueX = x + (int) Math.round(140 * u);
        double scale = 0.52 * u;

        int row = y + (int) Math.round(68 * u);
        int rowStep = (int) Math.round(28 * u);

        text(frame, "Risk level", labelX, row, scale, GREY, 1);
        text(frame, driver == null ? "--" : risk.toString(), valueX, row, scale, colour, 2);

        row += rowStep;
        text(frame, "Eyes", labelX, row, scale, GREY, 1);
        text(frame, driver == null ? "--" : eyeSummary(driver.eyes()), valueX, row, scale, WHITE, 1);

        row += rowStep;
        text(frame, "Drowsiness", labelX, row, scale, GREY, 1);
        drawBar(frame, valueX, row, x + width - valueX, driver == null ? 0 : driver.drowsinessScore(), u);

        row += rowStep;
        text(frame, "Distraction", labelX, row, scale, GREY, 1);
        drawBar(frame, valueX, row, x + width - valueX, driver == null ? 0 : driver.distractionScore(), u);

        row += rowStep;
        text(frame, "People in view", labelX, row, scale, GREY, 1);
        text(frame, Integer.toString(peopleInView), valueX, row, scale, WHITE, 1);

        if (risk.isAlert() && driver != null) {
            int boxTop = row + (int) Math.round(16 * u);
            Rect caution = new Rect(x + (int) Math.round(10 * u), boxTop,
                    width - (int) Math.round(20 * u), (int) Math.round(30 * u));
            fill(frame, caution, colour, 0.9);
            text(frame, cautionMessage(risk), caution.x + (int) Math.round(10 * u),
                    boxTop + (int) Math.round(21 * u), 0.55 * u, textColourOn(risk), 2);
        }
    }

    /**
     * Draws a footer strip with the frame rate and keyboard shortcuts.
     *
     * @param frame frame to draw on
     * @param fps   current processing frame rate
     */
    public void drawFooter(Mat frame, double fps) {
        double u = unit(frame);
        String fpsText = fps > 0 ? String.format("%.1f", fps) : "--"; // not measured yet
        String footer = "FPS " + fpsText + "   |   ESC / Q: quit   |   S: save snapshot";
        double scale = 0.5 * u;
        int height = (int) Math.round(28 * u);
        int width = textWidth(footer, scale, 1) + (int) Math.round(20 * u);
        fill(frame, new Rect(0, frame.rows() - height, width, height), DARK, 0.6);
        text(frame, footer, (int) Math.round(10 * u), frame.rows() - (int) Math.round(9 * u), scale, WHITE, 1);
    }

    // ------------------------------------------------------------------
    // Content helpers
    // ------------------------------------------------------------------

    private static String panelTitle(DriverStatus driver) {
        if (driver == null) {
            return "NO DRIVER DETECTED";
        }
        return switch (driver.risk()) {
            case SAFE -> "DRIVER STATUS: OK";
            case DISTRACTED -> "DISTRACTION WARNING";
            case DROWSY -> "DROWSINESS WARNING";
            case CRITICAL -> "CRITICAL ALERT";
        };
    }

    private static String cautionMessage(RiskLevel risk) {
        return switch (risk) {
            case DISTRACTED -> "KEEP YOUR EYES ON THE ROAD!";
            case DROWSY -> "TAKE A BREAK!";
            case CRITICAL -> "PULL OVER SAFELY NOW!";
            case SAFE -> "";
        };
    }

    private static String eyeSummary(EyeResult eyes) {
        if (eyes.state() == EyeState.UNKNOWN) {
            return "NOT VISIBLE";
        }
        return String.format("%s (%d%%)", eyes.state(), Math.round(eyes.openProbability() * 100));
    }

    private static Scalar colourFor(RiskLevel risk) {
        return switch (risk) {
            case SAFE -> GREEN;
            case DISTRACTED -> ORANGE;
            case DROWSY, CRITICAL -> RED;
        };
    }

    /** White text on red, dark text on the lighter colours. */
    private static Scalar textColourOn(RiskLevel risk) {
        return risk == RiskLevel.DROWSY || risk == RiskLevel.CRITICAL ? WHITE : DARK;
    }

    private static void drawBar(Mat frame, int x, int baseline, int availableWidth, int percent, double u) {
        int valueWidth = (int) Math.round(48 * u);
        int barWidth = Math.max(10, availableWidth - valueWidth - (int) Math.round(14 * u));
        int barHeight = (int) Math.round(12 * u);
        int top = baseline - barHeight;
        Scalar colour = percent >= 100 ? RED : percent >= 50 ? ORANGE : GREEN;

        Imgproc.rectangle(frame, new Rect(x, top, barWidth, barHeight), BAR_BACKGROUND, -1);
        int filled = (int) Math.round(barWidth * Math.min(100, percent) / 100.0);
        if (filled > 0) {
            Imgproc.rectangle(frame, new Rect(x, top, filled, barHeight), colour, -1);
        }
        text(frame, percent + "%", x + barWidth + (int) Math.round(8 * u), baseline, 0.48 * u, WHITE, 1);
    }

    // ------------------------------------------------------------------
    // Drawing primitives
    // ------------------------------------------------------------------

    private static double unit(Mat frame) {
        return Math.max(0.5, frame.cols() / REFERENCE_WIDTH);
    }

    private static void text(Mat frame, String text, int x, int y, double scale, Scalar colour, int thickness) {
        Imgproc.putText(frame, text, new Point(x, y), FONT, scale, colour, thickness, Imgproc.LINE_AA);
    }

    private static int textWidth(String text, double scale, int thickness) {
        return (int) Math.ceil(Imgproc.getTextSize(text, FONT, scale, thickness, null).width);
    }

    /** Fills a rectangle with a semi-transparent colour (opacity 0..1). */
    private static void fill(Mat frame, Rect rect, Scalar colour, double opacity) {
        int x1 = Math.max(0, rect.x);
        int y1 = Math.max(0, rect.y);
        int x2 = Math.min(frame.cols(), rect.x + rect.width);
        int y2 = Math.min(frame.rows(), rect.y + rect.height);
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        Mat roi = frame.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
        Mat overlay = new Mat(roi.size(), roi.type(), colour);
        try {
            Core.addWeighted(overlay, opacity, roi, 1 - opacity, 0, roi);
        } finally {
            overlay.release();
            roi.release();
        }
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x < b.x + b.width && b.x < a.x + a.width
                && a.y < b.y + b.height && b.y < a.y + a.height;
    }
}
