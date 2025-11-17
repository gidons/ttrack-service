package org.raincityvoices.ttrack.service.util;

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

public class Temp {

    public static class File extends java.io.File implements Closeable {
        private boolean owned = true;
        private File(String path) {
            super(path);
            super.deleteOnExit();
        }
        @Override
        public void close() throws IOException {
            if (owned) {
                FileUtils.deleteQuietly(this);
            }
        }
        public File transferOwnership() {
            this.owned = false;
            return new File(this.getAbsolutePath());
        }
    }

    public static File file(String prefix, String suffix) throws IOException {
        return new File(File.createTempFile(prefix, suffix).getAbsolutePath());
    }
    public static File file(String prefix) throws IOException {
        return file(prefix, "");
    }
}
