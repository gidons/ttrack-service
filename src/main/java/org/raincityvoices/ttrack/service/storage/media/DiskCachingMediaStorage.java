package org.raincityvoices.ttrack.service.storage.media;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import org.raincityvoices.ttrack.service.storage.files.FileMetadata;
import org.raincityvoices.ttrack.service.storage.files.RemoteFileStorage;
import org.raincityvoices.ttrack.service.util.AutoLock;
import org.raincityvoices.ttrack.service.util.DefaultFileManager;
import org.raincityvoices.ttrack.service.util.FileManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.microsoft.applicationinsights.core.dependencies.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * A disk-based caching implementation of {@link MediaStorage} that manages media files locally
 * while delegating to a remote storage backend.
 *
 * <p>This class implements a two-tier storage strategy:
 * <ul>
 *   <li>Remote storage via {@link RemoteFileStorage} for persistent data and metadata
 *   <li>Local disk cache for improved performance and offline access
 * </ul>
 *
 * <p>Key features:
 * <ul>
 *   <li>Automatic caching of downloaded media with ETags for change detection
 *   <li>Thread-safe operations using {@link ReentrantLock} per media location
 *   <li>Lazy loading of media with metadata validation
 *   <li>Configurable cache directory and file management
 * </ul>
 *
 * <p>The cache uses media ETags as file identifiers to handle version updates efficiently.
 * When media is modified remotely, the new version is downloaded and the old cached file
 * is automatically replaced. Temporary download/upload files are used during transitions
 * to ensure consistency.
 *
 * <p>A {@link LoadingCache} maintains per-location clients to manage concurrent access to
 * different media items, with a maximum of 10 concurrent locations and 10 concurrent threads.
 *
 * Note that the term "media" here is used in its general HTTP sense of content,
 * and not limited to audio/video/etc.
 * 
 * @see MediaStorage
 * @see RemoteFileStorage
 * @see FileMetadata
 */
@Slf4j
public class DiskCachingMediaStorage implements MediaStorage {

    static final String UPLOAD_FILE_SUFFIX = "upload";
    static final String DOWNLOAD_FILE_SUFFIX = "download";
    
    private final RemoteFileStorage remote;
    private final File cacheDir;
    private final FileManager fileManager;
    
    private final LoadingCache<String, CachingMediaClient> locationClients = CacheBuilder.newBuilder()
        .maximumSize(10)
        .concurrencyLevel(10)
        .build(CacheLoader.from(l -> new CachingMediaClient(l)));
    
    public DiskCachingMediaStorage(RemoteFileStorage remoteStorage, File cacheDir) {
        this(remoteStorage, cacheDir, new DefaultFileManager());
    }
    
    public DiskCachingMediaStorage(RemoteFileStorage remoteStorage, File cacheDir, FileManager fileManager) {
        this.remote = remoteStorage;
        this.cacheDir = cacheDir;
        this.fileManager = fileManager;
    }
    
    private class CachingMediaClient {
        private final String mediaLocation;
        private final ReentrantLock lock = new ReentrantLock();
        private File localFile;
        private FileMetadata metadata;

        CachingMediaClient(String mediaLocation) {
            this.mediaLocation = mediaLocation;
            this.metadata = FileMetadata.UNKNOWN;
            // Check if we already have the most recent media in the cache, and if so update the in-memory representation
            FileMetadata remoteMetadata = remote.fetchMetadata(mediaLocation);
            if (remoteMetadata != null) {
                File expectedFile = mediaFile(remoteMetadata.etag());
                if (fileManager.exists(expectedFile)) {
                    updateLocalFileAndMetadata(expectedFile, remoteMetadata);
                }
            }
        }
        
        public boolean exists() {
            return remote.exists(mediaLocation);
        }

        public MediaContent getMedia() {
            if (!remote.exists(mediaLocation)) {
                throw new IllegalArgumentException("No media found at location " + mediaLocation);
            }
            downloadIfNecessary();
            InputStream stream;
            try {
                stream = fileManager.getInputStream(localFile);
                return new MediaContent(stream, metadata);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read media from local file " + localFile, e);
            }
        }

        private void downloadIfNecessary() {
            try(AutoLock al = new AutoLock(lock)) {
                if (localFile != null && !fileManager.exists(localFile)) {
                    log.info("Local file {} does not exist. Clearing metadata to force update.", localFile);
                    metadata = FileMetadata.UNKNOWN;
                }
                File downloadFile = mediaFile(DOWNLOAD_FILE_SUFFIX);
                FileMetadata remoteMetadata = remote.download(mediaLocation, metadata, downloadFile);
                if (remoteMetadata == null) {
                    // Media deleted
                    log.info("Media for {} no longer available on remote storage.", mediaLocation);
                    return;
                }
                if (remoteMetadata.etag().equals(metadata.etag())) {
                    if (remoteMetadata.lengthBytes() != metadata.lengthBytes()) {
                        log.warn("Cached media length ({}) is different from remote ({}); last download may have failed. Retrying.",
                            metadata.lengthBytes(), remoteMetadata.lengthBytes()
                        );
                        deleteFromCache();
                        remoteMetadata = remote.download(mediaLocation, FileMetadata.UNKNOWN, downloadFile);
                    } else {
                        log.info("Media for {} has not changed since last downloaded.", mediaLocation);
                        return;
                    }
                }
                log.info("Downloaded media for {} with new ETag {}: creating new local file.", mediaLocation, remoteMetadata.etag());
                updateLocalFileAndMetadata(downloadFile, remoteMetadata);
            }
        }
        
