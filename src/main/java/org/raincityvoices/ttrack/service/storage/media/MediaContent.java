package org.raincityvoices.ttrack.service.storage.media;

import java.io.IOException;
import java.io.InputStream;

import org.raincityvoices.ttrack.service.storage.files.FileMetadata;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * A representation of media content, as a combination of a stream of binary data
 * and a {@link FileMetadata} describing it.
 * Note that the term "media" here is used in its general HTTP sense of content,
 * and not limited to audio/video/etc.
 */
@Value
@Accessors(fluent = true)
@RequiredArgsConstructor
public class MediaContent {
    InputStream stream;
    FileMetadata metadata;

    public static MediaContent fromMultipartFile(MultipartFile mpFile) throws IOException {
        return new MediaContent(mpFile.getInputStream(), FileMetadata.fromMultipartFile(mpFile));
    }
}
