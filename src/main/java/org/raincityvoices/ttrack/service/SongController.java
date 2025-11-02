package org.raincityvoices.ttrack.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.api.CreateMixTrackRequest;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.api.MixTrack;
import org.raincityvoices.ttrack.service.api.PartTrack;
import org.raincityvoices.ttrack.service.api.Song;
import org.raincityvoices.ttrack.service.api.SongId;
import org.raincityvoices.ttrack.service.audio.MixUtils;
import org.raincityvoices.ttrack.service.audio.model.AllPartsMix;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.audio.model.AudioMix;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.SongDTO;
import org.raincityvoices.ttrack.service.storage.SongStorage;
import org.raincityvoices.ttrack.service.util.TempFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.CrossOrigin;
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
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    private static final DefaultUriBuilderFactory URI_BUILDER_FACTORY = new DefaultUriBuilderFactory("/songs/");

    static {
        URI_BUILDER_FACTORY.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
    }

    @GetMapping
    public List<Song> listSongs() {
        log.info("Starting listSongs");
        return songStorage.listAllSongs()
            .stream()
            .map(SongDTO::toSong)
            .toList();
    }

    @PostMapping
    public Song createSong(@RequestBody Song song) {
        log.info("Starting createSong");
        log.debug("Song object: {}", song);
        SongDTO dto = SongDTO.fromSong(song);
        songStorage.writeSong(dto);
        return dto.toSong();
    }

    @GetMapping("/{id}")
    public Song getSong(@PathVariable("id") SongId songId) {
        log.info("Starting getSong for id {}", songId);
        SongDTO dto = songStorage.describeSong(songId.value());
        log.debug("Song DTO: {}", dto);
        return dto.toSong();
    }

    @PutMapping("/{id}")
    public Song updateSong(@PathVariable("id") String songId, @RequestBody Song song) {
        log.info("Starting updateSong for id {}", songId);
        log.debug("Song object: {}", song);
        if (!songId.equals(song.getId().value())) {
            throw new IllegalArgumentException("Song ID in URL does not match ID in body");
        }
        SongDTO dto = SongDTO.fromSong(song);
        songStorage.writeSong(dto);
        return dto.toSong();
    }

    @GetMapping("/{id}/parts")
    public List<PartTrack> listPartsForSong(@PathVariable("id") SongId songId) {
        log.info("Starting listPartsForSong for id {}", songId);
        List<AudioTrackDTO> trackDtos = songStorage.listPartsForSong(songId.value());
        log.info("Loaded tracks: ");
        trackDtos.forEach(o -> log.info("Track: {}", o));
        return trackDtos.stream()
                        .map(this::toPartTrack)
                        .toList();
    }

    @GetMapping("/{id}/mixes")
    public List<MixTrack> listMixesForSong(@PathVariable("id") SongId songId) {
        log.info("Starting listMixesForSong for id {}", songId);
        List<AudioTrackDTO> trackDtos = songStorage.listMixesForSong(songId.value());
        log.info("Loaded tracks: ");
        trackDtos.forEach(o -> log.info("Track: {}", o));
        return trackDtos.stream()
                        .map(this::toMixTrack)
                        .toList();
    }

    @PutMapping("/{id}/parts")
    public String uploadAllParts(@PathVariable("id") String songId, @RequestParam("partNames") List<String> partNames,
                                 @RequestParam MultipartFile audioFile) throws Exception {
        List<AudioPart> parts = partNames.stream().map(AudioPart::new).toList();
        log.info("Starting uploadAllParts for song ID {}, parts: {}", songId, parts);
        AudioTrackDTO dto = uploadAudioTrack(songId, audioFile, new AllPartsMix(parts));
        return dto.getId();
    }

    @GetMapping("/{id}/parts/{partName}")
    public PartTrack describePart(@PathVariable("id") SongId songId, @PathVariable("partName") AudioPart part) {
        log.info("Starting describePart for song ID {}, part {}", songId, part);
        AudioTrackDTO dto = songStorage.describePart(songId.value(), part.name());
        return toPartTrack(dto);
    }

    @PutMapping("/{id}/parts/{partName}")
    public String uploadMediaForPart(@PathVariable("id") SongId songId, @PathVariable("partName") AudioPart part, 
                                     @QueryParam("overwrite") boolean overwrite, @QueryParam("direct") boolean direct,
                                     @RequestParam MultipartFile audioFile) throws Exception {
        log.info("Starting uploadMediaForPart for song ID {}, part {}", songId, part);
        final AudioTrackDTO track;
        AudioTrackDTO existing = songStorage.describePart(songId.value(), part.name());
        if (existing != null) {
            if(!overwrite) {
                throw new ErrorResponseException(HttpStatus.CONFLICT);
            }
            track = existing;
        } else {
            track = AudioTrackDTO.builder()
                    .songId(songId.value())
                    .id(part.name())
                    .build();
        }
        songStorage.writeTrack(track);
        if (direct) {
            songStorage.uploadTrackAudio(track, new MediaContent(audioFile.getInputStream(), FileMetadata.fromMultipartFile(audioFile)));
            executorService.submit(new ProcessUploadedTrackTask(track, songStorage));
        } else {
            try(TempFile tf = new TempFile("part")) {
                audioFile.transferTo(tf.file());
                // Send-and-forget
                executorService.submit(new UploadPartTrackTask(track, tf.transferOwnership(), audioFile.getOriginalFilename(), songStorage));
            }
        }
        return part.name();
    }

    @GetMapping("/{id}/parts/{partName}/media")
    public void downloadMediaForPart(HttpServletResponse response, @PathVariable("id") SongId songId, @PathVariable("partName") AudioPart part) {
        log.info("Starting downloadMediaForPart for song ID {}, part {}", songId, part);
        AudioTrackDTO trackDto = songStorage.describePart(songId.value(), part.name());
        if (trackDto == null || trackDto.getBlobName() == null) {
            log.info("Track not found or missing blobName: dto={}", trackDto);
            throw new ErrorResponseException(HttpStatus.NOT_FOUND);
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
            log.info("Starting to download media from blob: {}", trackDto.getBlobName());
            content = songStorage.downloadMedia(trackDto);
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
        if (trackDto == null || trackDto.getBlobName() == null) {
            log.info("Track not found or missing blobName: dto={}", trackDto);
            throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        }
        return toMixTrack(trackDto);
    }

    @GetMapping("/{id}/mixes/{mixName}/media")
    public void downloadMediaForMix(HttpServletResponse response, @PathVariable("id") SongId songId, @PathVariable("mixName") String mixName) {
        log.info("Starting downloadMediaForMix for song ID {}, mix name {}", songId, mixName);
        AudioTrackDTO dto = songStorage.describeMix(songId.value(), mixName);
        if (dto == null) {
            throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        }
        SongDTO songDto = songStorage.describeSong(songId.value());
        if (songDto == null) {
            throw new IllegalStateException("No song found with ID " + songId);
        }
        String defaultFileName = String.format("%s - %s", songDto.getTitle(), mixName);
        downloadTrack(response, dto, defaultFileName);
    }

    @PostMapping("/{id}/mixes")
    public MixTrack createMixTrack(@PathVariable("id") SongId songId, @RequestBody CreateMixTrackRequest request,
                                        @QueryParam("overwrite") boolean overwrite) {
        log.info("Starting createMixTrack for song ID {}, request {}", songId, request);

        validateCreateMixRequest(request);

        final AudioMix mix;
        try {
            mix = MixUtils.parseStereoMix(request.description(), request.parts());
        } catch(IllegalArgumentException e) {
            log.info("Unable to parse audio mix description '{}' for parts {}", request.description(), request.parts());
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
        }
        
        SongDTO song = songStorage.describeSong(songId.value());
        if (song == null) {
            // TODO add details to error
            throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        }
        
        List<AudioTrackDTO> partTracks = request.parts().stream()        
                .map(p -> fetchTrackOrThrowNotFound(songId, p))
                .toList();
        
        AudioTrackDTO existing = songStorage.describeMix(songId.value(), request.name());
        AudioTrackDTO newDto;
        if (existing != null) {
            if (!overwrite) {
                throw new ErrorResponseException(HttpStatus.CONFLICT);
            }
            /* Existing track: create a new DTO with the new mix, but don't persist yet.
             * The DB will be updated by the mixing task, but until then we want to maintain
             * metadata matching the existing mix.
             */
            newDto = existing.toBuilder()
                .parts(request.parts().stream().map(AudioPart::name).toList())
                .audioMix(mix)
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
                        .build())
                    .build());
            log.info("Persisting new mix track DTO: {}", newDto);
            songStorage.writeTrack(newDto);
        }

        executorService.submit(new CreateMixTrackTask(newDto, partTracks, songStorage));

        return toMixTrack(newDto);
    }

    @GetMapping("/{id}/defaultMixes")
    public List<MixInfo> listDefaultMixesForSong(@PathVariable("id") SongId songId) {
        List<AudioTrackDTO> partTracks = songStorage.listPartsForSong(songId.value());
        List<AudioPart> parts = partTracks.stream().map(this::toPartTrack).map(PartTrack::part).toList();
        return MixUtils.getParseableMixes(parts);
    }

    // TODO move to CreateMixTrackRequest?
    private void validateCreateMixRequest(CreateMixTrackRequest request) {
        String errorMessage;
        if (request.name() == null) {
            errorMessage = "Missing required name field.";
        } else if (CollectionUtils.isEmpty(request.parts())) {
            errorMessage = "Missing or empty required parts list.";
        } else { errorMessage = null; }
        if (errorMessage != null) {
            log.error("Invalid CreateMixTrackRequest: {}", errorMessage);
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
        }
    }

    private AudioTrackDTO fetchTrackOrThrowNotFound(SongId songId, AudioPart part) {
        AudioTrackDTO dto = songStorage.describePart(songId.value(), part.name());
        if (dto == null) {
            log.error("Part {} for song ID {} not found.", part, songId);
            // TODO add details to error
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
        }
        return dto;
    }

    // TODO Superseded by UploadPartTrackTask?
    private AudioTrackDTO uploadAudioTrack(String songId, InputStream audioStream, FileMetadata metadata, AudioMix audioMix)
            throws IOException, UnsupportedAudioFileException, FileNotFoundException {
        
        log.info("Uploading audio file for song ID {}", songId);
        AudioTrackDTO dto = AudioTrackDTO.builder()
            .songId(songId)
            .audioMix(audioMix)
            .build();

        File tempFile = File.createTempFile("ttrack-upload-", "");
        tempFile.deleteOnExit();

        try {
            try (FileOutputStream tempStream = new FileOutputStream(tempFile)) {
                IOUtils.copy(audioStream, tempStream);
            }
            AudioFileFormat format = AudioSystem.getAudioFileFormat(tempFile);
            if (dto.getDurationSec() == null && format.getFrameLength() > 0 && format.getFormat().getFrameRate() > 0) {
                dto.setDurationSec((int) (format.getFrameLength() / format.getFormat().getFrameRate()));
                log.info("Inferred track duration: {} sec", dto.getDurationSec());
            }
            if(!format.getType().equals(Type.WAVE)) {
                throw new IllegalArgumentException("At this time, only WAV files are supported.");
            }
            metadata = metadata.withContentType(AudioFormats.WAV_TYPE);
            log.debug("Persisting track: {}", dto);
            AudioTrackDTO persistedDto = songStorage.uploadTrackAudio(dto, new MediaContent(new FileInputStream(tempFile), metadata));
            log.debug("Persisted: {}", persistedDto);
            return persistedDto;
        } finally {
            tempFile.delete();
        }
    }

    private AudioTrackDTO uploadAudioTrack(String songId, MultipartFile audioFile, AudioMix audioMix)
            throws IOException, UnsupportedAudioFileException, FileNotFoundException {
        return uploadAudioTrack(songId, audioFile.getInputStream(), FileMetadata.fromMultipartFile(audioFile), audioMix);
    }

    public MixTrack toMixTrack(AudioTrackDTO dto) {
        assert dto.isMixTrack();
        return MixTrack.builder()
            .songId(new SongId(dto.getSongId()))
            .mixInfo(MixInfo.builder()
                .name(dto.getId())
                .parts(dto.getParts().stream().map(AudioPart::new).toList())
                .mix(dto.getAudioMix())
                .build())
            .created(dto.getCreated())
            .updated(dto.getUpdated())
            .hasMedia(dto.hasMedia())
            .durationSec(dto.getDurationSec())
            .build();
    }

    public PartTrack toPartTrack(AudioTrackDTO dto) {
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
