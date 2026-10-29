# Driver Drowsiness & Distraction Detection Suite

**Author:** S.N.Amartya Goutham · [GitHub profile](https://github.com/gouthamsukamanchi-ship-it)

A real-time driver monitoring system written in Java with OpenCV. A webcam
watches the driver, and the system warns them on screen and with an alarm sound
when they:

- **get drowsy**: eyes stay closed for 1.5 seconds or more, or
- **get distracted**: head stays turned away from the road for 2 seconds or more.

---

## Features

| Feature | How |
|---|---|
| Face detection and tracking | YuNet deep-learning face detector. Every person gets a stable track ID, and the largest, most central face is treated as the driver. |
| Eye state (open / closed) | A small CNN classifies each eye crop as open or closed. |
| Drowsiness detection | Time-based closed-eye timer. Normal blinks (100–400 ms) are ignored. |
| Distraction detection | Head turn (yaw) and up/down tilt (pitch) are estimated from facial landmarks and compared with each person's own normal pose. |
| Risk levels | `SAFE`, `DISTRACTED`, `DROWSY`, `CRITICAL` (drowsy + distracted). |
| Alerts | Colour-coded overlay, a status panel and a continuous two-tone siren while the driver is distracted or drowsy. Only the driver triggers the alarm; passengers are shown but don't trigger it. |
| Inputs | Live webcam or a recorded video file. |
| Portable | Runs on Windows, macOS and Linux. OpenCV and the models are bundled, so nothing has to be installed apart from Java. |
| Tested | 33 JUnit tests cover the analysis logic, the alarm sound, the command line and model loading. |

---

## How it works

```
            camera / video frame
                     │
                     ▼
            ┌──────────────────┐
            │   FaceDetector   │   YuNet: faces + 5 landmarks,
            └────────┬─────────┘   tracking, driver ranking
                     │ landmarks
          ┌──────────┴───────────┐
          ▼                      ▼
  ┌──────────────┐      ┌──────────────────────┐
  │ EyeDetector  │      │ DistractionAnalyzer  │   head pose vs.
  │  (eye CNN)   │      └──────────┬───────────┘   normal pose
  └──────┬───────┘                 │
         ▼                         │
  ┌────────────────────┐           │
  │ DrowsinessAnalyzer │           │
  └─────────┬──────────┘           │
            └────────────┬─────────┘
                         ▼
                 ┌──────────────┐
                 │ RiskAnalyzer │   SAFE / DISTRACTED / DROWSY / CRITICAL
                 └──────┬───────┘
                        ▼
        OverlayRenderer   +   AlertManager
        (on-screen panel)     (alarm sound)
```

Every frame goes through `DriverMonitor.processFrame()`:

1. **Face detection and tracking** (`detection/FaceDetector`)
   - YuNet runs on a 640-pixel-wide copy of the frame and returns a box plus five
     landmarks (both eye centres, nose tip, both mouth corners) for every face.
   - Each detection is matched to the nearest existing track. A new face must be
     seen in 3 consecutive frames before it is shown, which filters out one-off
     false detections. A track survives up to 8 frames without a detection.
   - Faces are ranked by size and how close they are to the centre of the image.
     Rank 1 is the driver.

2. **Eye-state classification** (`detection/EyeDetector`)
   - A square crop (0.65 × the distance between the eyes) is taken around each
     eye centre and passed to the `open-closed-eye-0001` CNN (32×32 input).
   - The average "open" probability of both eyes gives `OPEN` (≥ 0.5) or `CLOSED`.
     If the face is not visible, or the eyes are too small or cut off by the
     frame edge, the result is `UNKNOWN`.

3. **Drowsiness** (`analysis/DrowsinessAnalyzer`)
   - Adds up how long the eyes stay closed and flags **drowsy at 1.5 s**.
   - Two consecutive open frames reset the timer, so a single misread frame does
     not. `UNKNOWN` frames neither add time nor reset it.
   - It uses time, not frame counts, so it behaves the same at any frame rate.

4. **Distraction** (`analysis/DistractionAnalyzer`)
   - The head pose is estimated from the landmarks, measured along the eye line
     so that tilting the head sideways (roll) does not count as turning:
     - `yaw   = sideways offset of the nose tip from the eye midpoint ÷ eye distance`
     - `pitch = how far down the nose tip sits between the eye line (0) and the mouth (1)`
   - The first 30 frames (about 1 s) of each person are used to learn their
     normal pose, which then keeps adapting slowly. This makes the result
     independent of camera placement and of where the person sits.
   - A deviation beyond the limits (yaw ±0.25 ≈ 30–35°, pitch ±0.12) for
     **2 s or more** means distracted. Short glances, for example at the mirrors,
     are ignored.

5. **Risk and alerts** (`analysis/RiskAnalyzer`, `ui/OverlayRenderer`, `alerts/AlertManager`)

   | Drowsy | Distracted | Risk level | Message | Alarm |
   |:---:|:---:|---|---|---|
   | no | no | `SAFE` | — | — |
   | no | yes | `DISTRACTED` | Keep your eyes on the road! | continuous siren |
   | yes | no | `DROWSY` | Take a break! | continuous siren |
   | yes | yes | `CRITICAL` | Pull over safely now! | continuous siren |

   The siren alternates between 1600 Hz and 1000 Hz every 0.25 s. It is
   generated in code (`alerts/SirenSound`), so no audio files are needed, and it
   stops as soon as the driver is back to `SAFE` (eyes open, looking ahead). If
   no audio output is available, the system beep is used instead.

---

## Project structure

```
.
├── pom.xml                         Maven build (dependencies, tests, runnable JAR)
├── README.md
├── .mvn/jvm.config                 JVM option for "mvn exec:java"
├── data/
│   ├── input/                      put demo videos here (optional)
│   └── output/                     snapshots saved with the S key
└── src/
    ├── main/
    │   ├── java/com/drowsiness/
    │   │   ├── Main.java               command line, main loop, window
    │   │   ├── DriverMonitor.java      per-frame processing pipeline
    │   │   ├── camera/                 VideoSource: webcam or video file
    │   │   ├── detection/              FaceDetector, EyeDetector and their result types
    │   │   ├── analysis/               drowsiness, distraction and risk analysis
    │   │   ├── alerts/                 AlertManager + SirenSound: audible alarm
    │   │   ├── ui/                     OverlayRenderer: on-screen overlay
    │   │   └── utils/                  FPS counter, model loader
    │   └── resources/models/           bundled ONNX models
    └── test/java/com/drowsiness/       JUnit 5 tests
```

---

## Requirements

- **Java 17 or newer** (a JDK)
- **Maven 3.8 or newer**
- A webcam, or a video file

OpenCV does **not** need to be installed. The `org.openpnp:opencv` dependency
contains the native libraries for Windows, Linux and macOS (Intel and Apple
Silicon), and the models are bundled in `src/main/resources/models`.

---

## Build and run

```bash
# Build a single runnable JAR (also runs the tests)
mvn package

# Run with the default webcam
java -jar target/driver-drowsiness-detection.jar
```

You can also run it directly with Maven, or from an IDE by running
`com.drowsiness.Main`:

```bash
mvn compile exec:java
mvn compile exec:java -Dexec.args="--video data/input/drive.mp4 --mute"
```

### Command-line options

| Option | Description |
|---|---|
| `--camera <index>` | Camera to use (default `0`) |
| `--video <file>` | Analyse a video file instead of the camera. Uses the platform's decoder, e.g. MP4/MOV on macOS. |
| `--mute` | Turn off the alarm sound, e.g. for a presentation |
| `-h`, `--help` | Show help |

### Keys (in the video window)

| Key | Action |
|---|---|
| `ESC` or `Q` | Quit |
| `S` | Save a snapshot of the annotated frame to `data/output/` |

> **macOS:** the first run asks for camera permission for the app you start it
> from (Terminal or VS Code). If the camera opens but delivers no frames, allow
> access in *System Settings → Privacy & Security → Camera* and restart that app.

---

## Configuration

All thresholds are named constants at the top of their class:

| Setting | Default | Location |
|---|---|---|
| Eyes closed before "drowsy" | 1.5 s | `DrowsinessAnalyzer.DROWSY_CLOSED_MILLIS` |
| Looking away before "distracted" | 2.0 s | `DistractionAnalyzer.DISTRACTED_MILLIS` |
| Head-turn limit (yaw) | 0.25 (≈ 30–35°) | `DistractionAnalyzer.YAW_LIMIT` |
| Look up/down limit (pitch) | 0.12 | `DistractionAnalyzer.PITCH_LIMIT` |
| Frames used to learn the normal pose | 30 (≈ 1 s) | `DistractionAnalyzer.CALIBRATION_FRAMES` |
| Eye "open" probability threshold | 0.5 | `EyeDetector.OPEN_THRESHOLD` |
| Minimum face-detection confidence | 0.80 | `FaceDetector.SCORE_THRESHOLD` |
| Requested camera resolution | 1280 × 720 | `VideoSource.CAMERA_WIDTH` / `CAMERA_HEIGHT` |

---

## Testing and evaluation

```bash
mvn test
```

**Unit tests (33, all passing):**

- `DrowsinessAnalyzerTest`: blinks are ignored, the 1.5 s threshold (exactly
  1485 ms vs 1518 ms), misread frames, unknown frames, stalled frames, and
  separate state per person
- `DistractionAnalyzerTest`: the 2 s threshold, short glances, looking down,
  sitting off-centre (not distracted), calibration, and head tilt not being
  counted as a turn
- `RiskAnalyzerTest`: the risk-level combinations
- `SirenSoundTest`: the siren has both tones at the right pitch, is loud
  without clipping, and loops without a click
- `MainOptionsTest`: command-line parsing and error handling
- `DetectorSmokeTest`: bundled models load; no faces in a blank frame; eyes that
  are too small or cut off are reported as unknown

**Scripted video test.** A 10 s test video was played through the full pipeline:

| Event in video | Expected | Result |
|---|---|---|
| Face looking ahead | `SAFE` | `SAFE` |
| Head turned at 3.0 s | `DISTRACTED` 2 s later | `DISTRACTED` at 5.00 s |
| Face leaves the frame at 6.0 s | brief hold, then no driver | no driver at 6.30 s |
| Closed eyes from 7.1 s | `DROWSY` 1.5 s later | `DROWSY` at 8.60 s |

**Live webcam test.** On an Apple Silicon MacBook Air with the built-in camera
at 1280 × 720, the system ran at 24–30 FPS (30 FPS is the camera's maximum). It
classified open and closed eyes and also detected a second person in the
background.

---

## Known limitations

- **Looking down** (for example at a phone or a screen below the camera) lowers
  the eyelids, and the eye model can read this as closed eyes.
- **Glare on glasses and very low light** reduce eye-state accuracy. An infrared
  camera, as used in commercial systems, would help at night.
- Head pose from five landmarks is an approximation. Looking down is detected
  less precisely than turning sideways.
- The normal head pose is learned in the first second after a face appears, so
  the driver should look ahead when monitoring starts.
- This is an educational project, **not a certified safety system**.

## Possible extensions

- PERCLOS (percentage of eye closure over a time window) and yawn detection
- Per-driver calibration of the eye-open threshold
- Logging of alert events and a trip summary report
- Phone-use detection with an object detector

---

## Third-party components

| Component | Source | Licence |
|---|---|---|
| OpenCV 4.9 (Java bindings and native libraries) | [opencv.org](https://opencv.org), packaged by [OpenPnP](https://github.com/openpnp/opencv) | Apache 2.0 |
| YuNet face detector (`face_detection_yunet_2023mar.onnx`) | [OpenCV Zoo](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet) | MIT |
| Eye-state classifier (`open_closed_eye.onnx`, model `open-closed-eye-0001`) | [Intel Open Model Zoo](https://github.com/openvinotoolkit/open_model_zoo/tree/master/models/public/open-closed-eye-0001) | Apache 2.0 |
| JUnit 5 (tests only) | [junit.org](https://junit.org/junit5/) | EPL 2.0 |
