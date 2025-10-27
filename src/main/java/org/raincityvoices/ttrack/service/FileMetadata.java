package org.raincityvoices.ttrack.service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFileFormat.Type;

import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

@Value
@Builder
@Accessors(fluent = true)
public class FileMetadata {
    @With String fileName;
    @With String contentType;
    @With long lengthBytes;
    @With float durationSec;

    public static FileMetadata fromMultipartFile(MultipartFile mpFile) {
        return FileMetadata.builder()
                .fileName(mpFile.getOriginalFilename())
                .contentType(mpFile.getContentType())
                .lengthBytes(mpFile.getSize())
                .build();
    }

    public static FileMetadata fromBlobProperties(BlobProperties props) {
        return FileMetadata.builder()
                .fileName(inferFileName(props))
                .contentType(props.getContentType())
                .lengthBytes(props.getBlobSize())
                .build();
    }

    public static FileMetadata fromAudioFileFormat(AudioFileFormat format) {
        // NOTE: this doesn't set the filename.
        return FileMetadata.builder()
                .lengthBytes(format.getByteLength())
                .contentType(Type.WAVE.equals(format.getType()) ? AudioFormats.WAV_TYPE : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE)
                .durationSec(format.getFrameLength() / format.getFormat().getFrameRate())
                .build();
    }

    public FileMetadata updateFrom(FileMetadata other) {
        return FileMetadata.builder()
                .lengthBytes(other.lengthBytes() > 0 ? other.lengthBytes() : lengthBytes())
                .contentType(other.contentType() != null ? other.contentType() : contentType())
                .fileName(other.fileName() != null ? other.fileName() : fileName())
                .durationSec(other.durationSec() > 0.0 ? other.durationSec() : durationSec())
                .build();
    }

    private static String inferFileName(BlobProperties props) {
        if (props.getContentDisposition() != null) {
            ContentDisposition header = ContentDisposition.parse(props.getContentDisposition());
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
