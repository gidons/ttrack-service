package org.raincityvoices.ttrack.service.storage;

import javax.sound.sampled.AudioFileFormat;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MimeTypeUtils;
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
    @Default @With String etag = "";

    public static final FileMetadata UNKNOWN = FileMetadata.builder().build();

    public static FileMetadata fromMultipartFile(MultipartFile mpFile) {
        return FileMetadata.builder()
                .fileName(mpFile.getOriginalFilename())
                .contentType(mpFile.getContentType())
                .lengthBytes(mpFile.getSize())
                .build();
    }

    public static FileMetadata fromBlobProperties(BlobProperties props) {
        return FileMetadata.builder()
                .fileName(inferFileName(props.getContentDisposition()))
                .contentType(props.getContentType())
                .lengthBytes(props.getBlobSize())
                .etag(props.getETag())
                .build();
    }

    public static FileMetadata fromBlobDownloadHeaders(BlobDownloadHeaders headers) {
        return FileMetadata.builder() 
                        .contentType(headers.getContentType())
                        .fileName(inferFileName(headers.getContentDisposition()))
                        .lengthBytes(headers.getContentLength())
                        .etag(headers.getETag())
                        .build();
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
                .lengthBytes(format.getByteLength())
                .contentType(contentType)
                .durationSec(durationSec)
                .build();
    }

    public FileMetadata updateFrom(FileMetadata other) {
        return FileMetadata.builder()
                .lengthBytes(other.lengthBytes() > 0 ? other.lengthBytes() : lengthBytes())
                .contentType(other.contentType() != null ? other.contentType() : contentType())
                .fileName(other.fileName() != null ? other.fileName() : fileName())
                .durationSec(other.durationSec() > 0.0 ? other.durationSec() : durationSec())
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
