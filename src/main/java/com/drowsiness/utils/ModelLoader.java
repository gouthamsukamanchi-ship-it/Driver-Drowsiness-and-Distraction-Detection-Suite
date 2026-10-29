package com.drowsiness.utils;

import org.opencv.core.MatOfByte;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads the neural-network model files that are bundled on the classpath
 * ({@code src/main/resources/models}).
 *
 * <p>Loading from the classpath instead of the file system means the program
 * works from any working directory and from a single runnable JAR.
 */
public final class ModelLoader {

    private ModelLoader() {
    }

    /**
     * Reads a bundled model into memory.
     *
     * @param resource absolute classpath location, e.g. {@code /models/x.onnx}
     * @return the model bytes, ready to pass to OpenCV
     * @throws IllegalStateException if the resource does not exist
     */
    public static MatOfByte load(String resource) {
        try (InputStream in = ModelLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Model not found on classpath: " + resource);
            }
            return new MatOfByte(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read model " + resource, e);
        }
    }
}
