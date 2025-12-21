package org.raincityvoices.ttrack.service.tasks;

import java.util.List;

import org.raincityvoices.ttrack.service.SongController;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RefreshAllMixesTask extends AudioTrackTask {

    public RefreshAllMixesTask(AudioTrackDTO allChannelsTrack, AudioTrackTaskManager manager) {
        super(allChannelsTrack, manager);
    }

    @Override
    protected String getTaskType() {
        return "RefreshAllMixes";
    }

    @Override
    protected TaskMetadata getTaskMetadata() {
        return new TaskMetadata.Empty();
    }

    @Override
    protected void doInitialize() throws Exception {
        if (!track().isMixTrack() || !(track().getId().equals(SongController.ALL_CHANNEL_MIX_ID))) {
            throw new IllegalArgumentException(String.format("Track %s is not the all-mix track.", trackFqId()));
        }
    }
    
    @Override
    protected AudioTrackDTO process() throws Exception {
        List<AudioTrackDTO> tracksToRecreate = songStorage().listMixesForSong(songId());
        tracksToRecreate.forEach(t -> {
            log.debug("Launching task to recreate mix track {}", t.getId());
            manager().scheduleCreateMixTrackTask(t);
        });
        return track();
    }

}
