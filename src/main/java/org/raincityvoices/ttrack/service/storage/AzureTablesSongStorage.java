package org.raincityvoices.ttrack.service.storage;

import java.time.Clock;
import java.util.List;
import java.util.Random;

import org.raincityvoices.ttrack.service.exceptions.ConflictException;
import org.raincityvoices.ttrack.service.storage.mapper.BaseDTO;
import org.raincityvoices.ttrack.service.storage.mapper.TableEntityMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.util.ETag;
import com.azure.cosmos.implementation.guava25.base.Preconditions;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.data.tables.models.TableServiceException;
import com.microsoft.applicationinsights.core.dependencies.http.client.methods.HttpHead;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AzureTablesSongStorage implements SongStorage {

    private final TableClient tableClient;

    private final TableEntityMapper<SongDTO> songMapper = new TableEntityMapper<>(SongDTO.class);
    private final TableEntityMapper<AudioTrackDTO> trackMapper = new TableEntityMapper<>(AudioTrackDTO.class);

    private final Random random = new Random();

    private final Clock clock = Clock.systemUTC();

    public AzureTablesSongStorage(TableClient songsTableClient) {
        this.tableClient = songsTableClient;
    }

    @Override
    public List<SongDTO> listAllSongs() {
        log.info("Listing all songs");
        PagedIterable<TableEntity> results = tableClient.listEntities(new ListEntitiesOptions()
            .setFilter("RowKey eq ''"), null, null);
        return results.stream().map(songMapper::fromTableEntity).toList();
    }

    @Override
    public SongDTO describeSong(String songId) {
        log.info("Reading song with ID {}", songId);
        try {
            TableEntity entity = tableClient.getEntity(songId, "");
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
        writeEntity(songDto, songMapper);
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
        PagedIterable<TableEntity> results = tableClient.listEntities(new ListEntitiesOptions()
            .setFilter(query), null, null);
        return results.stream().map(trackMapper::fromTableEntity).toList();
    }

    @Override
    public AudioTrackDTO describeTrack(String songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);
        log.info("Reading track with ID {} for song ID {}", trackId, songId);
        try {
            TableEntity entity = tableClient.getEntity(songId, trackId);
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
        writeEntity(trackDto, trackMapper);
        return trackDto;
    }

    private <T extends BaseDTO> void writeEntity(T dto, TableEntityMapper<T> mapper) {
        TableEntity entity;
        try {
            entity = mapper.toTableEntity(dto);
        } catch(Exception e) {
            throw new RuntimeException("Failed to convert " + dto + " to Tables entity.", e);
        }
        String fullKey = entity.getPartitionKey() + "/" + entity.getRowKey();
        try {
            log.debug("Table entity: {}", entity.getProperties());
            final Response<Void> response;
            if (dto.hasETag()) {
                response = tableClient.updateEntityWithResponse(entity, TableEntityUpdateMode.REPLACE, true, null, null);
            } else {
                response = tableClient.createEntityWithResponse(entity, null, null);
            }
            log.debug("ETag header: '{}'", response.getHeaders().getValue(HttpHeaderName.ETAG));
            dto.setETag(new ETag(response.getHeaders().getValue(HttpHeaderName.ETAG) + '"'));
        } catch(TableServiceException e) {
            if (e.getResponse().getStatusCode() == 409) {
                throw new ConflictException("Entity " + fullKey + " has been updated since last read.");
            }
            throw new RuntimeException("Failed to write " + fullKey + " to table.", e);
        } catch(Exception e) {
            throw new RuntimeException("Failed to write " + fullKey + " to table.", e);
        }

    }

    @Override
    public boolean deleteTrack(String songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);

        try {
            tableClient.deleteEntity(songId, trackId);
            return true;
        } catch(TableServiceException e) {
            if (e.getResponse().getStatusCode() == 404) {
                return false;
            }
            throw new RuntimeException("Exception while trying to delete track " + songId + "/" + trackId, e);
        }
    }
}
