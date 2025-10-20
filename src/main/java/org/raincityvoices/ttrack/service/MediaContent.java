package org.raincityvoices.ttrack.service;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class MediaContent {
    InputStream stream;
    FileMetadata metadata;

    public static MediaContent fromMultipartFile(MultipartFile mpFile) throws IOException {
        return new MediaContent(mpFile.getInputStream(), FileMetadata.fromMultipartFile(mpFile));

    }
}
