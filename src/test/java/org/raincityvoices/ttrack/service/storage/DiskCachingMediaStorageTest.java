package org.raincityvoices.ttrack.service.storage;

import static org.hamcrest.Matchers.oneOf;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage.DOWNLOAD_FILE_SUFFIX;
import static org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage.UPLOAD_FILE_SUFFIX;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage.RemoteStorage;
import org.raincityvoices.ttrack.service.util.FileManager;

public class DiskCachingMediaStorageTest {

    private static final String ETAG_1 = "etag-value-1";
    private static final String ETAG_2 = "etag-value-2";
    private static final String ORIGINAL_FILENAME = "Sunshine Lead";
    private static final String TEST_LOCATION = "sunshine/Lead";
    private static final AudioFileFormat AUDIO_FORMAT = new AudioFileFormat(Type.WAVE, AudioFormats.STEREO_PCM_44_1KHZ, 123 * 44100);
    private static final FileMetadata AUDIO_METADATA = FileMetadata.fromAudioFileFormat(AUDIO_FORMAT);
    private static final FileMetadata TEST_METADATA = FileMetadata.builder()
                                                        .contentType(AudioFormats.WAV_TYPE)
                                                        .durationSec(123)
                                                        .lengthBytes(1234567)
                                                        .fileName(ORIGINAL_FILENAME)
                                                        .etag(ETAG_1)
                                                        .build();
    
    private static final File CACHE_DIR = new File("/tmp/the-cache-dir");

    @Mock
    private RemoteStorage remote;
    
    @Mock
    private FileManager fileManager;

    private DiskCachingMediaStorage storage;

    @BeforeEach
    public void setup() throws IOException, UnsupportedAudioFileException {
        MockitoAnnotations.openMocks(this);
        storage = new DiskCachingMediaStorage(remote, CACHE_DIR, fileManager);
        when(remote.fetchMetadata(anyString())).thenReturn(TEST_METADATA);
        when(fileManager.getAudioFileFormat(anyFile())).thenReturn(AUDIO_FORMAT);
        when(fileManager.rename(anyFile(), anyFile())).thenReturn(true);
    }

    @Test
    void GIVEN_nothing_in_cache_WHEN_getMedia_THEN_download() throws IOException, UnsupportedAudioFileException {
        when(fileManager.exists(anyFile())).thenReturn(false);
        when(remote.exists(anyString())).thenReturn(true);
        when(remote.downloadMedia(anyString(), anyMetadata(), anyFile())).thenReturn(TEST_METADATA);
        AudioInputStream mediaStream = newInputStream();
        when(fileManager.getInputStream(anyFile())).thenReturn(mediaStream);
        
        MediaContent media = storage.getMedia(TEST_LOCATION);
        // TODO too strict... storage might wrap or replace the original file stream
        assertSame(mediaStream, media.stream());
        assertEquals(TEST_METADATA.updateFrom(AUDIO_METADATA), media.metadata());
        
        // caching client construction
        verify(remote).fetchMetadata(TEST_LOCATION);
        verify(fileManager).exists(mediaFile(ETAG_1));
        // download
        verify(remote).exists(TEST_LOCATION);
        verify(remote).downloadMedia(TEST_LOCATION, FileMetadata.UNKNOWN, downloadFile());
        // local update
        verify(fileManager).rename(downloadFile(), mediaFile(ETAG_1));
        verify(fileManager).getInputStream(mediaFile(ETAG_1));
        verify(fileManager).getAudioFileFormat(argThat(oneOf(mediaFile(ETAG_1), downloadFile())));

        verifyNoMoreInteractions(remote);
        verifyNoMoreInteractions(fileManager);
    }

    @Test
    void GIVEN_file_in_cache_and_etag_is_different_WHEN_getMedia_THEN_update() throws IOException, UnsupportedAudioFileException {
        FileMetadata newMetadata = TEST_METADATA.withEtag(ETAG_2);
        when(remote.exists(anyString())).thenReturn(true);
        // on construction, return the old ETag, which we have the file for
        when(remote.fetchMetadata(TEST_LOCATION)).thenReturn(TEST_METADATA);
        when(fileManager.exists(mediaFile(ETAG_1))).thenReturn(true);
        // on download, return the new ETag, which we don't have the file for
        when(remote.downloadMedia(anyString(), anyMetadata(), anyFile())).thenReturn(newMetadata);
        when(fileManager.exists(mediaFile(ETAG_2))).thenReturn(false);
        AudioInputStream mediaStream = newInputStream();
        when(fileManager.getInputStream(anyFile())).thenReturn(mediaStream);

        storage.getMedia(TEST_LOCATION);

        // caching client construction
        verify(remote).fetchMetadata(TEST_LOCATION);
        verify(fileManager).exists(mediaFile(ETAG_1));
        verify(fileManager).getAudioFileFormat(mediaFile(ETAG_1));
        // download
        verify(remote).exists(TEST_LOCATION);
        verify(remote).downloadMedia(TEST_LOCATION, TEST_METADATA, downloadFile());
        // local update
        verify(fileManager).rename(downloadFile(), mediaFile(ETAG_2));
        verify(fileManager).getInputStream(mediaFile(ETAG_2));
        verify(fileManager).getAudioFileFormat(argThat(oneOf(mediaFile(ETAG_2), downloadFile())));

        verifyNoMoreInteractions(remote);
        verifyNoMoreInteractions(fileManager);
    }

