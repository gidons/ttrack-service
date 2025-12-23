package org.raincityvoices.ttrack.service.util;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public class JsonUtils {

    public static final ObjectMapper MAPPER = newMapper();

    @JsonSerialize(as = AudioFormat.class)
    interface AudioFormatMixin {
        @JsonSerialize(using = ToStringSerializer.class)
        Encoding getEncoding();
    }
    
    @JsonSerialize(as = AudioFileFormat.class)
    interface AudioFileFormatMixin {
        @JsonSerialize(using = ToStringSerializer.class)
        AudioFileFormat.Type getType();
    }

    public static ObjectMapper newMapper() {
        return new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .addMixIn(AudioFileFormat.class, AudioFileFormatMixin.class)
            .addMixIn(AudioFormat.class, AudioFormatMixin.class);
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize as JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {
        return MAPPER.readValue(json, type);
    }
}
