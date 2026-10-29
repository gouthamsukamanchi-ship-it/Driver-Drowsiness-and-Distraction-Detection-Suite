package com.drowsiness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainOptionsTest {

    @Test
    void defaultsToFirstCamera() {
        Main.Options options = Main.Options.parse(new String[0]);
        assertEquals(0, options.cameraIndex());
        assertNull(options.videoFile());
        assertFalse(options.mute());
        assertFalse(options.help());
    }

    @Test
    void parsesAllOptions() {
        Main.Options options = Main.Options.parse(
                new String[] {"--camera", "1", "--video", "clip.mp4", "--mute", "--help"});
        assertEquals(1, options.cameraIndex());
        assertEquals("clip.mp4", options.videoFile());
        assertTrue(options.mute());
        assertTrue(options.help());
    }

    @Test
    void rejectsUnknownOption() {
        assertThrows(IllegalArgumentException.class, () -> Main.Options.parse(new String[] {"--fast"}));
    }

    @Test
    void rejectsMissingValue() {
        assertThrows(IllegalArgumentException.class, () -> Main.Options.parse(new String[] {"--video"}));
    }

    @Test
    void rejectsInvalidCameraIndex() {
        assertThrows(IllegalArgumentException.class, () -> Main.Options.parse(new String[] {"--camera", "-1"}));
        assertThrows(IllegalArgumentException.class, () -> Main.Options.parse(new String[] {"--camera", "front"}));
    }
}
