package org.raincityvoices.ttrack.service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.api.CreateMixRequestBase;
import org.raincityvoices.ttrack.service.api.CreateMixTrackPackageRequest;
import org.raincityvoices.ttrack.service.api.CreateMixTrackRequest;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.api.MixTrack;
import org.raincityvoices.ttrack.service.api.PartTrack;
import org.raincityvoices.ttrack.service.api.Song;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.MixUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.exceptions.BadRequestException;
import org.raincityvoices.ttrack.service.exceptions.ConflictException;
import org.raincityvoices.ttrack.service.exceptions.NotFoundException;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaContent;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.tasks.AudioTrackTaskFactory;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.Temp;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;

import com.azure.core.annotation.QueryParam;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin
@RequestMapping(path = "/songs", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@RequiredArgsConstructor
public class SongController {

    private final SongStorage songStorage;
    private final MediaStorage mediaStorage;
    private final FileManager fileManager;
    private final AudioTrackTaskFactory taskFactory;

    private static final DefaultUriBuilderFactory URI_BUILDER_FACTORY = new DefaultUriBuilderFactory("/songs/");

    static {
        URI_BUILDER_FACTORY.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
    }

    @GetMapping
    public List<Song> listSongs() {
        return songStorage.listAllSongs()
            .stream()
            .map(SongDTO::toSong)
            .toList();
    }

    @PostMapping
    public Song createSong(@RequestBody Song song) {
        SongDTO dto = SongDTO.fromSong(song);
        songStorage.writeSong(dto);
        return dto.toSong();
    }

    @GetMapping({"/{id}","/{id}/"})
    public Song getSong(@PathVariable("id") SongId songId) {
        SongDTO dto = songStorage.describeSong(songId.value());
        log.debug("Song DTO: {}", dto);
        if (dto == null) {
            throw new NotFoundException("Song " + songId.value() + " not found.");
        }
        return dto.toSong();
    }

    @PutMapping("/{id}")
    public Song updateSong(@PathVariable("id") String songId, @RequestBody Song song) {
        if (!songId.equals(song.getId().value())) {
            throw new IllegalArgumentException("Song ID in URL does not match ID in body");
        }
        SongDTO dto = SongDTO.fromSong(song);
        songStorage.writeSong(dto);
        return dto.toSong();
    }

    @GetMapping({"/{id}/parts","/{id}/parts/"})
    public List<PartTrack> listPartsForSong(@PathVariable("id") SongId songId) {
        List<AudioTrackDTO> trackDtos = songStorage.listPartsForSong(songId.value());
        log.info("Loaded tracks: ");
        trackDtos.forEach(o -> log.info("Track: {}", o));
        return trackDtos.stream()
                        .map(dto -> toPartTrack(dto))
                        .toList();
    }

    @GetMapping({"/{id}/mixes","/{id}/mixes/"})
    public List<MixTrack> listMixesForSong(@PathVariable("id") SongId songId) {
        List<AudioTrackDTO> trackDtos = songStorage.listMixesForSong(songId.value());
        log.info("Loaded tracks: ");
        trackDtos.forEach(o -> log.info("Track: {}", o));
        return trackDtos.stream()
                        .map(dto -> toMixTrack(dto))
                        .toList();
    }

    @DeleteMapping("/{id}")
    public void deleteSong(@PathVariable("id") SongId songId) {
        log.info("Starting deleteSong for id {}", songId);
    }

    @GetMapping({"/{id}/parts/{partName}","/{id}/parts/{partName}/"})
    public PartTrack describePart(@PathVariable("id") SongId songId, @PathVariable("partName") AudioPart part) {
        log.info("Starting describePart for song ID {}, part {}", songId, part);
        AudioTrackDTO dto = songStorage.describePart(songId.value(), part.name());
        return toPartTrack(dto);
    }

    @PutMapping("/{id}/parts/{partName}")
    public String uploadMediaForPart(@PathVariable("id") SongId songId, @PathVariable("partName") AudioPart part, 
                                     @QueryParam("overwrite") boolean overwrite, @RequestParam MultipartFile audioFile) throws Exception {
        final AudioTrackDTO track;
        AudioTrackDTO existing = songStorage.describePart(songId.value(), part.name());
        if (existing != null) {
            if(!overwrite) {
                throw new ConflictException("Part '" + part.name() + "' already exists. Use overwrite=true to replace it.");
            }
            track = existing;
        } else {
            track = AudioTrackDTO.builder()
                    .songId(songId.value())
                    .id(part.name())
                    .build();
        }
        songStorage.writeTrack(track);
        String mediaLocation = mediaStorage.locationFor(songId, track.getId());
        mediaStorage.putMedia(mediaLocation, MediaContent.fromMultipartFile(audioFile));
        track.setMediaLocation(mediaLocation);
        songStorage.writeTrack(track);
        taskFactory.scheduleProcessUploadedTrackTask(track);
        return part.name();
    }

    @GetMapping({"/{id}/parts/{partName}/media","/{id}/parts/{partName}/media/"})
    public void downloadMediaForPart(HttpServletResponse response, @PathVariable("id") SongId songId, @PathVariable("partName") AudioPart part) {
        AudioTrackDTO trackDto = songStorage.describePart(songId.value(), part.name());
        if (trackDto == null || trackDto.getMediaLocation() == null) {
            log.info("Track not found or missing blobName: dto={}", trackDto);
            throw new NotFoundException("Part '" + part.name() + "' not found for song '" + songId.value() + "'");
        }
        SongDTO songDto = songStorage.describeSong(songId.value());
        if (songDto == null) {
            throw new IllegalStateException("No song found with ID " + trackDto.getSongId());
        }
        String defaultFileName = String.format("%s - %s", songDto.getTitle(), part.name());
        downloadTrack(response, trackDto, defaultFileName);
    }

    private void downloadTrack(HttpServletResponse response, AudioTrackDTO trackDto, String defaultFileName) {
        MediaContent content;
        try {
            log.info("Starting to download media from: {}", trackDto.getMediaLocation());
            content = mediaStorage.getMedia(trackDto.getMediaLocation());
        } catch(Exception e) {
            throw new RuntimeException("Failed to download audio.", e);
        }

        log.info("Content metadata: {}", content.metadata());
        response.setContentType(StringUtils.defaultIfBlank(content.metadata().contentType(), MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE));
        response.setContentLengthLong(content.metadata().lengthBytes());
        if (AudioFormats.WAV_TYPE.equals(content.metadata().contentType())) {
            if (!defaultFileName.endsWith(AudioFormats.WAV_EXT)) {
                defaultFileName += AudioFormats.WAV_EXT;
            }
        }
        String fileName = StringUtils.defaultIfBlank(content.metadata().fileName(), defaultFileName);
        String disposition = ContentDisposition.attachment().filename(fileName).build().toString();
        log.debug("disposition: {}", disposition); 
        response.setHeader("Content-Disposition", disposition);
        try {
            IOUtils.copy(content.stream(), response.getOutputStream());
        } catch(IOException e) {
            throw new RuntimeException("Failed to send audio to client.", e);
        }
    }

    @GetMapping("/{id}/mixes/{mixName}")
    public MixTrack describeMix(@PathVariable("id") SongId songId, @PathVariable("mixName") String mixName) {
        log.info("Starting describeMix for song ID {}, mix name {}", songId, mixName);
        AudioTrackDTO trackDto = songStorage.describeMix(songId.value(), mixName);
        if (trackDto == null) {
            throw new NotFoundException("Mix '" + mixName + "' not found for song '" + songId.value() + "'");
        }
        return toMixTrack(trackDto);
    }

    @GetMapping({"/{id}/mixes/{mixName}/media","/{id}/mixes/{mixName}/media/"})
    public void downloadMediaForMix(HttpServletResponse response, @PathVariable("id") SongId songId, @PathVariable("mixName") String mixName) {
        log.info("Starting downloadMediaForMix for song ID {}, mix name {}", songId, mixName);
        AudioTrackDTO dto = songStorage.describeMix(songId.value(), mixName);
        if (dto == null) {
            log.error("Track {}/{} not found.", songId.value(), mixName);
            throw new NotFoundException("Mix '" + mixName + "' not found for song '" + songId.value() + "'");
        }
        if (!dto.hasMedia()) {
            log.error("Track {} has no media available.", dto.getFqId());
            throw new NotFoundException("Mix '" + mixName + "' has no media available");
        }
        SongDTO songDto = songStorage.describeSong(songId.value());
        if (songDto == null) {
            log.error("Unexpected error: song {} not found", songId.value());
            throw new NotFoundException("Song '" + songId.value() + "' not found");
        }
        String defaultFileName = String.format("%s - %s", songDto.getTitle(), mixName);
        downloadTrack(response, dto, defaultFileName);
    }

    @PostMapping("/{id}/mixes")
    public List<MixTrack> createMixTracks(@PathVariable("id") SongId songId, @RequestBody CreateMixRequestBase request,
                                        @QueryParam("overwrite") boolean overwrite) {
        verifySongExists(songId);
        
        List<AudioTrackDTO> partTracks = request.parts().stream()        
                .map(p -> fetchTrackOrThrowNotFound(songId, p))
                .toList();
        
        if (request instanceof CreateMixTrackRequest) {
            return List.of(createMixTrack(songId, (CreateMixTrackRequest)request, overwrite, partTracks));
        } else if (request instanceof CreateMixTrackPackageRequest) {
            return createMixPackage(songId, (CreateMixTrackPackageRequest)request, overwrite, partTracks);
        } else {
            throw new IllegalStateException("Unexpected request class: " + request.getClass());
        }
    }

    private void verifySongExists(SongId songId) {
        SongDTO song = songStorage.describeSong(songId.value());
        if (song == null) {
            throw new NotFoundException("Song '" + songId.value() + "' not found");
        }
    }

    private List<MixTrack> createMixPackage(SongId songId, CreateMixTrackPackageRequest request, boolean overwrite, List<AudioTrackDTO> partTracks) {
        List<CreateMixTrackRequest> requests = request.mixDescriptions().stream()
            .map(desc -> CreateMixTrackRequest.builder()
                .parts(request.parts())
                .name(desc + ((request.packageDescription() == null) ? "" : " " + request.packageDescription()))
                .description(desc)
                .pitchShift(request.pitchShift())
                .speedFactor(request.speedFactor())
                .build())
            .toList();
        return requests.stream().map(req -> createMixTrack(songId, req, overwrite, partTracks)).toList();
    }

    private MixTrack createMixTrack(SongId songId, CreateMixTrackRequest request, boolean overwrite, List<AudioTrackDTO> partTracks) {
        String errorMessage = request.validate();
        if (errorMessage != null) {
            log.error("Error validating CreateMixTrackRequest: {}", errorMessage);
            throw new BadRequestException("Invalid mix request: " + errorMessage);
        }

        final AudioMix mix;
        try {
            mix = MixUtils.parseStereoMix(request.description(), request.parts());
        } catch(IllegalArgumentException e) {
            log.info("Unable to parse audio mix description '{}' for parts {}", request.description(), request.parts());
            throw new BadRequestException("Invalid mix description or parts: " + e.getMessage());
        }
        
        AudioTrackDTO existing = songStorage.describeMix(songId.value(), request.name());
        AudioTrackDTO newDto;
        if (existing != null) {
            if (!overwrite) {
                throw new ConflictException("Mix '" + request.name() + "' already exists. Use overwrite=true to replace it.");
            }
            /* Existing track: create a new DTO with the new mix, but don't persist yet.
             * The DB will be updated by the mixing task, but until then we want to maintain
             * metadata matching the existing mix.
             */
            newDto = existing.toBuilder()
                .parts(request.parts().stream().map(AudioPart::name).toList())
                .audioMix(mix)
                .pitchShift(request.pitchShift())
                .speedFactor(request.speedFactor())
                .build();
        } else {
            // New track: create and persist.
            newDto = AudioTrackDTO.fromMixTrack(
                MixTrack.builder()
                    .songId(songId)
                    .mixInfo(MixInfo.builder()
                        .name(request.name())
                        .parts(request.parts())
                        .mix(mix)
                        .pitchShift(request.pitchShift())
                        .speedFactor(request.speedFactor())
                        .build())
                    .build());
            log.info("Persisting new mix track DTO: {}", newDto);
            songStorage.writeTrack(newDto);
        }

        taskFactory.scheduleCreateMixTrackTask(newDto);

        return toMixTrack(newDto);
    }

    @DeleteMapping("/{id}/mixes/{mixName}")
    public void deleteMix(@PathVariable("id") SongId songId, @PathVariable("mixName") String mixName) {
        verifySongExists(songId);
        AudioTrackDTO dto = songStorage.describeMix(songId.value(), mixName);
        if (dto == null) {
            throw new NotFoundException("Mix '" + mixName + "' not found for song '" + songId.value() + "'");
        }
        songStorage.deleteTrack(songId.value(), mixName);
        String mediaLocation = dto.getMediaLocation();
        if (mediaLocation != null) {
            mediaStorage.deleteMedia(mediaLocation);
        }
    }

    @GetMapping({"/{id}/defaultMixes","/{id}/defaultMixes/"})
    public List<MixInfo> listDefaultMixesForSong(@PathVariable("id") SongId songId) {
        List<AudioTrackDTO> partTracks = songStorage.listPartsForSong(songId.value());
        List<AudioPart> parts = partTracks.stream().map(dto -> toPartTrack(dto)).map(PartTrack::part).toList();
        return MixUtils.getParseableMixes(parts);
    }

    private AudioTrackDTO fetchTrackOrThrowNotFound(SongId songId, AudioPart part) {
        AudioTrackDTO dto = songStorage.describePart(songId.value(), part.name());
        if (dto == null) {
            log.error("Part {} for song ID {} not found.", part, songId);
            throw new BadRequestException("Part '" + part.name() + "' not found for song '" + songId.value() + "'");
        }
        return dto;
    }

    // TODO move this somewhere better
    public static MixTrack toMixTrack(AudioTrackDTO dto) {
        assert dto.isMixTrack();
        return MixTrack.builder()
        .songId(new SongId(dto.getSongId()))
        .mixInfo(MixInfo.builder()
        .name(dto.getId())
        .parts(dto.getParts().stream().map(AudioPart::new).toList())
        .mix(dto.getAudioMix())
        .pitchShift(Objects.requireNonNullElse(dto.getPitchShift(), 0))
        .speedFactor(Objects.requireNonNullElse(dto.getSpeedFactor(), 1.0))
        .build())
        .created(dto.getCreated())
        .updated(dto.getUpdated())
        .hasMedia(dto.hasMedia())
        .durationSec(dto.getDurationSec())
        .build();
    }
    
    // TODO move this somewhere better
    public static PartTrack toPartTrack(AudioTrackDTO dto) {
        assert dto.isPartTrack();
        return PartTrack.builder()
            .songId(new SongId(dto.getSongId()))
            .part(new AudioPart(dto.getId()))
            .created(dto.getCreated())
            .updated(dto.getUpdated())
            .hasMedia(dto.hasMedia())
            .durationSec(dto.getDurationSec())
            .build();
    }

    public static URI songUrl(SongId songId) {
        return buildUrl(songId);
    }

    public static URI partTrackUrl(SongId songId, AudioPart part) {
        return buildUrl(songId, "parts", part.name());
    }

    public static URI partMediaUrl(SongId songId, AudioPart part) {
        return buildUrl(songId, "parts", part.name(), "media");
    }

    public static URI mixTrackUrl(SongId songId, String mixName) {
        return buildUrl(songId, "mixes", mixName);
    }

    public static URI mixMediaUrl(SongId songId, String mixName) {
        return buildUrl(songId, "mixes", mixName, "media");
    }

    private static URI buildUrl(SongId songId, String ... pathSegments) {
        try {
            return URI_BUILDER_FACTORY.builder().pathSegment(songId.value()).pathSegment(pathSegments).build();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unexpected exception building URL with pathSegments: " + StringUtils.join(pathSegments, ","), e);
        }
    }
}
