package org.raincityvoices.ttrack.service.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.FileMetadata;
import org.raincityvoices.ttrack.service.MediaContent;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class DiskCachingMediaStorageIntegTest {

    
    private static final String FILENAME = "Sunshine Lead.wav";
    private static final File TEST_WAV_FILE = new File("./src/test/resources/sunshine-lead.wav");
    private static final String LOCATION = "test/sunshine%20lead";

    @Autowired
    private DiskCachingMediaStorage mediaStorage;
    @Autowired
    private BlobMediaClient blobClient;

    @BeforeEach
    public void startup() {
        cleanup();
    }

    @AfterEach
    public void cleanup() {
        mediaStorage.deleteFromCache(LOCATION);
        blobClient.delete(LOCATION);
    }

    @Test
    public void testEndToEnd() throws Exception {
        InputStream mediaStream = new FileInputStream(TEST_WAV_FILE);
        byte[] originalBytes = IOUtils.toByteArray(mediaStream);
        mediaStream = new FileInputStream(TEST_WAV_FILE);
        FileMetadata metadata = FileMetadata.builder()
            .fileName(FILENAME)
            .build();
        mediaStorage.putMedia(LOCATION, new MediaContent(mediaStream, metadata));
        mediaStream.close();

        FileMetadata metadata2 = mediaStorage.getMediaMetadata(LOCATION);
        verifyMetadata(metadata2);
        MediaContent fetched = mediaStorage.getMedia(LOCATION);

        assertNotNull(fetched);
        assertNotNull(fetched.metadata());
        assertNotNull(fetched.stream());
        AudioFileFormat format = AudioSystem.getAudioFileFormat(fetched.stream());
        // AudioFormat.equals() is identity-based :(
        assertEquals(AudioFormats.MONO_PCM_48KHZ.toString(), format.getFormat().toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        fetched.stream().transferTo(baos);
        byte[] fetchedBytes = baos.toByteArray();
        assertEquals(fetched.metadata().lengthBytes(), fetchedBytes.length);
        assertArrayEquals(originalBytes, fetchedBytes);
        fetched.stream().close();
        verifyMetadata(fetched.metadata());
    }

    @Test
    @Disabled
    public void testGetMetadata() {
        FileMetadata metadata = mediaStorage.getMediaMetadata(LOCATION);
        System.out.println("Metadata: " + metadata);
        verifyMetadata(metadata);
    }

    private void verifyMetadata(FileMetadata metadata) {
        assertEquals(FILENAME, metadata.fileName());
        assertEquals(AudioFormats.WAV_TYPE, metadata.contentType());
        assertEquals(11814080, metadata.lengthBytes());
        assertThat(metadata.etag(), not(emptyString()));
    }

}
