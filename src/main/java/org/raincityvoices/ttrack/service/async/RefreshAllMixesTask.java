package org.raincityvoices.ttrack.service.async;

import java.util.List;

import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.songs.SongDTO;
import org.raincityvoices.ttrack.service.storage.songs.SongStorage;
import org.raincityvoices.ttrack.service.util.PrototypeBean;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

/**
 * Async task that launches a RefreshMixTrackTask for every mix-track of the target song.
 * 
 * Note: currently, this task does not do any locking. This is technically safe - all it 
 * does is create other AsnckTasks, and those will have locking as necessary - but it 
 * might not be ideal.
 */
@PrototypeBean
@Slf4j
public class RefreshAllMixesTask extends AsyncTask<AsyncTask.Input, AsyncTask.Output> {

    @Autowired
    private SongStorage songStorage;
    @Autowired
    private AsyncTaskManager taskManager;

    public RefreshAllMixesTask(String songId) {
        super(new Input(songId));
    }

    @Override
    protected String getTaskType() {
        return "RefreshAllMixes";
    }

    @Override
    public Class<Input> getInputClass() {
        return Input.class;
    }

    @Override
    protected void doInitialize() throws Exception {
        SongDTO song = songStorage.describeSong(songId());
        if (song == null) {
            throw new IllegalArgumentException("Song " + songId() + " does not exist.");
        }
    }
    
    @Override
    protected Output process() throws Exception {
        List<AudioTrackDTO> tracksToRecreate = songStorage.listMixesForSong(songId());
        tracksToRecreate.forEach(t -> {
            log.debug("Launching task to recreate mix track {}", t.getId());
            taskManager.schedule(CreateMixTrackTask.class, t);
        });
        // TODO add the list of task IDs to the output?
        return new Output();
    }

}
