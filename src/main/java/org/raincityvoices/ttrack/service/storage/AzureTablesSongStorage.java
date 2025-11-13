package org.raincityvoices.ttrack.service.storage;

import java.time.Clock;
import java.util.List;
import java.util.Random;

import org.raincityvoices.ttrack.service.storage.mapper.TableEntityMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.azure.core.http.rest.PagedIterable;
import com.azure.cosmos.implementation.guava25.base.Preconditions;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class AzureTablesSongStorage implements SongStorage {

    private final TableClient songsClient;

    private final TableEntityMapper<SongDTO> songMapper = new TableEntityMapper<>(SongDTO.class);
    private final TableEntityMapper<AudioTrackDTO> trackMapper = new TableEntityMapper<>(AudioTrackDTO.class);

    private final Random random = new Random();

    private final Clock clock = Clock.systemUTC();

    @Override
    public List<SongDTO> listAllSongs() {
        log.info("Listing all songs");
        PagedIterable<TableEntity> results = songsClient.listEntities(new ListEntitiesOptions()
            .setFilter("RowKey eq ''"), null, null);
        return results.stream().map(songMapper::fromTableEntity).toList();
    }

    @Override
    public SongDTO describeSong(String songId) {
        log.info("Reading song with ID {}", songId);
        try {
            TableEntity entity = songsClient.getEntity(songId, "");
            log.debug("Table entity: {}", entity.getProperties());
            return songMapper.fromTableEntity(entity);
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                log.info("No song found with ID {}; returning null.", songId);
                return null;
            }
            throw new IllegalArgumentException("Failed to get song with ID " + songId, e);
        } catch(Exception e) {
            throw new RuntimeException("Failed to read song with ID " + songId, e);
        }
    }

    @Override
    public String writeSong(SongDTO songDto) {
        log.info("Writing song with ID {}", songDto.getId());
        log.debug("Song details: {}", songDto);
        if (songDto.getId().isEmpty()) {
            songDto.setId(createIdForSong(songDto));
        }
        try {
            TableEntity entity = songMapper.toTableEntity(songDto);
            log.debug("Table entity: {}", entity.getProperties());
            songsClient.upsertEntity(entity);
        } catch(Exception e) {
            throw new RuntimeException("Failed to write song " + songDto.getId() + " to table.", e);
        }
        return songDto.getId();
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
        log.debug("Query: {}", query);
        PagedIterable<TableEntity> results = songsClient.listEntities(new ListEntitiesOptions()
            .setFilter(query), null, null);
        return results.stream().map(trackMapper::fromTableEntity).toList();
    }

    @Override
    public AudioTrackDTO describeTrack(String songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);
        log.info("Reading track with ID {} for song ID {}", trackId, songId);
        try {
            TableEntity entity = songsClient.getEntity(songId, trackId);
            log.debug("Table entity: {}", entity.getProperties());
            return trackMapper.fromTableEntity(entity);
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                log.info("No track found with ID " + trackId + " for song ID " + songId + "; returning null.");
                return null;
            }
            throw new IllegalArgumentException("Exception getting track with ID " + trackId + " for song ID " + songId, e);
        } catch(Exception e) {
            throw new RuntimeException("Failed to read track with ID " + trackId + " for song ID " + songId, e);
        }
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
        try {
            TableEntity entity = trackMapper.toTableEntity(trackDto);
            log.debug("Table entity: {}", entity.getProperties());
            songsClient.upsertEntity(entity);
        } catch(Exception e) {
            throw new RuntimeException("Failed to write track " + trackDto.getId() + " to table.", e);
        }
        return trackDto;
    }

    @Override
    public void deleteTrack(String songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);

        songsClient.deleteEntity(songId, trackId);
    }
}
