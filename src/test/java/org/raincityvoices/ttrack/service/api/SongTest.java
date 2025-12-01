package org.raincityvoices.ttrack.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class SongTest {

    @Test
    public void test() throws JsonMappingException, JsonProcessingException {
        String json = """
                {"title":"Hi Diddly Dum","arranger":"Billy Gard","key":"Bb","durationSec":100}
                """;
        // Song song = Song.builder().title("ttt").arranger("sss").durationSec(123).key("B").build();
        Song song = JsonUtils.newMapper().readValue(json, Song.class);
        assertEquals(SongId.NONE, song.getId());
    }
}
