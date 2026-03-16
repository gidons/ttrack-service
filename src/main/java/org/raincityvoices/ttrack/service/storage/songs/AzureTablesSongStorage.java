package org.raincityvoices.ttrack.service.storage.songs;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.raincityvoices.ttrack.service.storage.BasicTablesDAO;
import org.springframework.stereotype.Component;

import com.azure.data.tables.TableClient;
import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AzureTablesSongStorage implements SongStorage {

    private final TableClient tableClient;

    private final BasicTablesDAO<SongDTO> songDao;
    private final BasicTablesDAO<AudioTrackDTO> trackDao;

    private final Random random = new Random();

    private final Clock clock = Clock.systemUTC();


    public AzureTablesSongStorage(TableClient songsTableClient) {
        this.tableClient = songsTableClient;
        this.songDao = new BasicTablesDAO<SongDTO>(SongDTO.class, tableClient);
        this.trackDao = new BasicTablesDAO<AudioTrackDTO>(AudioTrackDTO.class, tableClient);
    }

    @Override
    public List<SongDTO> listAllSongs(boolean includeArchived) {
        log.info("Listing all songs");
        return songDao.query("RowKey eq ''" + (includeArchived ? "" : " and not Archived"));
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

    @Override
    public boolean deleteSong(String songId) {
        Preconditions.checkNotNull(songId);
        if (!listTracksForSong(songId, 1).isEmpty()) {
            log.warn("Song {} has tracks; not deleting.", songId);
            return false;
        }
        return songDao.delete(songId, "");
    }

    @Override
    public boolean archiveSong(String songId) {
        Preconditions.checkNotNull(songId);
        SongDTO dto = songDao.get(songId, "");
        if (dto == null || dto.isArchived()) {
            return false;
        }
        dto.setArchived(true);
        songDao.put(dto);
        return true;
    }

    @Override
    public boolean unarchiveSong(String songId) {
        Preconditions.checkNotNull(songId);
        SongDTO dto = songDao.get(songId, "");
        if (dto == null || !dto.isArchived()) {
            return false;
        }
        dto.setArchived(false);
        songDao.put(dto);
        return true;
    }

    /**
     * Generate a unique ID for a new song. Note that this is not guaranteed to be unique,
     * but the chance of collision is very low (8 hex digits = 32 bits).
     */
    private String createIdForSong(SongDTO songDto) {
        return String.format("%08x", random.nextLong(0x100000000L));
    }

    @Override
    public List<AudioTrackDTO> listParts(Optional<String> songId) {
        log.info("Listing part tracks for {}", songId.map(id -> "song " + id).orElse("all songs"));
        String query = "RowKey ne '' and not (Parts ne '')" +
            songId.map(id -> " and PartitionKey eq '" + id + "'").orElse("");
        log.info("Query: {}", query);
        return trackDao.query(query
        );
    }

    public List<AudioTrackDTO> listMixes(Optional<String> songId, Optional<String> mixName) {
        log.info("Listing mix tracks: songId={}, mixName={}", songId.orElse("<all>"), mixName.orElse("<all>"));
        String filter = String.format(
            "Parts ne '' %s %s",
            songId.map(id -> "and PartitionKey eq '" + id + "'").orElse(""),
            mixName.map(n -> "and RowKey eq '" + n + "'").orElse("")
        );
        return trackDao.query(filter);
    }

    @Override
    public List<AudioTrackDTO> listTracksForSong(String songId) {
        Preconditions.checkNotNull(songId);
        log.info("Listing tracks for song ID {}", songId);
        return listTracksForSong(songId, null);
    }

    private List<AudioTrackDTO> listTracksForSong(String songId, Integer maxResults) {
        // TODO Check for existence of the song?
        String query = String.format("PartitionKey eq '%s' and RowKey ne ''", songId);
        return trackDao.query(query, maxResults);
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
