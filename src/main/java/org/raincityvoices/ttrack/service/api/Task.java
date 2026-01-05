package org.raincityvoices.ttrack.service.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.experimental.Accessors;

@Value
@Builder
@Accessors(fluent = true)
@Getter(onMethod=@__(@JsonProperty()))
public class Task {
    String id;
    String status;
    Object input;
    Object output;
    Instant scheduled;
    Instant ended;
}
