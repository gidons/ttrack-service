package org.raincityvoices.ttrack.service.async;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.util.PrototypeBean;

import lombok.extern.slf4j.Slf4j;

@PrototypeBean
@Slf4j
public class RefreshMixTrackTask extends MixTrackTaskBase<AudioTrackTask.Input, AudioTrackTask.Output> {

    public RefreshMixTrackTask(AudioTrackDTO track) {
        super(new Input(track));
    }

    @Override
    public Class<Input> getInputClass() {
        return Input.class;
    }
    
    @Override
    public String toString() {
        return String.format("[RefreshMixTrackTask: target=%s/%s]", songId(), trackId());
    }

    @Override
    protected String getTaskType() {
        return "RefreshMixTrack";
    }

    @Override
    protected void doInitialize() {
        describeTrackOrThrow(trackId());
    }

    @Override
    public Output processTrack() throws Exception {
        log.info("Refreshing mix track: {}", mixTrack());

        performMix();

        return new Output();
    }
}
