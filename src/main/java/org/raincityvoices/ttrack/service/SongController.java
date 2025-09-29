package org.raincityvoices.ttrack.service;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class SongController {

    @GetMapping("/songs")
    public List<Song> listSongs() {
        // Return a small static sample list for now.
        return List.of(
            new Song("1", "Imagine", "John Lennon", "C", 183),
            new Song("2", "Here Comes the Sun", "The Beatles", "A", 185),
            new Song("3", "Bohemian Rhapsody", "Queen", "Bb", 254)
        );
    }
}
