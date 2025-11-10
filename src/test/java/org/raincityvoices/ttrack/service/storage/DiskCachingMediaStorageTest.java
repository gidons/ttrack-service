package org.raincityvoices.ttrack.service.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFileFormat.Type;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.raincityvoices.ttrack.service.FileMetadata;
import org.raincityvoices.ttrack.service.MediaContent;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage.RemoteStorage;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DiskCachingMediaStorageTest {

    private static final String ETAG_1 = "etag-value-1";
    private static final String ETAG_2 = "etag-value-2";
    private static final String ORIGINAL_FILENAME = "Sunshine Lead";
    private static final String TEST_LOCATION = "sunshine/Lead";
    private static final String TEST_FILENAME = URLEncoder.encode(TEST_LOCATION, UTF_8);
    private static final String TEST_MD_FILENAME = TEST_FILENAME + DiskCachingMediaStorage.METADATA_SUFFIX;
    private static final AudioFileFormat AUDIO_FORMAT = new AudioFileFormat(Type.WAVE, AudioFormats.STEREO_PCM_44_1KHZ, 123 * 44100);
    private static final FileMetadata TEST_METADATA = FileMetadata.builder()
        .contentType(AudioFormats.WAV_TYPE)
        .durationSec(123)
        .lengthBytes(1234567)
        .fileName(ORIGINAL_FILENAME)
        .etag(ETAG_1)
        .build();

    private static final ObjectMapper MAPPER = JsonUtils.newMapper();
    private static final String TEST_METADATA_JSON;
    private static final File CACHE_DIR = new File("/tmp/the-cache-dir");
    private static final File TEST_FILE = new File(CACHE_DIR, TEST_FILENAME);
    private static final File TEST_MD_FILE = new File(CACHE_DIR, TEST_MD_FILENAME);

    @Mock
    private RemoteStorage remote;
    
    @Mock
    private DiskCachingMediaStorage.FileSystem fileSystem;

    private DiskCachingMediaStorage storage;

    static {
        try {
            TEST_METADATA_JSON = MAPPER.writeValueAsString(TEST_METADATA);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setup() throws IOException, UnsupportedAudioFileException {
        MockitoAnnotations.openMocks(this);
        storage = new DiskCachingMediaStorage(remote, CACHE_DIR, fileSystem);
        when(remote.fetchMetadata(TEST_LOCATION)).thenReturn(TEST_METADATA);
        when(fileSystem.getInputStream(TEST_MD_FILE)).thenReturn(IOUtils.toInputStream(TEST_METADATA_JSON, UTF_8));
        when(fileSystem.getAudioFileFormat(TEST_FILE)).thenReturn(AUDIO_FORMAT);
    }

    @Test
    void GIVEN_nothing_in_cache_WHEN_getMedia_THEN_download() throws IOException {
        when(fileSystem.exists(anyFile())).thenReturn(false);
        when(remote.exists(anyString())).thenReturn(true);
        ByteArrayOutputStream mdStream = new ByteArrayOutputStream();
        when(fileSystem.getOutputStream(anyFile())).thenReturn(mdStream);

        storage.getMedia(TEST_LOCATION);

        verify(fileSystem, times(2)).exists(TEST_FILE);
        verify(remote).exists(TEST_LOCATION);
        verify(remote).downloadMedia(TEST_LOCATION, TEST_FILE);
        verify(remote).fetchMetadata(TEST_LOCATION);
        verify(fileSystem).getOutputStream(TEST_MD_FILE);
        FileMetadata actualMetadata = MAPPER.readValue(mdStream.toString(UTF_8), FileMetadata.class);
        assertEquals(TEST_METADATA, actualMetadata);
    }

    @Test
    void GIVEN_file_in_cache_and_etag_is_different_WHEN_getMedia_THEN_download() throws IOException {
        FileMetadata newMetadata = TEST_METADATA.withEtag(ETAG_2);
        when(fileSystem.exists(anyFile())).thenReturn(true);
        when(remote.exists(anyString())).thenReturn(true);
        when(remote.fetchMetadata(TEST_LOCATION)).thenReturn(newMetadata);
        ByteArrayOutputStream mdStream = new ByteArrayOutputStream();
        when(fileSystem.getOutputStream(anyFile())).thenReturn(mdStream);

        storage.getMedia(TEST_LOCATION);

        verify(fileSystem, times(2)).exists(TEST_FILE);
        verify(fileSystem).exists(TEST_MD_FILE);
        verify(remote).exists(TEST_LOCATION);
        verify(remote).downloadMedia(TEST_LOCATION, TEST_FILE);
        verify(remote, times(2)).fetchMetadata(TEST_LOCATION);
        verify(fileSystem).getOutputStream(TEST_MD_FILE);
        FileMetadata actualMetadata = MAPPER.readValue(mdStream.toString(UTF_8), FileMetadata.class);
        assertEquals(newMetadata, actualMetadata);
    }

    @Test
    void GIVEN_file_in_cache_and_etag_is_same_WHEN_getMedia_THEN_no_download() throws IOException {
        when(fileSystem.exists(anyFile())).thenReturn(true);
        when(remote.exists(anyString())).thenReturn(true);
        when(remote.fetchMetadata(TEST_LOCATION)).thenReturn(TEST_METADATA);

        storage.getMedia(TEST_LOCATION);

        verify(fileSystem, times(2)).exists(TEST_FILE);
        verify(fileSystem).exists(TEST_MD_FILE);
        verify(remote).exists(TEST_LOCATION);
        verify(remote).fetchMetadata(TEST_LOCATION);
        verify(remote, never()).downloadMedia(TEST_LOCATION, TEST_FILE);
        verify(fileSystem, never()).getOutputStream(anyFile());
    }

    @Test
    void GIVEN_nothing_in_cache_WHEN_putMedia_THEN_upload_and_save_in_cache() throws IOException {
        InputStream mediaInStream = Mockito.mock(InputStream.class);
        when(fileSystem.exists(anyFile())).thenReturn(false);
        ByteArrayOutputStream mediaOutStream = new ByteArrayOutputStream();
        when(fileSystem.getOutputStream(TEST_FILE)).thenReturn(mediaOutStream);
        ByteArrayOutputStream mdStream = new ByteArrayOutputStream();
        when(fileSystem.getOutputStream(TEST_MD_FILE)).thenReturn(mdStream);

        FileMetadata originalMetadata = FileMetadata.builder().fileName(ORIGINAL_FILENAME).build();
        FileMetadata audioFormatMetadata = FileMetadata.fromAudioFileFormat(AUDIO_FORMAT);
        when(remote.fetchMetadata(TEST_LOCATION)).thenReturn(TEST_METADATA);

        storage.putMedia(TEST_LOCATION, new MediaContent(mediaInStream, originalMetadata));

        verify(mediaInStream).transferTo(mediaOutStream);
        verify(remote).uploadMedia(TEST_FILE, TEST_LOCATION);
        verify(remote).updateMetadata(originalMetadata.updateFrom(audioFormatMetadata), TEST_LOCATION);
        verify(remote).fetchMetadata(TEST_LOCATION);
        verify(fileSystem).getOutputStream(TEST_MD_FILE);
        FileMetadata actualMetadata = MAPPER.readValue(mdStream.toString(UTF_8), FileMetadata.class);
        assertEquals(TEST_METADATA, actualMetadata);
    }

    private File anyFile() { return any(File.class); }
}
