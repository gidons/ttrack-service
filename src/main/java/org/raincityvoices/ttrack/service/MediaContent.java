package org.raincityvoices.ttrack.service;

import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@RequiredArgsConstructor
public class MediaContent {
    AudioInputStream stream;
    FileMetadata metadata;

    public MediaContent(InputStream stream, FileMetadata metadata) throws UnsupportedAudioFileException, IOException {
        this(AudioSystem.getAudioInputStream(stream), metadata);
    }

    public static MediaContent fromMultipartFile(MultipartFile mpFile) throws IOException, UnsupportedAudioFileException {
        return new MediaContent(AudioSystem.getAudioInputStream(mpFile.getInputStream()), FileMetadata.fromMultipartFile(mpFile));
    }
}
