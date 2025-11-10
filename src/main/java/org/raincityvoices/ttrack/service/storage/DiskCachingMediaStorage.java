package org.raincityvoices.ttrack.service.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.FileMetadata;
import org.raincityvoices.ttrack.service.MediaContent;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.microsoft.applicationinsights.core.dependencies.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskCachingMediaStorage implements MediaStorage {

    public interface RemoteStorage {
        boolean exists(String location);
        void downloadMedia(String location, File destination);
        FileMetadata fetchMetadata(String location);
        void uploadMedia(File source, String location);
        void updateMetadata(FileMetadata metadata, String location);
    }

    /** Testable wrapper around static filesystem calls */
    @VisibleForTesting
    interface FileSystem {
        InputStream getInputStream(File file) throws IOException;
        OutputStream getOutputStream(File file) throws IOException;
        AudioFileFormat getAudioFileFormat(File file) throws IOException, UnsupportedAudioFileException;
        boolean exists(File file);
        boolean delete(File file);
    }

    private static FileSystem DEFAULT_FILE_SYSTEM = new FileSystem() {
        @Override
        public InputStream getInputStream(File file) throws IOException {
            return new BufferedInputStream(new FileInputStream(file));
        }
        @Override
        public OutputStream getOutputStream(File file) throws IOException {
            return new BufferedOutputStream(new FileOutputStream(file));
        }
        @Override
        public AudioFileFormat getAudioFileFormat(File file) throws IOException, UnsupportedAudioFileException {
            return AudioSystem.getAudioFileFormat(file);
        }
        @Override
        public boolean exists(File file) {
            return file.exists();
        }
        @Override
        public boolean delete(File file) {
            return file.delete();
        }
    };

    static final String METADATA_SUFFIX = ".meta";
    private static final ObjectMapper MAPPER = JsonUtils.newMapper();
    private final RemoteStorage remote;
    private final File cacheDir;
    private final FileSystem fileSystem;

    public DiskCachingMediaStorage(RemoteStorage remoteStorage, File cacheDir) {
        this(remoteStorage, cacheDir, DEFAULT_FILE_SYSTEM);
    }

    public DiskCachingMediaStorage(RemoteStorage remoteStorage, File cacheDir, FileSystem fileSystem) {
        this.remote = remoteStorage;
        this.cacheDir = cacheDir;
        this.fileSystem = fileSystem;
    }

    private class CachingMediaClient {
        private String mediaLocation;
        private final File localFile;
        private final File metadataFile;
        /** Metadata inferred from the local media file, e.g. audio format/length. */
        private FileMetadata mediaMetadata = FileMetadata.UNKNOWN;
        /** Combined remote and locally-inferred metadata. */
        private FileMetadata metadata = FileMetadata.UNKNOWN;

        CachingMediaClient(String mediaLocation) {
            this.mediaLocation = mediaLocation;
            this.localFile = mediaFile();
            this.metadataFile = metadataFile();
            if (fileSystem.exists(metadataFile)) {
                metadata = readMetadata();
            }
            if (fileSystem.exists(localFile)) {
                mediaMetadata = inferMediaMetadata();
            }
        }

        private FileMetadata inferMediaMetadata() {
            try {
                return FileMetadata.fromAudioFileFormat(fileSystem.getAudioFileFormat(localFile));
            } catch (IOException | UnsupportedAudioFileException e) {
                log.error("Unable to infer metadata from media file {}", localFile);
                return null;
            }
        }

        public MediaContent getMedia() {
            if (!remote.exists(mediaLocation)) {
                throw new IllegalArgumentException("Media at " + mediaLocation + " does not exist");
            }
            if (shouldDownload()) {
                remote.downloadMedia(mediaLocation, localFile);
                mediaMetadata = inferMediaMetadata();
                downloadMetadata();
            }
            final InputStream stream;
            try {
                stream = fileSystem.getInputStream(localFile);
            } catch (IOException e) {
                throw new RuntimeException("Unexpectedly failed to find local cached file " + localFile.getAbsolutePath(), e);
            }
            return new MediaContent(stream, metadata);
        }

        public FileMetadata getMediaMetadata() {
            if (!remote.exists(mediaLocation)) {
                throw new IllegalArgumentException("Media at " + mediaLocation + " does not exist");
            }
            return downloadMetadata();
        }

        public void putMedia(MediaContent content) {
            try {
                log.info("Writing media cache file {}...", localFile.getAbsolutePath());
                try (OutputStream fos = fileSystem.getOutputStream(localFile)) {
                    content.stream().transferTo(fos);
                }
                mediaMetadata = inferMediaMetadata();
                // Combine whatever metadata we had before with newly-provided and inferred metadata
                this.metadata = metadata.updateFrom(content.metadata()).updateFrom(mediaMetadata);
                // TODO do this asynchronously? Maybe have a background thread that flushes uploads?
                log.info("Uploading media to {}...", mediaLocation);
                remote.uploadMedia(localFile, mediaLocation);
                // Update metadata with what can be inferred from file
                remote.updateMetadata(metadata, mediaLocation);
                // Get the current ETag
                downloadMetadata();
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload media to blob " + mediaLocation, e);
            }
        }

        public void deleteFromCache() {
            if (fileSystem.exists(localFile)) {
                fileSystem.delete(localFile);
            }
            if (fileSystem.exists(metadataFile)) {
                fileSystem.delete(metadataFile);
            }
        }

        private boolean shouldDownload() {
            if (fileSystem.exists(localFile)) {
                log.info("Local file {} exists; checking etag...", localFile.getAbsolutePath());
                if (metadata != null) {
                    FileMetadata remoteMetadata = remote.fetchMetadata(mediaLocation);
                    if (metadata.etag().equals(remoteMetadata.etag())) {
                        log.info("ETag matches; using cached file.", localFile.getAbsolutePath());
                        return false;
                    } else {
                        log.info("ETag does not match");
                        return true;
                    }
                } else {
                    log.info("No metadata file found");
                    return true;
                }
            } else {
                log.info("No local file found");
                return true;
            }
        }

        private FileMetadata downloadMetadata() {
            FileMetadata remoteMetadata = remote.fetchMetadata(mediaLocation);
            metadata = remoteMetadata.updateFrom(mediaMetadata);
            writeMetadataToCache();
            return metadata;
        }

        private void writeMetadataToCache() {
            log.info("Writing metadata to cache file {}", metadataFile.getAbsolutePath());
            try (OutputStream os = fileSystem.getOutputStream(metadataFile)) {
                MAPPER.writeValue(os, metadata);
            } catch (IOException e) {
                log.atError().addArgument(metadataFile.getAbsolutePath()).setCause(e).log("Failed to write metadata file {}");
            }
        }

        private FileMetadata readMetadata() {
            try {
                return MAPPER.readValue(fileSystem.getInputStream(metadataFile), FileMetadata.class);
            } catch(IOException e) {
                throw new RuntimeException("Unexpected error parsing metadata file " + metadataFile.getAbsolutePath(), e);
            }
        }
            
        private File mediaFile() {
            String localFileName = URLEncoder.encode(mediaLocation, StandardCharsets.UTF_8);
            return new File(cacheDir, localFileName);
        }
        
        private File metadataFile() {
            return new File(cacheDir, localFileName(mediaLocation) + METADATA_SUFFIX);
        }
        
        private static String localFileName(String mediaLocation) {
            return URLEncoder.encode(mediaLocation, StandardCharsets.UTF_8);
        }       
    }

    public MediaContent getMedia(String mediaLocation) {
        Preconditions.checkNotNull(mediaLocation);
        Preconditions.checkArgument(!mediaLocation.endsWith(METADATA_SUFFIX), "Locations must not end with " + METADATA_SUFFIX);
        return new CachingMediaClient(mediaLocation).getMedia();
    }

    public void putMedia(String mediaLocation, MediaContent content) {
        Preconditions.checkNotNull(mediaLocation);
        Preconditions.checkArgument(!mediaLocation.endsWith(METADATA_SUFFIX), "Locations must not end with " + METADATA_SUFFIX);
        Preconditions.checkNotNull(content);
        new CachingMediaClient(mediaLocation).putMedia(content);
    }

    public FileMetadata getMediaMetadata(String mediaLocation) {
        Preconditions.checkNotNull(mediaLocation);
        return new CachingMediaClient(mediaLocation).getMediaMetadata();
    }

    public void deleteFromCache(String mediaLocation) {

    }
}
