package org.raincityvoices.ttrack.service.tasks;

import org.raincityvoices.ttrack.service.api.MixInfo;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Metadata for CreateMixTrackTask.
 * Contains information needed to restart the task: the complete MixInfo specification.
 */
@Value
@Jacksonized
@Builder(toBuilder = true)
public class CreateMixTrackMetadata implements TaskMetadata {
    
    /** The complete mix information including parts, mix specification, and mix parameters. */
    private MixInfo mixInfo;
}
