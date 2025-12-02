package org.raincityvoices.ttrack.service.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
public class DiskCachingMediaStorageIntegTest {
    
    private static final String FILENAME_WAV = "Sunshine Lead.wav";
    private static final String FILENAME_MP3 = "Sunshine Lead.mp3";
    private static final File TEST_WAV_FILE = TestData.SUNSHINE_LEAD_WAV;
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
        blobClient.deleteMedia(LOCATION);
    }

    private class DownloadTask implements Callable<FileMetadata> {
        private int num;
        DownloadTask(int num) { this.num = num; }
        @Override
        public FileMetadata call() throws Exception {
            log.info("Starting download {}...", num);
            try {
                MediaContent media = mediaStorage.getMedia(LOCATION);
                log.info("Download {} complete.", num);
                return media.metadata();
            } catch(Exception e) {
                log.error("Exception in download {}", num, e);
                throw e;
            }
        }
    }

    @Test
    public void testMultithreadedDownload() throws Exception {
        AudioInputStream mediaStream = AudioSystem.getAudioInputStream(TEST_WAV_FILE);
        FileMetadata metadata = FileMetadata.builder().fileName(FILENAME_WAV).build();
        mediaStorage.putMedia(LOCATION, new MediaContent(mediaStream, metadata));
        mediaStream.close();
        
        mediaStorage.deleteFromCache(LOCATION);
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 1; i <= 3; ++i) {
            executor.submit(new DownloadTask(i));
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
    
    @Test
    public void testEndToEnd() throws Exception {
        InputStream mediaStream = new BufferedInputStream(new FileInputStream(TEST_WAV_FILE));
        byte[] originalBytes = IOUtils.toByteArray(mediaStream);
        mediaStream = new BufferedInputStream(new FileInputStream(TEST_WAV_FILE));
        FileMetadata metadata = FileMetadata.builder()
            .fileName(FILENAME_WAV)
            .build();
        mediaStorage.putMedia(LOCATION, new MediaContent(mediaStream, metadata));
        mediaStream.close();

        FileMetadata metadata2 = mediaStorage.getMediaMetadata(LOCATION);
        verifyWavMetadata(metadata2);
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
        verifyWavMetadata(fetched.metadata());
    }

    @Test
    public void testMp3EndToEnd() throws Exception {
        InputStream mediaStream = new BufferedInputStream(new FileInputStream(TestData.SUNSHINE_LEAD_MP3));
        byte[] originalBytes = IOUtils.toByteArray(mediaStream);
        mediaStream = new BufferedInputStream(new FileInputStream(TestData.SUNSHINE_LEAD_MP3));
        FileMetadata metadata = FileMetadata.builder()
            .fileName(FILENAME_MP3)
            .build();
        mediaStorage.putMedia(LOCATION, new MediaContent(mediaStream, metadata));
        mediaStream.close();

        FileMetadata metadata2 = mediaStorage.getMediaMetadata(LOCATION);
        verifyMp3Metadata(metadata2);
        MediaContent fetched = mediaStorage.getMedia(LOCATION);

        assertNotNull(fetched);
        assertNotNull(fetched.metadata());
        assertNotNull(fetched.stream());
        AudioFileFormat format = AudioSystem.getAudioFileFormat(fetched.stream());
        System.out.printf("AudioFileFormat for %s: %s\n", fetched.metadata().fileName(), JsonUtils.toJson(format));
        // AudioFormat.equals() is identity-based :(
        // assertEquals(AudioFormats.MONO_PCM_48KHZ.toString(), format.getFormat().toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        fetched.stream().transferTo(baos);
        byte[] fetchedBytes = baos.toByteArray();
        assertEquals(fetched.metadata().lengthBytes(), fetchedBytes.length);
        assertArrayEquals(originalBytes, fetchedBytes);
        fetched.stream().close();
        verifyMp3Metadata(fetched.metadata());
    }

    private void verifyWavMetadata(FileMetadata metadata) {
        assertEquals(FILENAME_WAV, metadata.fileName());
        assertEquals(AudioFormats.WAV_TYPE, metadata.contentType());
        assertEquals(11814080, metadata.lengthBytes());
        assertThat(metadata.etag(), not(emptyString()));
    }

    private void verifyMp3Metadata(FileMetadata metadata) {
        assertEquals(FILENAME_MP3, metadata.fileName());
        assertEquals(AudioFormats.MP3_TYPE, metadata.contentType());
        assertEquals(985004, metadata.lengthBytes());
        assertThat(metadata.etag(), not(emptyString()));
    }

}