    @Test
    void GIVEN_file_in_cache_and_etag_is_same_WHEN_getMedia_THEN_no_update() throws IOException, UnsupportedAudioFileException {
        // on construction, return the old ETag, which we have the file for
        when(remote.fetchMetadata(TEST_LOCATION)).thenReturn(TEST_METADATA);
        when(fileManager.exists(mediaFile(ETAG_1))).thenReturn(true);
        // on download, return the same metadata
        when(remote.exists(anyString())).thenReturn(true);
        when(remote.downloadMedia(anyString(), anyMetadata(), anyFile())).thenReturn(TEST_METADATA);

        storage.getMedia(TEST_LOCATION);

        // caching client construction
        verify(remote).fetchMetadata(TEST_LOCATION);
        verify(fileManager).exists(mediaFile(ETAG_1));
        verify(fileManager).getAudioFileFormat(mediaFile(ETAG_1));
        // download
        verify(remote).exists(TEST_LOCATION);
        verify(remote).downloadMedia(TEST_LOCATION, TEST_METADATA, downloadFile());
        // provide the media
        verify(fileManager).getInputStream(mediaFile(ETAG_1));
        // nothing else
        verifyNoMoreInteractions(remote);
        verifyNoMoreInteractions(fileManager);
    }

    @Test
    void GIVEN_nothing_in_cache_or_remote_WHEN_putMedia_THEN_upload_and_save_in_cache() throws IOException, UnsupportedAudioFileException {
        when(remote.exists(anyString())).thenReturn(false);
        when(fileManager.exists(anyFile())).thenReturn(false);
        AudioInputStream mediaInStream = Mockito.mock(AudioInputStream.class);
        ByteArrayOutputStream mediaOutStream = new ByteArrayOutputStream();
        when(fileManager.getOutputStream(anyFile())).thenReturn(mediaOutStream);
        
        FileMetadata originalMetadata = FileMetadata.builder().fileName(ORIGINAL_FILENAME).build();
        // first call, during construction: no media; second call, after upload: just-uploaded metadata plus new ETag.
        when(remote.fetchMetadata(anyString())).thenReturn(null, TEST_METADATA);

        storage.putMedia(TEST_LOCATION, new MediaContent(mediaInStream, originalMetadata));
        assertEquals(TEST_METADATA.updateFrom(AUDIO_METADATA), storage.getMediaMetadata(TEST_LOCATION));

        verify(mediaInStream).transferTo(mediaOutStream);
        verify(fileManager).getOutputStream(uploadFile());
        verify(fileManager).getAudioFileFormat(uploadFile());
        verify(remote).uploadMedia(uploadFile(), TEST_LOCATION);
        verify(remote).updateMetadata(originalMetadata.updateFrom(AUDIO_METADATA), TEST_LOCATION);
        verify(remote, times(2)).fetchMetadata(TEST_LOCATION);
        verify(fileManager).rename(uploadFile(), mediaFile(ETAG_1));
        verify(fileManager, atMostOnce()).getAudioFileFormat(mediaFile(ETAG_1));

        verifyNoMoreInteractions(fileManager);
        verifyNoMoreInteractions(remote);
    }

    private AudioInputStream newInputStream() {
        AudioInputStream stream = Mockito.mock(AudioInputStream.class);
        when(stream.getFormat()).thenReturn(AUDIO_FORMAT.getFormat());
        when(stream.getFrameLength()).thenReturn((long)AUDIO_FORMAT.getFrameLength());
        return stream;
    }
    
    private File mediaFile(String suffix) {
        return storage.mediaFile(TEST_LOCATION, suffix);
    }

    private File downloadFile() { return mediaFile(DOWNLOAD_FILE_SUFFIX); }

    private File uploadFile() { return mediaFile(UPLOAD_FILE_SUFFIX); }

    private File anyFile() { return any(File.class); }
    private FileMetadata anyMetadata() { return any(FileMetadata.class); }
}
