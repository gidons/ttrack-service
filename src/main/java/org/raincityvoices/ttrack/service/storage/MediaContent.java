package org.raincityvoices.ttrack.service.storage;

import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@RequiredArgsConstructor
public class MediaContent {
    InputStream stream;
    FileMetadata metadata;

    public static MediaContent fromMultipartFile(MultipartFile mpFile) throws IOException, UnsupportedAudioFileException {
        return new MediaContent(mpFile.getInputStream(), FileMetadata.fromMultipartFile(mpFile));
    }
}
