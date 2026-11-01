package org.raincityvoices.ttrack.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.api.Song;
import org.raincityvoices.ttrack.service.api.SongId;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Starter hermetic integration tests for {@link org.raincityvoices.ttrack.service.SongController}.
 *
 * <p>They drive the running application over HTTP (via the JSON helpers in
 * {@link AzuriteIntegrationTestBase}) and exercise real Azure Table Storage via
 * the Azurite container configured in that base class. Requests are
 * auto-authenticated because they originate from localhost.
 *
 * <p>These two tests deliberately avoid any endpoint that returns a media
 * download URL: those require User Delegation SAS, which Azurite cannot mint.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SongControllerIntegTest extends AzuriteIntegrationTestBase {

    @Test
    void createdSongCanBeFetchedById() {
        Song toCreate = Song.builder()
            .title("Hello, Mary Lou")
            .shortTitle("Mary Lou")
            .arranger("Jane Doe")
            .key("Bb")
            .voicing("TTBB")
            .build();

        Song created = postJson("/songs", toCreate, Song.class);
        // The service assigns an ID when none is provided.
        assertThat(created.getId()).isNotEqualTo(SongId.NONE);
        assertThat(created.getTitle()).isEqualTo("Hello, Mary Lou");

        String id = created.getId().value();
        Song fetched = getJson("/songs/{id}", Song.class, id);

        assertThat(fetched.getId().value()).isEqualTo(id);
        assertThat(fetched.getTitle()).isEqualTo("Hello, Mary Lou");
        assertThat(fetched.getShortTitle()).isEqualTo("Mary Lou");
        assertThat(fetched.getArranger()).isEqualTo("Jane Doe");
        assertThat(fetched.getKey()).isEqualTo("Bb");
        assertThat(fetched.getVoicing()).isEqualTo("TTBB");
    }

    @Test
    void archivedSongIsExcludedFromDefaultListButVisibleWhenIncluded() {
        Song created = postJson("/songs", Song.builder().title("Sweet Adeline").build(), Song.class);
        String id = created.getId().value();

        assertThat(songIds(false)).contains(id);

        // DELETE archives the song (soft delete) rather than removing it.
        rest.delete("/songs/{id}", id);

        assertThat(songIds(false)).doesNotContain(id);
        assertThat(songIds(true)).contains(id);
    }

    private List<String> songIds(boolean includeArchived) {
        List<Song> songs = getJsonList("/songs?includeArchived={inc}", Song.class, includeArchived);
        return songs.stream().map(s -> s.getId().value()).toList();
    }
}
