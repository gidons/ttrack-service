package org.raincityvoices.ttrack.service.storage;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.raincityvoices.ttrack.service.FileMetadata;
import org.raincityvoices.ttrack.service.MediaContent;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.storage.mapper.TableEntityMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.azure.core.http.rest.PagedIterable;
import com.azure.cosmos.implementation.guava25.base.Preconditions;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.specialized.BlobInputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class SongStorageImpl implements SongStorage {

    private final TableClient songsClient;
    private final BlobContainerClient mediaContainerClient;

    private final TableEntityMapper<SongDTO> songMapper = new TableEntityMapper<>(SongDTO.class);
    private final TableEntityMapper<AudioTrackDTO> trackMapper = new TableEntityMapper<>(AudioTrackDTO.class);

    private final Random random = new Random();

    @Override
    public List<SongDTO> listAllSongs() {
        log.info("Listing all songs");
        PagedIterable<TableEntity> results = songsClient.listEntities(new ListEntitiesOptions()
            .setFilter("RowKey eq ''"), null, null);
        return results.stream().map(songMapper::fromTableEntity).toList();
    }

    @Override
    public SongDTO describeSong(SongId songId) {
        log.info("Reading song with ID {}", songId);
        try {
            TableEntity entity = songsClient.getEntity(songId.value(), "");
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
    public SongId writeSong(SongDTO songDto) {
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
        return new SongId(songDto.getId());
    }

    /**
     * Generate a unique ID for a new song. Note that this is not guaranteed to be unique,
     * but the chance of collision is very low (8 hex digits = 32 bits).
     */
    private String createIdForSong(SongDTO songDto) {
        return String.format("%08x", random.nextLong(0x100000000L));
    }

    @Override
    public List<AudioTrackDTO> listTracksForSong(SongId songId) {
        Preconditions.checkNotNull(songId);
        // TODO Check for existence of the song?
        log.info("Listing tracks for song ID {}", songId);
        String query = String.format("PartitionKey eq '%s' and RowKey ne ''", songId.value());
        log.debug("Query: {}", query);
        PagedIterable<TableEntity> results = songsClient.listEntities(new ListEntitiesOptions()
            .setFilter(query), null, null);
        return results.stream().map(trackMapper::fromTableEntity).toList();
    }

    @Override
    public AudioTrackDTO describeTrack(SongId songId, String trackId) {
        Preconditions.checkNotNull(songId);
        Preconditions.checkNotNull(trackId);
        log.info("Reading track with ID {} for song ID {}", trackId, songId);
        try {
            TableEntity entity = songsClient.getEntity(songId.value(), trackId);
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
        Preconditions.checkNotNull(trackDto.getSongId());
        Preconditions.checkArgument(trackDto.isValid());
        log.info("Writing track with ID {}", trackDto.getId());
        log.debug("Track details: {}", trackDto);
        if (trackDto.getId().isEmpty()) {
            // TODO Ensure this ID is unique within the song.
            // TODO Prevent duplicates by checking for existing tracks with the same mix name.
            trackDto.setId(createIdForTrack(trackDto));
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

    public AudioTrackDTO uploadTrackAudio(AudioTrackDTO trackDto, MediaContent media) {
        Preconditions.checkNotNull(trackDto);
        Preconditions.checkNotNull(media);
        Preconditions.checkNotNull(trackDto.getSongId());
        Preconditions.checkArgument(trackDto.isValid());
        if (trackDto.getId() == null) {
            trackDto.setId(createIdForTrack(trackDto));
        }
        log.info("Uploading audio for track ID {} of song ID {}", trackDto.getId(), trackDto.getSongId());
        log.debug("Content metadata: {}", media.metadata());
        String blobName = String.format("%s/%s", trackDto.getSongId(), trackDto.getId());
        try {
            BlobClient blobClient = mediaContainerClient.getBlobClient(blobName);
            blobClient.upload(media.stream(), true);
            blobClient.setHttpHeaders(media.metadata().toBlobHttpHeaders());
        } catch(Exception e) {
            throw new RuntimeException("Failed to upload audio for track " + trackDto.getId(), e);
        }
        
        trackDto.setBlobName(blobName);
        trackDto.setCreated(Instant.now());
        trackDto.setProcessed(trackDto.getCreated());
        log.info("Writing metadata for track ID {} of song ID {}", trackDto.getId(), trackDto.getSongId());
        writeTrack(trackDto);
        return trackDto;
    }

    private String createIdForTrack(AudioTrackDTO trackDto) {
        // This ID only has to be unique within a song, so we just use 16 bits of randomness.
        // TODO Maybe use a sequence number instead?
        return String.format("%04x", random.nextLong(0x10000L));
    }

    @Override
    public MediaContent readMedia(String blobName) {
        BlobClient blobClient = mediaContainerClient.getBlobClient(blobName);
        BlobInputStream stream = blobClient.openInputStream();
        return new MediaContent(stream, FileMetadata.fromBlobProperties(blobClient.getProperties()));
    }

}
