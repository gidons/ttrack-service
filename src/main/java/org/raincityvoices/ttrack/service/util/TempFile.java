package org.raincityvoices.ttrack.service.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

public class TempFile implements Closeable {

    public static final String BASE_PREFIX = "ttrack-service-";

    private final File file;
    private boolean owned;

    public TempFile(String prefix) { this(prefix, ""); }

    public TempFile(String prefix, String suffix) {
        try {
            file = File.createTempFile(BASE_PREFIX + prefix, suffix);
            file.deleteOnExit();
            owned = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp file", e);
        }
    }

    protected TempFile(File file) {
        this.file = file;
        this.owned = true;
    }

    public File file() { return file; }

    public TempFile transferOwnership() {
        this.owned = false;
        return new TempFile(file);
    }

    @Override
    public void close() throws IOException {
        if (file != null && owned) {
            FileUtils.deleteQuietly(file);
        }
    }
}
