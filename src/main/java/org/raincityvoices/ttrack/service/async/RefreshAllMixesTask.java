package org.raincityvoices.ttrack.service.async;

import java.util.List;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.PrototypeBean;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

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
