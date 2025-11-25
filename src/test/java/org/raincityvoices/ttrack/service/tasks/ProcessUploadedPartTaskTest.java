package org.raincityvoices.ttrack.service.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raincityvoices.ttrack.service.audio.Ffmpeg;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.FileMetadata;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.JsonUtils;

@ExtendWith(MockitoExtension.class)
public class ProcessUploadedPartTaskTest {

    private static final String PART_NAME = "Bass";
    private static final String MEDIA_LOCATION = TestData.TEST_SONG_ID + "/" + PART_NAME;

    @Mock
    private SongStorage songStorage;
    @Mock
    private MediaStorage mediaStorage;
    @Mock
    private AsyncTaskStorage taskStorage;
    @Mock
    private FileManager fileManager;
    @Mock
    private Ffmpeg ffmpeg;
    @Mock
    private AudioTrackTaskFactory taskFactory;

    private ProcessUploadedPartTask task;
    private AudioTrackDTO track;

    @Captor
    private ArgumentCaptor<AsyncTaskDTO> taskDtoCaptor;

    @BeforeEach
    public void setup() {
        when(taskFactory.getSongStorage()).thenReturn(songStorage);
        when(taskFactory.getMediaStorage()).thenReturn(mediaStorage);
        when(taskFactory.getAsyncTaskStorage()).thenReturn(taskStorage);
        when(taskFactory.getFileManager()).thenReturn(fileManager);
        when(taskFactory.getFfmpeg()).thenReturn(ffmpeg);
        lenient().when(mediaStorage.locationFor(anyString(), anyString())).thenCallRealMethod();
        track = TestData.partTrackDto(PART_NAME, false);
        task = new ProcessUploadedPartTask(track, taskFactory);
    }

    @Test
    public void GIVEN_no_issues_WHEN_initialize_THEN_succeed() throws Exception {
        when(songStorage.describeTrack(anyString(), anyString())).thenReturn(track);
        when(mediaStorage.exists(anyString())).thenReturn(true); 

        task.initialize();

        verify(songStorage).describeTrack(TestData.TEST_SONG_ID, PART_NAME);
        verify(mediaStorage).exists(track.getMediaLocation());
    }

    @Test
    public void GIVEN_no_media_WHEN_initialize_THEN_throw() {
        when(songStorage.describeTrack(anyString(), anyString())).thenReturn(track);
        when(mediaStorage.exists(anyString())).thenReturn(false); 

        assertThrows("media", RuntimeException.class, 
            () -> task.initialize());

        verify(songStorage).describeTrack(TestData.TEST_SONG_ID, PART_NAME);
        verify(mediaStorage).exists(track.getMediaLocation());
    }

    @Test
    public void GIVEN_no_mix_tracks_WHEN_process_THEN_update_metadata_no_new_tasks() throws Exception {
        FileMetadata fileMetadata = FileMetadata.fromAudioFileFormat(AudioFormats.WAV_MONO).withDurationSec(45);
        AsyncTaskDTO taskDto = newTaskDto();
        MediaContent content = new MediaContent(null, fileMetadata);
        when(songStorage.describeTrack(anyString(), anyString())).thenReturn(track);
        when(taskStorage.getTask(anyString())).thenReturn(taskDto);
        when(mediaStorage.getMedia(anyString())).thenReturn(content);
        when(songStorage.listMixesForSong(anyString())).thenReturn(List.of());

        task.call();

        assertEquals((int)fileMetadata.durationSec(), track.getDurationSec().intValue());

        verify(mediaStorage).getMedia(MEDIA_LOCATION);
        verify(songStorage).writeTrack(track);
        verify(taskFactory, never()).scheduleCreateMixTrackTask(any(AudioTrackDTO.class));
        verifyNoMoreInteractions(mediaStorage, songStorage);
        verify(taskStorage).getTask(task.taskId());
        verify(taskStorage, times(2)).updateTask(taskDtoCaptor.capture());
    }

    @Test
    public void GIVEN_two_mix_tracks_WHEN_process_THEN_update_metadata_and_schedule_two_mix_tasks() throws Exception {
        FileMetadata fileMetadata = FileMetadata.fromAudioFileFormat(AudioFormats.WAV_MONO).withDurationSec(45);
        AsyncTaskDTO taskDto = newTaskDto();
        when(taskStorage.getTask(anyString())).thenReturn(taskDto);
        when(songStorage.describeTrack(anyString(), anyString())).thenReturn(track);
        MediaContent content = new MediaContent(null, fileMetadata);
        when(mediaStorage.getMedia(anyString())).thenReturn(content);
        AudioTrackDTO mix1 = TestData.mixTrackDto(PART_NAME + " dominant");
        AudioTrackDTO mix2 = TestData.mixTrackDto(PART_NAME + " missing");
        AudioTrackDTO irrelevantMix = TestData.mixTrackDto("Full Mix").toBuilder()
            .parts(List.of("Some", "Other", "Parts")).build();
        when(songStorage.listMixesForSong(anyString())).thenReturn(List.of(mix1, irrelevantMix, mix2));

        task.call();

        assertEquals((int)fileMetadata.durationSec(), track.getDurationSec().intValue());

        verify(mediaStorage).getMedia(MEDIA_LOCATION);
        verify(songStorage).writeTrack(track);
        verify(taskFactory, times(2)).scheduleCreateMixTrackTask(any(AudioTrackDTO.class));
        verifyNoMoreInteractions(mediaStorage, songStorage);
        verify(taskStorage).getTask(task.taskId());
        verify(taskStorage, times(2)).updateTask(taskDtoCaptor.capture());
    }

    private AsyncTaskDTO newTaskDto() {
        AsyncTaskDTO taskDto = AsyncTaskDTO.builder()
            .taskId(task.taskId())
            .songId(TestData.TEST_SONG_ID)
            .trackId(PART_NAME)
            .metadata(task.getTaskMetadata())
            .status(AsyncTaskDTO.PENDING)
            .build();
        return taskDto;
    }


    @Test
    public void GIVEN_no_task_record_WHEN_process_THEN_throw_RTE() throws Exception {
        when(taskStorage.getTask(anyString())).thenReturn(null);

        assertThrows(task.taskId(), RuntimeException.class, 
            () -> task.call());

        verify(taskStorage).getTask(task.taskId());
    }
}
