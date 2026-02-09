package org.raincityvoices.ttrack.service.storage.files;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.comparator.Comparators;
import org.springframework.web.multipart.MultipartFile;

import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@Builder
@Accessors(fluent = true)
@Getter(onMethod = @__(@JsonProperty))
public class FileMetadata {
    @With String fileName;
    @With String contentType;
    @With long lengthBytes;
    @With float durationSec;
    @With Instant updated;
    @Default @With String etag = "";

    public static final FileMetadata UNKNOWN = FileMetadata.builder().build();

    public static FileMetadata fromMultipartFile(MultipartFile mpFile) {
        String contentType = mpFile.getContentType();
        if (contentType == null || contentType.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
            contentType = inferContentTypeFromName(mpFile.getOriginalFilename());
        }
        log.info("MPF content type: {}; inferred: {}", mpFile.getContentType(), contentType);
        return FileMetadata.builder()
                .fileName(mpFile.getOriginalFilename())
                .contentType(contentType)
                .lengthBytes(mpFile.getSize())
                .updated(Instant.now())
                .build();
    }

    public static FileMetadata fromBlobProperties(BlobProperties props) {
        return FileMetadata.builder()
                .fileName(inferFileName(props.getContentDisposition()))
                .contentType(props.getContentType())
                .lengthBytes(props.getBlobSize())
                .updated(props.getLastModified().toInstant())
                .etag(props.getETag())
                .build();
    }

    public static FileMetadata fromBlobDownloadHeaders(BlobDownloadHeaders headers) {
        return FileMetadata.builder() 
                        .contentType(headers.getContentType())
                        .fileName(inferFileName(headers.getContentDisposition()))
                        .lengthBytes(headers.getContentLength())
                        .updated(headers.getLastModified().toInstant())
                        .etag(headers.getETag())
                        .build();
    }

    public static FileMetadata fromFile(File file, FileManager fileManager) {
        String contentTypeFromName = inferContentTypeFromName(file.getName());
        final FileMetadata inferred = FileMetadata.builder()
            .contentType(contentTypeFromName)
            .lengthBytes(fileManager.getLengthBytes(file))
            .fileName(file.getName())
            .updated(Instant.ofEpochMilli(file.lastModified()))
            .build();
        log.info("File metadata inferred for file {}: {}", file, inferred);

        FileMetadata fromAudio = null;
        if (contentTypeFromName == null || contentTypeFromName.startsWith("audio")) {
            fromAudio = inferFromFileContents(file, fileManager);
            log.info("File metadata inferred from audio file {}: {}", file, fromAudio);
        }
        return FileMetadata.UNKNOWN.updateFrom(inferred).updateFrom(fromAudio);
    }

    /**
     * Infer metadata for a file whose name and history are not reliable; we can only use
     * the file contents (including its size).
     */
    public static FileMetadata fromTempFile(File file, FileManager fileManager) {
        final FileMetadata fromContents = inferFromFileContents(file, fileManager);
        return fromContents.withLengthBytes(fileManager.getLengthBytes(file));
    }

    private static String inferContentTypeFromName(String fileName) {
        if (fileName == null) { return null; }
        if (fileName.endsWith(".mxl")) {
            // Zipped MusicXML file
            return "application/zip";
        }
        return MediaTypeFactory.getMediaType(fileName).map(MediaType::getType).orElse(null);
    }

    private static FileMetadata inferFromFileContents(File file, FileManager fileManager) {
        try {
            AudioFileFormat format = fileManager.getAudioFileFormat(file);
            return FileMetadata.fromAudioFileFormat(format);
        } catch(IOException e) {
            log.error("Unable to open file {}", file, e);
        } catch(UnsupportedAudioFileException e) {
            log.warn("Unsupported audio file format for file {}", file);
        }
        return FileMetadata.UNKNOWN;
    }

    public static FileMetadata fromAudioFileFormat(AudioFileFormat format) {
        // NOTE: this doesn't set the filename.
        log.info("Inferring metadata from AudioFileFormat: {}", JsonUtils.toJson(format));
        float durationSec = format.getFrameLength() / (format.getFormat().getFrameRate());
        final String contentType;
        switch(format.getType().toString()) {
            case "WAVE":
                contentType = AudioFormats.WAV_TYPE;
                break;
            case "MP3":
                contentType = AudioFormats.MP3_TYPE;
                break;
            default:
                log.warn("Unable to infer audio metadata for format type {}", format.getType());
                contentType = MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
                // the format might have negative frameLength and/or frameRate, which will be confusing
                if (durationSec < 0) {
                    durationSec = 0.0f;
                }
        }
        return FileMetadata.builder()
                // AudioFormat.getByteLength() appears to be unreliable.
                .lengthBytes(format.getByteLength())
                .contentType(contentType)
                .durationSec(durationSec)
                .build();
    }

    public FileMetadata updateFrom(FileMetadata other) {
        if (other == null) {
            return this;
        }
        
        return FileMetadata.builder()
                .lengthBytes(other.lengthBytes() > 0 ? other.lengthBytes() : lengthBytes())
                .contentType(other.contentType() != null ? other.contentType() : contentType())
                .fileName(other.fileName() != null ? other.fileName() : fileName())
                .durationSec(other.durationSec() > 0.0 ? other.durationSec() : durationSec())
                .updated(ObjectUtils.max(other.updated(), updated()))
                .etag(!StringUtils.isEmpty(other.etag()) ? other.etag() : etag())
                .build();
    }

    private static String inferFileName(String contentDisposition) {
        if (contentDisposition != null) {
            ContentDisposition header = ContentDisposition.parse(contentDisposition);
            return header.getFilename(); // may be null!
        }
        return null;
    }

    public BlobHttpHeaders toBlobHttpHeaders() {
        return new BlobHttpHeaders()
            .setContentDisposition(fileName() != null ? "attachment; filename=" + fileName() : null)
            .setContentType(contentType());
    }
}
