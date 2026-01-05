package org.raincityvoices.ttrack.service.async;

import org.raincityvoices.ttrack.service.Conversions;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.mapper.Property;
import org.raincityvoices.ttrack.service.util.PrototypeBean;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@PrototypeBean
@Slf4j
public class CreateMixTrackTask extends MixTrackTaskBase<CreateMixTrackTask.Input, AudioTrackTask.Output> {
    @Data
    @NoArgsConstructor
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Input extends AudioTrackTask.Input {
        @Getter(onMethod = @__(@Property(type = "json")))
        private MixInfo mixInfo;
        public Input(AudioTrackDTO mixTrack) {
            super(mixTrack);
            this.mixInfo = Conversions.toMixTrack(mixTrack).mixInfo();
        }
    }

    CreateMixTrackTask(AudioTrackDTO mixTrack) {
        super(new Input(mixTrack));
    }

    @Override
    public Class<Input> getInputClass() {
        return Input.class;
    }

    @Override
    public String toString() {
        return String.format("[CreateMixTrackTask: target=%s mixInfo=%s]", fqId(), input().getMixInfo());
    }

    @Override
    protected String getTaskType() {
        return "CreateMixTrack";
    }

    @Override
    protected void doInitialize() {
        describeTrackOrThrow(trackId());
    }

    @Override
    public Output processTrack() throws Exception {
        log.info("Processing mix track: {}", mixTrack());
        // Note: at this point, the mixTrack() DTO has the _old_ mix, which - if it exists - may be different from the requested mix.

        mixTrack().setMixInfo(input().getMixInfo());
        
        performMix();

        return new Output();
    }
}

