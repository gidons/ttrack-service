package org.raincityvoices.ttrack.service.util;

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

/**
 * Utility class for creating and managing temporary files used by the service.
 *
 * <p>
 * This class provides a thin wrapper around java.io.File to make temporary files
 * easier to manage and to support automatic cleanup. Use Temp.file(...) to create
 * a temporary file represented by a Temp.File instance, which implements
 * java.io.Closeable so it can be used in try-with-resources blocks.
 * </p>
 *
 * <p>Behavior summary:
 * <ul>
 *   <li>By default, created Temp.File instances are registered with
 *       File.deleteOnExit() (so JVM shutdown will attempt to remove them) and are
 *       considered "owned" by the wrapper instance.</li>
 *   <li>If Temp.KEEP_FILES is set to true, neither deleteOnExit nor runtime
 *       deletion on close will occur; files are preserved.</li>
 *   <li>Calling close() on a Temp.File will attempt to delete the underlying
 *       file using Apache Commons IO's FileUtils.deleteQuietly(...) if the file
 *       instance is still the owner and KEEP_FILES is false.</li>
 *   <li>transferOwnership() marks the current wrapper as no longer owning the
 *       file and returns a new Temp.File instance for the same path which becomes
 *       the new owner. This allows transferring responsibility for cleanup to
 *       another object.</li>
 * </ul>
 * </p>
 *
 * <p>Common usage:
 * <pre>
 * try (Temp.File tmp = Temp.file("prefix", ".txt")) {
 *     // write to tmp, pass path around...
 * } // tmp.close() will delete the file (unless KEEP_FILES is true or ownership transferred)
 * </pre>
 * </p>
 *
 * <p>Notes:
 * <ul>
 *   <li>Temp.File extends java.io.File; treat it as a File instance when passing
 *       to APIs that expect a File.</li>
 *   <li>Creating a temporary file may throw IOException (propagated by
 *       Temp.file(...)).</li>
 *   <li>The implementation uses deleteQuietly to suppress exceptions during
 *       deletion; callers that need deletion diagnostics should handle removal
 *       explicitly.</li>
 *   <li>Instances are not guaranteed to be thread-safe; coordinate ownership and
 *       close operations if used across threads.</li>
 * </ul>
 * </p>
 *
 * @see java.io.Closeable
 * @see java.io.File
 * @see java.io.File#createTempFile(String, String)
 * @see org.apache.commons.io.FileUtils#deleteQuietly(java.io.File)
 */
public class Temp {

    public static boolean KEEP_FILES = false;

    public static class File extends java.io.File implements Closeable {
        private boolean owned = true;
        private File(String path) {
            super(path);
            if (!KEEP_FILES) {
                super.deleteOnExit();
            }
        }
        @Override
        public void close() throws IOException {
            if (owned && !KEEP_FILES) {
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
