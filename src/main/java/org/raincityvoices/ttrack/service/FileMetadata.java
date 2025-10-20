package org.raincityvoices.ttrack.service;

import org.springframework.http.ContentDisposition;
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