        private void updateLocalFileAndMetadata(File tempFile, FileMetadata remoteMetadata) {
            File newFile = mediaFile(remoteMetadata.etag());
            if (!newFile.equals(tempFile)) {
                if (!fileManager.rename(tempFile, newFile)) {
                    throw new RuntimeException("Failed to rename " + tempFile + " to " + newFile);
                }
            }
            FileMetadata inferredMetadata = FileMetadata.fromTempFile(newFile, fileManager)
                // Ignore some attributes that are almost guaranteed to be wrong
                .withFileName(null)
                .withUpdated(null);
            metadata = remoteMetadata.updateFrom(inferredMetadata);
            localFile = newFile;
        }
        
        public FileMetadata getMediaMetadata() {
            return metadata;
        }
        
        public void putMedia(MediaContent content) {
            File uploadFile = mediaFile(UPLOAD_FILE_SUFFIX);
            try(AutoLock al = new AutoLock(lock)) {
                log.info("Writing media to temporary upload file {}...", uploadFile);
                try(OutputStream fos = fileManager.getOutputStream(uploadFile)) {
                    content.stream().transferTo(fos);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write media to disk at " + uploadFile, e);
                }
                FileMetadata mediaMetadata = FileMetadata.fromFile(uploadFile, fileManager);
                // Combine whatever metadata we had before with newly-provided and inferred metadata
                this.metadata = metadata.updateFrom(mediaMetadata).updateFrom(content.metadata());
                // TODO do this asynchronously? Maybe have a background thread that flushes uploads?
                try {
                    log.info("Uploading media to {}...", mediaLocation);
                    remote.upload(uploadFile, mediaLocation);
                    // Update metadata with what can be inferred from file
                    remote.updateMetadata(metadata, mediaLocation);
                } catch(Exception e) {
                   throw new RuntimeException("Failed to upload media and/or metadata to " + mediaLocation);
                }
                // Get the current metadata, including ETag, which cannot be determined locally
                // Note: theoretically this could come back null, if someone deleted the Blob out-of-band.
                FileMetadata remoteMetadata = remote.fetchMetadata(mediaLocation);
                updateLocalFileAndMetadata(uploadFile, remoteMetadata);
            }
        }

        public boolean delete() {
            if (!remote.exists(mediaLocation)) {
                log.warn("No media to delete at {}.", mediaLocation);
                return false;
            }
            lock.lock();
            try {
                log.info("Deleting media for {}", mediaLocation);
                deleteFromCache();
                remote.delete(mediaLocation);
                // reset the metadata, including ETag, so the media will be downloaded if recreated later.
                metadata = FileMetadata.UNKNOWN;
                return true;
            } finally {
                lock.unlock();
            }
        }

        public void deleteFromCache() {
            if (localFile != null && fileManager.exists(localFile)) {
                fileManager.delete(localFile);
            }
        }

        private File mediaFile(String suffix) {
            return DiskCachingMediaStorage.this.mediaFile(mediaLocation, suffix);
        }
    }

    @Override
    public boolean exists(String mediaLocation) {
        return getClient(mediaLocation).exists();
    }

    public MediaContent getMedia(String mediaLocation) {
        Preconditions.checkNotNull(mediaLocation);
        return getClient(mediaLocation).getMedia();
    }

    @Override
    public String getDownloadUrl(String mediaLocation, Duration timeout) {
        return remote.getDownloadUrl(mediaLocation, timeout);
    }

    public void putMedia(String mediaLocation, MediaContent content) {
        Preconditions.checkNotNull(mediaLocation);
        Preconditions.checkNotNull(content);
        getClient(mediaLocation).putMedia(content);
    }

    public FileMetadata getMediaMetadata(String mediaLocation) {
        Preconditions.checkNotNull(mediaLocation);
        return getClient(mediaLocation).getMediaMetadata();
    }

    @Override
    public boolean deleteMedia(String mediaLocation) {
        return getClient(mediaLocation).delete();        
    }

    public void deleteFromCache(String mediaLocation) {
        getClient(mediaLocation).deleteFromCache();
        locationClients.invalidate(mediaLocation);
    }

    public void clearCache() {
        
    }

    @VisibleForTesting
    File mediaFile(String mediaLocation, String suffix) {
        String localFileName = URLEncoder.encode(mediaLocation, StandardCharsets.UTF_8) + "." + suffix;
        return new File(cacheDir, localFileName);
    }

    private CachingMediaClient getClient(String mediaLocation) {
        try {
            return locationClients.get(mediaLocation);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to get caching client for " + mediaLocation);
        }
    }
}
