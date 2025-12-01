package org.raincityvoices.ttrack.service.storage;

import java.time.Clock;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.azure.cosmos.implementation.guava25.base.Preconditions;
import com.azure.data.tables.TableClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AzureTablesSongStorage implements SongStorage {

    private final TableClient tableClient;

    private final BaseTablesDAO<SongDTO> songDao;
    private final BaseTablesDAO<AudioTrackDTO> trackDao;

    private final Random random = new Random();

    private final Clock clock = Clock.systemUTC();


    public AzureTablesSongStorage(TableClient songsTableClient) {
        this.tableClient = songsTableClient;
        this.songDao = new BaseTablesDAO<SongDTO>(SongDTO.class, tableClient);
        this.trackDao = new BaseTablesDAO<AudioTrackDTO>(AudioTrackDTO.class, tableClient);
    }

    @Override
    public List<SongDTO> listAllSongs() {
        log.info("Listing all songs");
        return songDao.query("RowKey eq ''");
    }

    @Override
    public SongDTO describeSong(String songId) {
        log.info("Reading song with ID {}", songId);
        return songDao.get(songId, "");
    }

    @Override
    public String writeSong(SongDTO songDto) {
        log.info("Writing song with ID {}", songDto.getId());
        log.debug("Song details: {}", songDto);
        if (songDto.getId().isEmpty()) {
            songDto.setId(createIdForSong(songDto));
        }
        songDao.put(songDto);
        return songDto.getId();
    }

    public boolean deleteSong(String songId) {
        Preconditions.checkNotNull(songId);
        return songDao.delete(songId, "");
    }

    /**
     * Generate a unique ID for a new song. Note that this is not guaranteed to be unique,
     * but the chance of collision is very low (8 hex digits = 32 bits).
     */
    private String createIdForSong(SongDTO songDto) {
        return String.format("%08x", random.nextLong(0x100000000L));
    }

    @Override
    public List<AudioTrackDTO> listTracksForSong(String songId) {
        Preconditions.checkNotNull(songId);
        // TODO Check for existence of the song?
        log.info("Listing tracks for song ID {}", songId);
        String query = String.format("PartitionKey eq '%s' and RowKey ne ''", songId);
        return trackDao.query(query);
    }

    @Override
    public AudioTrackDTO describeTrack(String songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);
        log.info("Reading track with ID {} for song ID {}", trackId, songId);
        return trackDao.get(songId, trackId);
    }

    @Override
    public AudioTrackDTO writeTrack(AudioTrackDTO trackDto) {
        Preconditions.checkNotNull(trackDto);
        Preconditions.checkArgument(trackDto.isValid());
        log.info("Writing track with ID {}", trackDto.getId());
        log.debug("Track details: {}", trackDto);

        if (trackDto.getCreated() == null) {
            trackDto.setCreated(clock.instant());
        }
        trackDao.put(trackDto);
        return trackDto;
    }

    @Override
    public boolean deleteTrack(String songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);

        return trackDao.delete(songId, trackId);
    }
}
